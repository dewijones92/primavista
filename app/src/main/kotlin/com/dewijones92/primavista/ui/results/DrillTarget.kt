package com.dewijones92.primavista.ui.results

import com.dewijones92.primavista.practice.SessionResult
import com.dewijones92.primavista.practice.SkillOutcome
import com.dewijones92.primavista.score.Polyphony
import com.dewijones92.primavista.score.SkillTag

/**
 * What "drill the weakest" will actually drill, decided once so the button cannot promise one skill
 * while the session loads another. See `.claude/CODE-NOTES.md`.
 */
internal fun drillTarget(result: SessionResult, input: Polyphony): SkillOutcome? =
    result.skillOutcomes
        .filter { it.attempts > 0 }
        .filterNot { input == Polyphony.Mono && it.tag == SkillTag.HandIndependence }
        .minByOrNull { it.accuracy }
