package com.shilapi.xcertplay.media

import android.util.Log

/** Five-second video counters that separate network/iPhone gaps from decoder throughput. */
internal class VideoStats(
    private val label: String = "",
    private val nanoTime: () -> Long = System::nanoTime,
) {
    private var windowStartNs = nanoTime()
    private var lastArrivalNs = 0L
    private var received = 0
    private var rendered = 0
    private var recoveries = 0
    private var bytes = 0L
    private var maxArrivalGapNs = 0L
    private var touchSamples = 0
    private var touchLatencySumNs = 0L
    private var maxTouchLatencyNs = 0L
    private val decodeMicros = IntArray(1024)
    private var decodeSamples = 0
    private var late = 0
    private var pacingDelayNanos = 0L
    private var lastDegenerateRecoveryNs = 0L
    private var lastRenderedNs = 0L
    val lastArrivalMsAgo: Long get() = if (lastArrivalNs == 0L) -1L else (nanoTime() - lastArrivalNs) / 1_000_000
    val lastRenderedMsAgo: Long get() = if (lastRenderedNs == 0L) -1L else (nanoTime() - lastRenderedNs) / 1_000_000

    @Synchronized fun reset() {
        windowStartNs = nanoTime()
        lastArrivalNs = 0L
        received = 0
        rendered = 0
        recoveries = 0
        bytes = 0L
        touchSamples = 0
        touchLatencySumNs = 0L
        maxTouchLatencyNs = 0L
        decodeSamples = 0
        late = 0
        lastDegenerateRecoveryNs = 0L
        lastRenderedNs = 0L
    }

    @Synchronized fun onReceived(size: Int) {
        val now = nanoTime()
        val gap = now - lastArrivalNs
        if (lastArrivalNs != 0L && gap < IDLE_GAP_NS) maxArrivalGapNs = maxOf(maxArrivalGapNs, gap)
        lastArrivalNs = now
        // Touches only change the main screen; a second stream must not consume their samples.
        val touchLatency = if (label.isEmpty()) TouchLatencyProbe.onFrame(now) else -1L
        if (touchLatency >= 0) {
            touchSamples++
            touchLatencySumNs += touchLatency
            maxTouchLatencyNs = maxOf(maxTouchLatencyNs, touchLatency)
        }
        received++
        bytes += size
    }

    @Synchronized fun onRendered() {
        rendered++
        lastRenderedNs = nanoTime()
    }

    @Synchronized fun onRecovery() { recoveries++ }

    /** Time from queueing a frame into the decoder to dequeueing its output. */
    @Synchronized fun onDecodeLatency(ns: Long) {
        if (decodeSamples < decodeMicros.size) decodeMicros[decodeSamples++] = (ns / 1000).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
    }

    /** A paced frame left the decoder after its display time and was shown at once. */
    @Synchronized fun onLate() { late++ }

    /** Smooth video's current display delay ([PacingDelay]). */
    @Synchronized fun onPacingDelay(ns: Long) { pacingDelayNanos = ns }

    /** True if the pipeline is receiving a steady stream of frames but none are being rendered. */
    @Synchronized fun shouldRecoverDegeneratePipeline(): Boolean {
        val now = nanoTime()
        // Debounce: do not trigger consecutive degenerate recoveries within 3 seconds
        if (lastDegenerateRecoveryNs != 0L && now - lastDegenerateRecoveryNs < 3_000_000_000L) return false
        val elapsedNs = now - windowStartNs
        // If within the last 2+ seconds we received at least 30 frames but rendered 0, or within 3+ seconds
        // we received at least 5 frames but rendered 0 (static/low-fps stream after reverse camera),
        // the pipeline is stalled.
        val degenerate = (elapsedNs >= 2_000_000_000L && received >= 30 && rendered == 0) ||
            (elapsedNs >= 3_000_000_000L && received >= 5 && rendered == 0)
        if (degenerate) {
            lastDegenerateRecoveryNs = now
            windowStartNs = now
            received = 0
            rendered = 0
        }
        return degenerate
    }

    private fun decodeSummary(): String {
        if (decodeSamples == 0) return ""
        val sorted = decodeMicros.copyOf(decodeSamples).also { it.sort() }
        fun percentileMs(p: Int) = sorted[(decodeSamples - 1) * p / 100] / 1000
        val delay = if (pacingDelayNanos > 0) " delay=%dms".format(pacingDelayNanos / 1_000_000) else ""
        return " decode p50=%dms p90=%dms late=%d".format(percentileMs(50), percentileMs(90), late) + delay
    }

    @Synchronized fun logIfDue(): String? {
        val now = nanoTime()
        val elapsedNs = now - windowStartNs
        if (elapsedNs < WINDOW_NS) return null
        val seconds = elapsedNs / 1e9
        if (received == 0 && touchSamples == 0) {
            if (lastArrivalNs != 0L) {
                val rxAgoMs = (now - lastArrivalNs) / 1_000_000
                val shownAgoMs = if (lastRenderedNs != 0L) (now - lastRenderedNs) / 1_000_000 else -1L
                val line = "video stats$label idle rx=0.0fps shown=0.0fps lastRx=${rxAgoMs}ms ago lastShown=${shownAgoMs}ms ago"
                Log.i(TAG, line)
                windowStartNs = now
                return line
            }
            windowStartNs = now
            return null
        }
        val touchAvgMs = if (touchSamples == 0) -1 else touchLatencySumNs / touchSamples / 1_000_000
        val line = ("video stats$label rx=%.1ffps shown=%.1ffps maxGap=%dms kbps=%d recoveries=%d " +
            "touch2frame avg=%dms max=%dms n=%d touchSendMax=%dms").format(
            received / seconds, rendered / seconds, maxArrivalGapNs / 1_000_000,
            (bytes * 8 / 1000 / seconds).toLong(), recoveries,
            touchAvgMs, maxTouchLatencyNs / 1_000_000, touchSamples, TouchLatencyProbe.maxSendNs / 1_000_000,
        ) + decodeSummary()
        TouchLatencyProbe.maxSendNs = 0
        Log.i(TAG, line)
        windowStartNs = now
        received = 0; rendered = 0; recoveries = 0; bytes = 0; maxArrivalGapNs = 0
        touchSamples = 0; touchLatencySumNs = 0; maxTouchLatencyNs = 0
        decodeSamples = 0; late = 0
        return line
    }

    private companion object {
        const val TAG = "DiPlay-VideoStats"
        const val WINDOW_NS = 5_000_000_000L
        const val IDLE_GAP_NS = 2_000_000_000L // longer gaps are a static screen, not lag
    }
}
