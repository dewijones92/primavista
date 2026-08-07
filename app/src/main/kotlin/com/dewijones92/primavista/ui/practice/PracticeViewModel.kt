package com.dewijones92.primavista.ui.practice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dewijones92.primavista.common.Diag
import com.dewijones92.primavista.notation.GlyphMetrics
import com.dewijones92.primavista.notation.StaffLayout
import com.dewijones92.primavista.notation.StaffSpaces
import com.dewijones92.primavista.notation.StaffSystem
import com.dewijones92.primavista.practice.AnswerSource
import com.dewijones92.primavista.practice.Conductor
import com.dewijones92.primavista.practice.JudgeState
import com.dewijones92.primavista.practice.NoteJudgement
import com.dewijones92.primavista.practice.PerformanceJudge
import com.dewijones92.primavista.practice.RefusalReason
import com.dewijones92.primavista.practice.SessionResult
import com.dewijones92.primavista.practice.TransportState
import com.dewijones92.primavista.practice.Verdict
import com.dewijones92.primavista.score.Score
import com.dewijones92.primavista.score.Ticks
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

public data class PracticeUiState(
    val score: Score? = null,
    val system: StaffSystem? = null,
    val transport: TransportState = TransportState.Idle,
    val position: Ticks = Ticks.ZERO,
    val playheadX: StaffSpaces = StaffSpaces.ZERO,
    val countInBeatsRemaining: Int = 0,
    val verdicts: Map<Int, Verdict> = emptyMap(),
    /** Notes played that answered to nothing written. Counted, because they have no notehead to colour. */
    val extras: Int = 0,
    val refusal: RefusalReason? = null,
    val result: SessionResult? = null,
    val inputLabel: String = "",
    val tempoBpm: Int = 0,
)

/**
 * Drives one practice session: samples the [Conductor], feeds the [PerformanceJudge], and turns
 * its verdicts into something the staff can colour.
 *
 * It deliberately holds no idea of "now" of its own — [tick] is called from the UI's frame clock
 * and asks the Conductor. See .claude/CODE-NOTES.md for why sampling from a frame clock is not the
 * same mistake as deriving timing from recomposition, and why backgrounding pauses rather than
 * freezes.
 */
public class PracticeViewModel(
    private val layout: StaffLayout,
    private val metrics: GlyphMetrics,
    private val diag: Diag,
    private val conductorFor: (Score) -> Conductor,
) : ViewModel() {

    private val _state = MutableStateFlow(PracticeUiState())
    public val state: StateFlow<PracticeUiState> = _state.asStateFlow()

    private var conductor: Conductor? = null
    private var source: AnswerSource? = null
    private var judge: PerformanceJudge? = null
    private var judgeState: JudgeState? = null
    private var collection: Job? = null
    private var loaded: Triple<Score, AnswerSource, PerformanceJudge>? = null

    public val glyphMetrics: GlyphMetrics get() = metrics

    /**
     * [judge] is supplied per score rather than held for the lifetime of the view model, because a
     * judge is bound to one score's per-note skills. One judge reused across pieces would report a
     * note's skills from whichever piece happened to be loaded first.
     */
    public fun load(score: Score, source: AnswerSource, judge: PerformanceJudge) {
        collection?.cancel()
        this.source = source
        this.judge = judge
        loaded = Triple(score, source, judge)

        val refusal = judge.accepts(score, source)
        if (refusal != null) {
            // Logged as loudly as it is shown: a refusal is exactly the line a future report needs
            // to explain why nothing happened (docs/spec.md I3).
            diag.event("practice", "refused score='${score.title}' src=${source.label} reason=$refusal")
            _state.value = PracticeUiState(
                score = score,
                refusal = refusal,
                inputLabel = source.label,
                tempoBpm = score.defaultTempoBpm,
            )
            return
        }

        val system = layout.layout(score, metrics)
        val conductor = conductorFor(score).also { this.conductor = it }
        // The judge gets an immutable snapshot, never the live transport: handing it the running
        // Conductor made a paused session re-judge differently from a report of itself.
        judgeState = judge.begin(score, conductor.timingSnapshot())

        diag.event(
            "practice",
            "loaded '${score.title}' [origin=${score.origin::class.simpleName} " +
                "notes=${score.attackedNotes.size} bars=${score.measures.size} " +
                "src=${source.label} poly=${source.polyphony} tempo=${conductor.tempoBpm} " +
                "lat=${source.latency.millis}ms/${source.latency.provenance}]",
        )
        diag.state("practice") {
            val s = _state.value
            "transport=${s.transport} pos=${s.position.value} judged=${s.verdicts.size}/" +
                "${s.score?.attackedNotes?.size ?: 0} src=${s.inputLabel}"
        }

        _state.value = PracticeUiState(
            score = score,
            system = system,
            transport = conductor.state,
            inputLabel = source.label,
            tempoBpm = conductor.tempoBpm,
        )

        collection = viewModelScope.launch {
            source.notes().collect { note ->
                val current = judgeState ?: return@collect
                val (next, settled) = judge.advance(current, note)
                judgeState = next
                if (settled.isNotEmpty()) apply(settled)
                diag.counted("input", "notes-${source.label}")
            }
        }
    }

    public fun start() {
        val conductor = conductor ?: return
        conductor.start()
        diag.event("practice", "start tempo=${conductor.tempoBpm} countIn=${conductor.countInBeatsRemaining()}")
        _state.value = _state.value.copy(transport = conductor.state)
    }

    /**
     * Called once per display frame. The Conductor is the source of the time; the frame clock only
     * decides when to look at it.
     */
    public fun tick() {
        val conductor = conductor ?: return
        val system = _state.value.system ?: return
        if (conductor.state != TransportState.Running && conductor.state != TransportState.CountingIn) return

        val position = conductor.position()
        val current = judgeState
        val judge = this.judge
        if (current != null && judge != null) {
            val (next, settled) = judge.advanceTime(current, position)
            judgeState = next
            if (settled.isNotEmpty()) apply(settled)
        }

        _state.value = _state.value.copy(
            position = position,
            playheadX = layout.xOf(system, position),
            transport = conductor.state,
            countInBeatsRemaining = conductor.countInBeatsRemaining(),
        )
        diag.counted("practice", "ticks")

        val score = _state.value.score
        if (score != null && position >= score.endsAt) finish()
    }

    /**
     * Backgrounding **pauses**, and that is a decision rather than a side effect. Letting the
     * Conductor run on while nothing is sampling it would return to a position seconds ahead with
     * every note in between marked Missed — blaming Dewi for a phone call.
     */
    public fun onBackgrounded() {
        val conductor = conductor ?: return
        if (conductor.state == TransportState.Running || conductor.state == TransportState.CountingIn) {
            conductor.pause()
            diag.event("practice", "paused because the app was backgrounded at pos=${conductor.position().value}")
            _state.value = _state.value.copy(transport = conductor.state)
        }
    }

    public fun resume() {
        val conductor = conductor ?: return
        conductor.resume()
        diag.event("practice", "resumed at pos=${conductor.position().value}")
        _state.value = _state.value.copy(transport = conductor.state)
    }

    public fun finish() {
        val current = judgeState ?: return
        val judge = this.judge ?: return
        val conductor = conductor
        conductor?.stop()
        val result = judge.finish(current)
        judgeState = null
        collection?.cancel()
        diag.event(
            "practice",
            "finished correct=${result.correct}/${result.notesExpected} " +
                "accuracy=${"%.2f".format(result.accuracy)}",
        )
        _state.value = _state.value.copy(
            transport = TransportState.Finished,
            result = result,
        )
    }

    private fun apply(settled: List<NoteJudgement>) {
        val merged = _state.value.verdicts.toMutableMap()
        var extras = _state.value.extras
        settled.forEach { judgement ->
            when (judgement) {
                is NoteJudgement.OfNote -> {
                    merged[judgement.noteIndex] = judgement.verdict
                    diag.event(
                        "judge",
                        "n=${judgement.noteIndex} -> ${judgement.verdict} " +
                            "[conf=${judgement.confidence} src=${_state.value.inputLabel}]",
                    )
                }
                // Counted rather than mapped: an extra answers to no notehead, so there is nothing
                // to colour — but it must still be visible, or a trill of wrong notes reads as a
                // clean performance.
                is NoteJudgement.Unexpected -> {
                    extras++
                    diag.event(
                        "judge",
                        "extra ${judgement.verdict.heard.number} at ${judgement.verdict.atTicks.value} " +
                            "[conf=${judgement.confidence} src=${_state.value.inputLabel}]",
                    )
                }
            }
        }
        _state.value = _state.value.copy(verdicts = merged, extras = extras)
    }

    public fun dismissRefusal() {
        _state.value = _state.value.copy(refusal = null)
    }

    public fun dismissResult() {
        _state.value = _state.value.copy(result = null)
    }

    /** Reloads the same score, source and judge from scratch — a fresh session, not a resumed one. */
    public fun restart() {
        val (score, source, judge) = loaded ?: return
        diag.event("practice", "restarting '${score.title}'")
        load(score, source, judge)
    }

    override fun onCleared() {
        collection?.cancel()
        conductor?.stop()
    }
}
