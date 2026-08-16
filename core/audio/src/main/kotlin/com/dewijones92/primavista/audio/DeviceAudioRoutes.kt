package com.dewijones92.primavista.audio

import android.media.AudioDeviceInfo
import com.dewijones92.primavista.practice.AudioRoute
import com.dewijones92.primavista.practice.RouteKind

/**
 * The platform's device types, reduced to the kinds that differ in latency.
 * See .claude/CODE-NOTES.md.
 */
internal object DeviceAudioRoutes {

    fun routeOf(device: AudioDeviceInfo?): AudioRoute = when (device) {
        null -> AudioRoute.Unidentified
        else -> AudioRoute(kindOf(device.type), nameOf(device))
    }

    fun kindOf(type: Int): RouteKind = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_MIC -> RouteKind.BuiltIn

        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_LINE_ANALOG,
        AudioDeviceInfo.TYPE_LINE_DIGITAL,
        AudioDeviceInfo.TYPE_AUX_LINE,
        -> RouteKind.Wired

        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLE_HEADSET,
        AudioDeviceInfo.TYPE_BLE_BROADCAST,
        -> RouteKind.Bluetooth

        AudioDeviceInfo.TYPE_USB_DEVICE,
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_USB_ACCESSORY,
        -> RouteKind.Usb

        else -> RouteKind.Unknown
    }

    /**
     * The product name rather than `AudioDeviceInfo.id`, which is reassigned on every reconnect —
     * a stored measurement keyed by it would be orphaned the next time the headset is unplugged.
     */
    private fun nameOf(device: AudioDeviceInfo): String =
        device.productName?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: "type ${device.type}"
}
