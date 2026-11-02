package com.shilapi.xcertplay.transport

import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbRequest
import android.util.Log
import java.nio.ByteBuffer

/**
 * Reference-counted owner for one Android USB device connection.
 *
 * Legacy Android head units can reject claims made from a second file descriptor for another
 * interface of the same composite iPhone. USBMUX and NCM can therefore share one connection while
 * keeping independent lifetimes; the underlying connection closes only after both users release it.
 */
internal class SharedUsbDeviceConnection private constructor(
    val connection: UsbDeviceConnection,
) : SharedUsbConnection {
    private var references = 1
    private var closed = false
    /** Serializes raw usbfs transfers so two threads never share one ioctl staging buffer. */
    private val usbfsLock = Any()
    private val usbCompat = UsbRequestCompat()
    private val completions = UsbCompletionRouter<UsbRequest> { usbCompat.requestWait(connection, it) }

    override val fileDescriptor: Int get() = connection.fileDescriptor
    override val frameworkConnection: UsbDeviceConnection get() = connection
    override val supportsAsyncRequests: Boolean get() = true

    override fun controlTransfer(
        requestType: Int, request: Int, value: Int, index: Int,
        buffer: ByteArray?, length: Int, timeoutMillis: Int,
    ): Int = connection.controlTransfer(requestType, request, value, index, buffer, length, timeoutMillis)

    override fun bulkTransfer(endpoint: android.hardware.usb.UsbEndpoint, buffer: ByteArray, length: Int, timeoutMillis: Int): Int =
        bulkTransfer(endpoint, buffer, 0, length, timeoutMillis)

    override fun bulkTransfer(
        endpoint: android.hardware.usb.UsbEndpoint, buffer: ByteArray, offset: Int, length: Int, timeoutMillis: Int,
    ): Int = bulkTransferCompat(connection, endpoint, buffer, offset, length, timeoutMillis)

    /**
     * usbfs ioctl on the framework connection's own descriptor. The framework's bulkTransfer is
     * the same ioctl, so this reaches the identical endpoint; the difference is that the errno
     * survives instead of collapsing into -1.
     */
    override fun bulkTransferErrno(
        endpoint: android.hardware.usb.UsbEndpoint, buffer: ByteArray, offset: Int, length: Int, timeoutMillis: Int,
    ): Int {
        val descriptor = connection.fileDescriptor
        if (descriptor < 0) return UsbfsIoNative.ERRNO_UNAVAILABLE
        if (offset < 0 || length < 0 || offset + length > buffer.size) return UsbfsIoNative.ERRNO_UNAVAILABLE
        val isIn = endpoint.address and 0x80 != 0
        val staging = ByteBuffer.allocateDirect(maxOf(length, 1))
        if (!isIn && length > 0) {
            staging.put(buffer, offset, length)
            staging.flip()
        }
        val transferred = synchronized(usbfsLock) {
            UsbfsIoNative.bulk(descriptor, endpoint.address, staging, length, timeoutMillis)
        }
        if (transferred > 0 && isIn) {
            staging.rewind()
            staging.get(buffer, offset, minOf(transferred, length))
        }
        return transferred
    }

    override fun claimInterface(usbInterface: android.hardware.usb.UsbInterface, force: Boolean): Boolean =
        connection.claimInterface(usbInterface, force)

    override fun releaseInterface(usbInterface: android.hardware.usb.UsbInterface): Boolean =
        connection.releaseInterface(usbInterface)

    override fun setInterface(usbInterface: android.hardware.usb.UsbInterface): Boolean =
        if (com.shilapi.xcertplay.compat.realSdkInt() >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            try {
                connection.setInterface(usbInterface)
            } catch (_: NoSuchMethodError) {
                false
            }
        } else false

    override fun queue(request: UsbRequest, buffer: ByteBuffer): Boolean {
        completions.register(request)
        return try {
            usbCompat.queue(request, buffer).also { if (!it) completions.forget(request) }
        } catch (failure: Throwable) {
            completions.forget(request)
            throw failure
        }
    }

    override fun await(request: UsbRequest, timeoutMillis: Long): UsbRequest = completions.await(request, timeoutMillis)
    override fun forget(request: UsbRequest) = completions.forget(request)

    @Synchronized
    override fun retain(): SharedUsbDeviceConnection {
        check(!closed) { "USB device connection is already closed" }
        references += 1
        return this
    }

    @Synchronized
    override fun release() {
        if (closed) return
        references -= 1
        check(references >= 0) { "USB device connection released too many times" }
        if (references == 0) {
            closed = true
            // Android 4.x: the wait worker can be blocked inside native requestWait().
            // Freeing the connection under it (native_close frees the usb_device) makes
            // REAPURB return freed memory and libc aborts (SIGABRT in the wait thread).
            // Cancel every queued request so the blocked wait completes, wait for the
            // worker to go idle, and only then free the connection.
            completions.cancelAll { request -> runCatching { request.cancel() } }
            val drained = usbCompat.drain(WORKER_DRAIN_TIMEOUT_MILLIS)
            if (!drained) {
                Log.w(TAG, "USB wait worker did not go idle within ${WORKER_DRAIN_TIMEOUT_MILLIS}ms; closing the connection anyway")
            }
            connection.close()
        }
    }

    companion object {
        private const val TAG = "xcertplay-usb"
        private const val WORKER_DRAIN_TIMEOUT_MILLIS = 2_000L

        fun own(connection: UsbDeviceConnection): SharedUsbDeviceConnection =
            SharedUsbDeviceConnection(connection)
    }
}
