package com.dewijones92.primavista.audio

import android.media.AudioDeviceInfo
import com.dewijones92.primavista.practice.RouteKind
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The platform's device types reduced to the kinds that differ in latency.
 *
 * This runs on the JVM without Robolectric because `AudioDeviceInfo.TYPE_*` are compile-time
 * constants, so the `when` compiles to literal comparisons and never touches the android.jar stub.
 * If that ever stops being true this test fails loudly rather than the mapping going untested.
 */
class DeviceAudioRoutesTest {

    @Test
    fun `the built-in microphone is its own kind`() {
        assertEquals(RouteKind.BuiltIn, DeviceAudioRoutes.kindOf(AudioDeviceInfo.TYPE_BUILTIN_MIC))
    }

    /** The one that matters: a radio hop must never be graded as the built-in path. */
    @Test
    fun `every bluetooth device type is recognised as radio`() {
        val bluetooth = listOf(
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLE_BROADCAST,
        )

        for (type in bluetooth) {
            assertEquals("type $type", RouteKind.Bluetooth, DeviceAudioRoutes.kindOf(type))
        }
    }

    @Test
    fun `wired and usb paths are told apart from the built-in one`() {
        assertEquals(RouteKind.Wired, DeviceAudioRoutes.kindOf(AudioDeviceInfo.TYPE_WIRED_HEADSET))
        assertEquals(RouteKind.Usb, DeviceAudioRoutes.kindOf(AudioDeviceInfo.TYPE_USB_HEADSET))
    }

    /** A type this build has never heard of is Unknown, not silently the built-in mic. */
    @Test
    fun `an unrecognised device type is unknown rather than assumed built-in`() {
        assertEquals(RouteKind.Unknown, DeviceAudioRoutes.kindOf(Int.MAX_VALUE))
    }

    @Test
    fun `a device the platform will not name at all is unidentified`() {
        assertEquals(com.dewijones92.primavista.practice.AudioRoute.Unidentified, DeviceAudioRoutes.routeOf(null))
    }
}
