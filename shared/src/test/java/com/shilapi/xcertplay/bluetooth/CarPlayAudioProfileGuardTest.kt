package com.shilapi.xcertplay.bluetooth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CarPlayAudioProfileGuardTest {
    @Test fun rejectsMissingIdentity() {
        assertFalse(CarPlayAudioProfileGuard.isUnambiguousCandidate(emptyList()))
    }

    @Test fun rejectsMultipleIphones() {
        assertFalse(CarPlayAudioProfileGuard.isUnambiguousCandidate(listOf("iPhone A", "iPhone B")))
    }

    @Test fun rejectsUnrelatedDevice() {
        assertFalse(CarPlayAudioProfileGuard.isUnambiguousCandidate(listOf("Android Phone")))
    }

    @Test fun detectsSingleNamedIphone() {
        assertTrue(CarPlayAudioProfileGuard.isUnambiguousCandidate(listOf("iPhone")))
    }
}
