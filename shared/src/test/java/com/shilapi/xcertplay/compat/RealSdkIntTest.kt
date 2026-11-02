package com.shilapi.xcertplay.compat

import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Test

class RealSdkIntTest {

    @Test
    fun returnsReportedSdkWhenReleaseIsNull() {
        assertEquals(27, realSdkInt(27, null))
    }

    @Test
    fun returnsReportedSdkWhenNoMismatch() {
        assertEquals(28, realSdkInt(28, "9"))
        assertEquals(Build.VERSION_CODES.JELLY_BEAN_MR1, realSdkInt(Build.VERSION_CODES.JELLY_BEAN_MR1, "4.2.2"))
    }

    @Test
    fun correctsFakedSdkForJellyBeanVariants() {
        // Issue #371: Head units with Android 4.2.2 faking higher SDK
        assertEquals(Build.VERSION_CODES.JELLY_BEAN, realSdkInt(27, "4.1.2"))
        assertEquals(Build.VERSION_CODES.JELLY_BEAN_MR1, realSdkInt(27, "4.2.2"))
        assertEquals(Build.VERSION_CODES.JELLY_BEAN_MR2, realSdkInt(27, "4.3.1"))
    }

    @Test
    fun correctsFakedSdkForKitKatAndLollipop() {
        // Issue #368 / Allwinner T3 faking API 27 on 4.4.2
        assertEquals(Build.VERSION_CODES.KITKAT, realSdkInt(27, "4.4.2"))
        assertEquals(Build.VERSION_CODES.LOLLIPOP, realSdkInt(27, "5.0.2"))
        assertEquals(Build.VERSION_CODES.LOLLIPOP_MR1, realSdkInt(27, "5.1.1"))
        assertEquals(Build.VERSION_CODES.M, realSdkInt(27, "6.0.1"))
    }
}
