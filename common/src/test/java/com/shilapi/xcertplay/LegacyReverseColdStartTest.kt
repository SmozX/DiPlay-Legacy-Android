package com.shilapi.xcertplay

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class LegacyReverseColdStartTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val prefs get() = context.getSharedPreferences("xcertplay_airplay", Context.MODE_PRIVATE)

    @Before fun clearPreferences() {
        prefs.edit().clear().apply()
    }

    @Test fun explicitSettingCanBeSavedAndRestored() {
        AirPlayPersistence.saveLegacyReverseColdStart(context, false)
        assertFalse(AirPlayPersistence.loadLegacyReverseColdStart(context))

        AirPlayPersistence.saveLegacyReverseColdStart(context, true)
        assertTrue(AirPlayPersistence.loadLegacyReverseColdStart(context))
    }

    @Test
    @Config(sdk = [19])
    fun api19DefaultsToTrueOnFreshInstall() {
        // SDK <= 19 should default to true for reverse cold start protection
        assertTrue(AirPlayPersistence.loadLegacyReverseColdStart(context))
    }

    @Test
    @Config(sdk = [19])
    fun explicitFalseTakesPrecedenceEvenOnLegacyUnit() {
        AirPlayPersistence.saveLegacyReverseColdStart(context, false)
        assertFalse(AirPlayPersistence.loadLegacyReverseColdStart(context))
    }
}
