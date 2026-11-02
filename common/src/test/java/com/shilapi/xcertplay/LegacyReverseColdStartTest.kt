package com.shilapi.xcertplay

import android.content.Context
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowBuild

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class LegacyReverseColdStartTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val prefs get() = context.getSharedPreferences("xcertplay_airplay", Context.MODE_PRIVATE)

    @Before fun clearPreferences() {
        prefs.edit().clear().apply()
        ShadowBuild.reset()
    }

    @Test fun explicitSettingCanBeSavedAndRestored() {
        AirPlayPersistence.saveLegacyReverseColdStart(context, false)
        assertFalse(AirPlayPersistence.loadLegacyReverseColdStart(context))

        AirPlayPersistence.saveLegacyReverseColdStart(context, true)
        assertTrue(AirPlayPersistence.loadLegacyReverseColdStart(context))
    }

    @Test fun legacyHardwareDefaultsToTrueOnFreshInstall() {
        ShadowBuild.setHardware("mt8227")
        assertTrue(AirPlayPersistence.isLegacyReverseHeadUnit())
        assertTrue(AirPlayPersistence.loadLegacyReverseColdStart(context))
    }

    @Test fun pf8227DeviceDefaultsToTrueOnFreshInstall() {
        ShadowBuild.setDevice("pf8227")
        assertTrue(AirPlayPersistence.isLegacyReverseHeadUnit())
        assertTrue(AirPlayPersistence.loadLegacyReverseColdStart(context))
    }

    @Test fun explicitFalseTakesPrecedenceEvenOnLegacyUnit() {
        ShadowBuild.setHardware("mt8227")
        AirPlayPersistence.saveLegacyReverseColdStart(context, false)
        assertFalse(AirPlayPersistence.loadLegacyReverseColdStart(context))
    }
}
