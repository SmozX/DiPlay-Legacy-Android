package com.shilapi.xcertplay.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyCarHotspotStarterTest {
    @Test fun legacyApiRangeIncludesAndroid41Through60() {
        assertTrue(LegacyCarHotspotStarter.supportedSdk(16))
        assertTrue(LegacyCarHotspotStarter.supportedSdk(17))
        assertTrue(LegacyCarHotspotStarter.supportedSdk(19))
        assertTrue(LegacyCarHotspotStarter.supportedSdk(22))
        assertTrue(LegacyCarHotspotStarter.supportedSdk(23))
    }

    @Test fun newerApisKeepTheirExistingImplementation() {
        assertFalse(LegacyCarHotspotStarter.supportedSdk(15))
        assertFalse(LegacyCarHotspotStarter.supportedSdk(24))
        assertFalse(LegacyCarHotspotStarter.supportedSdk(37))
    }
}
