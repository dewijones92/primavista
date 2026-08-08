package com.dewijones92.primavista.database

import com.dewijones92.primavista.practice.InputLatency

internal fun PracticeSettings.toEntity(): SettingsEntity = SettingsEntity(
    tempoBpm = tempoBpm,
    metronomeOn = metronomeOn,
    listenFirstOn = listenFirstOn,
    inputLabel = inputLabel,
)

internal fun SettingsEntity.toSettings(): PracticeSettings = PracticeSettings(
    tempoBpm = tempoBpm,
    metronomeOn = metronomeOn,
    listenFirstOn = listenFirstOn,
    inputLabel = inputLabel,
)

internal fun AudioRouteLatencyEntity.toLatency(): InputLatency = InputLatency(millis, provenance)

internal fun AudioRouteLatencyEntity.toRouteLatency(): RouteLatency =
    RouteLatency(AudioRoute(route), toLatency(), measuredAtEpochMillis)
