package com.dewijones92.primavista.score

internal val scoreEventOrder: Comparator<ScoreEvent> =
    compareBy({ it.onset.value }, { it.staff.ordinal }, { it.voice })
