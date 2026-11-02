package com.shilapi.xcertplay.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class VideoStatsTest {

    @Test
    fun logsIdleStatsWhenFramesStopArriving() {
        var simulatedNow = 1_000_000_000L
        val stats = VideoStats(label = "", nanoTime = { simulatedNow })

        stats.onReceived(1000)
        stats.onRendered()

        // Pass first 5s window which had 1 received frame
        simulatedNow += 5_500_000_000L
        val activeLog = stats.logIfDue()
        assertNotNull(activeLog)
        assertTrue(activeLog!!.contains("video stats"))

        // Next 5s window with 0 received frames triggers idle log
        simulatedNow += 5_500_000_000L
        val idleLog = stats.logIfDue()
        assertNotNull(idleLog)
        assertTrue(idleLog!!.contains("idle rx=0.0fps shown=0.0fps"))
        assertTrue(idleLog.contains("lastRx="))
        assertTrue(idleLog.contains("lastShown="))
        assertTrue(stats.lastArrivalMsAgo >= 11_000)
        assertTrue(stats.lastRenderedMsAgo >= 11_000)
    }

    @Test
    fun returnsNegativeOneWhenNoFramesEverArrived() {
        val stats = VideoStats(label = "", nanoTime = { 10_000_000_000L })
        assertEquals(-1L, stats.lastArrivalMsAgo)
        assertEquals(-1L, stats.lastRenderedMsAgo)
    }
}
