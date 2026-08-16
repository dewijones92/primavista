package com.dewijones92.primavista.database

import com.dewijones92.primavista.practice.AudioRoute
import com.dewijones92.primavista.practice.InputLatency
import com.dewijones92.primavista.practice.RouteLatency

internal fun PracticeSettings.toEntity(): SettingsEntity = SettingsEntity(
    tempoBpm = tempoBpm,
    metronomeOn = metronomeOn,
    listenFirstOn = listenFirstOn,
    inputLabel = inputLabel,
    readingLeadBeats = readingLeadBeats,
)

internal fun SettingsEntity.toSettings(): PracticeSettings = PracticeSettings(
    tempoBpm = tempoBpm,
    metronomeOn = metronomeOn,
    listenFirstOn = listenFirstOn,
    inputLabel = inputLabel,
    readingLeadBeats = readingLeadBeats,
)

internal fun AudioRouteLatencyEntity.toLatency(): InputLatency = InputLatency(millis, provenance)

internal fun AudioRouteLatencyEntity.toRouteLatency(): RouteLatency =
    RouteLatency(AudioRoute.of(route), toLatency(), measuredAtEpochMillis)
