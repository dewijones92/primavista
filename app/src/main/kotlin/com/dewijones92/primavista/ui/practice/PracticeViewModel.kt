package com.dewijones92.primavista.ui.practice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dewijones92.primavista.audio.Metronome
import com.dewijones92.primavista.audio.TonePlayer
import com.dewijones92.primavista.common.Diag
import com.dewijones92.primavista.database.SessionId
import com.dewijones92.primavista.database.StoredSession
import com.dewijones92.primavista.di.InputMode
import com.dewijones92.primavista.di.PracticeSelection
import com.dewijones92.primavista.di.PracticeWiring
import com.dewijones92.primavista.notation.GlyphMetrics
import com.dewijones92.primavista.notation.StaffSpaces
import com.dewijones92.primavista.notation.StaffSystem
import com.dewijones92.primavista.practice.AnswerSource
import com.dewijones92.primavista.practice.Conductor
import com.dewijones92.primavista.practice.JudgeState
import com.dewijones92.primavista.practice.NoteJudgement
import com.dewijones92.primavista.practice.PerformanceJudge
import com.dewijones92.primavista.practice.RefusalReason
import com.dewijones92.primavista.practice.SessionResult
import com.dewijones92.primavista.practice.TickTiming
import com.dewijones92.primavista.practice.TransportState
import com.dewijones92.primavista.practice.Verdict
import com.dewijones92.primavista.score.CorpusPiece
import com.dewijones92.primavista.score.Midi
import com.dewijones92.primavista.score.Note
import com.dewijones92.primavista.score.Polyphony
import com.dewijones92.primavista.score.Score
import com.dewijones92.primavista.score.SkillTag
import com.dewijones92.primavista.score.Ticks
import com.dewijones92.primavista.score.TimeSignature
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

private const val TAG = "practice"
private const val ECHO_MILLIS = 450L
private const val NANOS_PER_MILLI = 1_000_000L

/** What the screen is asking the session for. Named actions, so a report says which button ran. */
public enum class PracticeIntent { Next, DrillWeakest, Again }

public enum class PracticeToggle { Metronome, Echo }

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
    val input: InputMode = InputMode.Tap,
    val metronomeOn: Boolean = true,
    /** Play the written note after a wrong one. Muted for the mic, which would hear it and judge it. */
    val echoOn: Boolean = true,
    /** The skills this piece was chosen to drill, so the screen can say why it is on. */
    val targeting: Set<SkillTag> = emptySet(),
    val choiceSummary: String = "",
    /** Playing the piece to Dewi rather than judging him. */
    val previewing: Boolean = false,
    val loading: Boolean = false,
    val notice: String? = null,
)

/**
 * Drives one practice session: chooses what to read, samples the [Conductor], feeds the
 * [PerformanceJudge], drives the metronome from that same sample, and writes what happened down.
 *
 * It deliberately holds no idea of "now" of its own — [tick] is called from the UI's frame clock
 * and asks the Conductor. See .claude/CODE-NOTES.md for why sampling from a frame clock is not the
 * same mistake as deriving timing from recomposition, and why backgrounding pauses rather than
 * freezes.
 */
public class PracticeViewModel(private val wiring: PracticeWiring) : ViewModel() {

    private val diag: Diag = wiring.diag
    private val _state = MutableStateFlow(PracticeUiState())
    public val state: StateFlow<PracticeUiState> = _state.asStateFlow()

    private var conductor: Conductor? = null
    private var source: AnswerSource? = null
    private var judge: PerformanceJudge? = null
    private var judgeState: JudgeState? = null
    private var collection: Job? = null
    private var loaded: Score? = null
    private var record: SessionRecord? = null
    private var handledRequest = 0
    private val playback = ScorePlayback(wiring.tonePlayer)

    public val glyphMetrics: GlyphMetrics get() = wiring.metrics

    /** The ladder. Every entry to the screen asks the scheduler rather than opening the same piece. */
    public fun choose(intent: PracticeIntent) {
        if (_state.value.loading) return
        _state.value = _state.value.copy(loading = true, notice = null, result = null)
        viewModelScope.launch {
            val input = _state.value.input
            val seed = wiring.nowEpochMillis()
            val selection = runCatching {
                when (intent) {
                    PracticeIntent.Next -> wiring.chooseNext(input.polyphony, seed)
                    PracticeIntent.Again ->
                        loaded
                            ?.let { PracticeSelection(it, _state.value.targeting, _state.value.choiceSummary) }
                            ?: wiring.chooseNext(input.polyphony, seed)
                    PracticeIntent.DrillWeakest -> drillTarget(_state.value.result, input)
                        ?.let { wiring.chooseDrill(it, input.polyphony, seed) }
                        ?: wiring.chooseNext(input.polyphony, seed)
                }
            }.getOrElse {
                diag.event(TAG, "intent=$intent produced nothing: ${it::class.simpleName} ${it.message}")
                null
            }
            if (selection == null) {
                _state.value = _state.value.copy(loading = false, notice = NOTHING_TO_READ)
                return@launch
            }
            diag.event(TAG, "intent=$intent input=${input.name} seed=$seed -> '${selection.score.title}'")
            load(selection, input)
            if (intent == PracticeIntent.Again) play()
        }
    }

    /** A piece asked for from the Repertoire tab. [requestId] makes a repeat request re-load it. */
    public fun openPiece(requestId: Int, piece: CorpusPiece?) {
        if (piece == null || requestId == handledRequest) return
        handledRequest = requestId
        _state.value = _state.value.copy(loading = true, notice = null, result = null)
        viewModelScope.launch {
            val selection = wiring.open(piece)
            if (selection == null) {
                _state.value = _state.value.copy(loading = false, notice = "\"${piece.title}\" could not be read.")
                return@launch
            }
            diag.event(TAG, "repertoire request=$requestId -> '${selection.score.title}'")
            load(selection, _state.value.input)
        }
    }

    /**
     * [granted] is the permission answer, and a refusal is an ordinary thing a person does: it
     * surfaces as a message and leaves the input where it was, never as a crash and never as a mic
     * session that would record silence and blame Dewi for it.
     */
    public fun selectInput(mode: InputMode, granted: Boolean = true) {
        if (!granted) {
            diag.event(TAG, "input stays ${_state.value.input.name}: RECORD_AUDIO refused, PLAY IT cannot listen")
            _state.value = _state.value.copy(notice = MIC_REFUSED, input = InputMode.Tap)
            return
        }
        if (mode == _state.value.input) return
        val muted = mode == InputMode.Mic
        diag.event(
            TAG,
            "input ${_state.value.input.name}->${mode.name} poly=${mode.polyphony} " +
                "metronome=${!muted} echo=${!muted} (the mic hears the app's own sound, so both mute)",
        )
        _state.value = _state.value.copy(
            input = mode,
            metronomeOn = !muted,
            echoOn = !muted,
            notice = if (muted) MIC_MUTES else null,
        )
        wiring.metronome.enabled = !muted
        val score = loaded ?: return
        viewModelScope.launch {
            load(PracticeSelection(score, _state.value.targeting, _state.value.choiceSummary), mode)
        }
    }

    public fun toggle(feature: PracticeToggle) {
        val current = _state.value
        val next = when (feature) {
            PracticeToggle.Metronome -> current.copy(metronomeOn = !current.metronomeOn)
            PracticeToggle.Echo -> current.copy(echoOn = !current.echoOn)
        }
        wiring.metronome.enabled = next.metronomeOn
        diag.event(TAG, "$feature toggled: metronome=${next.metronomeOn} echo=${next.echoOn} src=${next.inputLabel}")
        _state.value = next
    }

    /**
     * Hear it before you read it, through the same transport and the same clock — the tone player is
     * fed from [tick], so the note you hear is the note under the playhead by construction rather
     * than by a second timer agreeing with the first (docs/spec.md I1).
     */
    public fun listen() {
        val conductor = conductor ?: return
        val score = loaded ?: return
        if (conductor.isRunning) return
        judgeState = null
        record = null
        playback.begin(score)
        conductor.start()
        armMetronome(wiring.metronome, score, conductor, _state.value.metronomeOn)
        diag.event(
            TAG,
            "listen '${score.title}' notes=${score.attackedNotes.size} tempo=${conductor.tempoBpm}bpm " +
                "(playing, not judging: no verdicts come from this)",
        )
        _state.value = _state.value.copy(
            previewing = true,
            transport = conductor.state,
            verdicts = emptyMap(),
            extras = 0,
            result = null,
            notice = null,
        )
    }

    /** Start, or resume. One action, because the transport button is one button. */
    public fun play() {
        val conductor = conductor ?: return
        val score = loaded ?: return
        val source = this.source ?: return
        val judge = this.judge ?: return
        when (conductor.state) {
            TransportState.Running, TransportState.CountingIn -> return
            TransportState.Paused -> {
                conductor.resume()
                val retimed = judgeState?.let { judge.retime(it, conductor.timingSnapshot()) }
                diag.event(
                    TAG,
                    "resumed at pos=${conductor.position().value}ticks " +
                        "origin=${conductor.nanosFor(Ticks.ZERO)}ns " +
                        "judgeRetimed=${retimed != null} " +
                        "(without the retime every later note reads late by the pause)",
                )
                judgeState = retimed
            }
            TransportState.Idle, TransportState.Finished -> {
                conductor.start()
                judgeState = judge.begin(score, conductor.timingSnapshot())
                record = SessionRecord(wiring, score, source, conductor.tempoBpm)
                diag.event(
                    TAG,
                    "start '${score.title}' tempo=${conductor.tempoBpm}bpm " +
                        "countIn=${conductor.countInBeatsRemaining()}beats " +
                        "origin=${conductor.nanosFor(Ticks.ZERO)}ns " +
                        "src=${source.label} notes=${score.attackedNotes.size}",
                )
                collection?.cancel()
                collection = viewModelScope.launch {
                    source.notes().collect { note ->
                        val current = judgeState
                        if (current == null) {
                            diag.counted("input", "notesWhileNotJudging")
                            return@collect
                        }
                        val (next, settled) = judge.advance(current, note)
                        judgeState = next
                        if (settled.isNotEmpty()) applyJudgements(settled)
                        diag.counted("input", "notes-${source.label}")
                    }
                }
            }
        }
        armMetronome(wiring.metronome, score, conductor, _state.value.metronomeOn)
        _state.value = _state.value.copy(transport = conductor.state, previewing = false, result = null)
    }

    /**
     * Called once per display frame. The Conductor is the source of the time; the frame clock only
     * decides when to look at it, and the metronome is handed that same reading rather than keeping
     * its own (docs/spec.md I1).
     */
    public fun tick() {
        val conductor = conductor ?: return
        val system = _state.value.system ?: return
        if (!conductor.isRunning) return

        val position = conductor.position()
        wiring.metronome.onPosition(position)

        val current = judgeState
        val judge = this.judge
        if (current != null && judge != null) {
            val (next, settled) = judge.advanceTime(current, position)
            judgeState = next
            if (settled.isNotEmpty()) applyJudgements(settled)
        }
        if (_state.value.previewing) playback.advance(position, conductor)

        _state.value = _state.value.copy(
            position = position,
            playheadX = wiring.layout.xOf(system, position),
            transport = conductor.state,
            countInBeatsRemaining = conductor.countInBeatsRemaining(),
        )
        diag.counted(TAG, "ticks")

        val score = loaded
        if (score != null && position >= closesAt(score, conductor, judge)) finish()
    }

    /**
     * Backgrounding **pauses**, and that is a decision rather than a side effect. Letting the
     * Conductor run on while nothing is sampling it would return to a position seconds ahead with
     * every note in between marked Missed — blaming Dewi for a phone call. The session is written to
     * disk here as well as at the end, because a process killed mid-piece must not lose it
     * (docs/spec.md I4).
     */
    public fun pause() {
        val conductor = conductor ?: return
        if (!conductor.isRunning) return
        conductor.pause()
        wiring.metronome.stop()
        wiring.tonePlayer.stopAll()
        val record = this.record
        if (record != null && !_state.value.previewing) {
            diag.event(
                TAG,
                "paused at pos=${conductor.position().value}ticks judged=${record.judgements.size}; " +
                    "saving now so a kill cannot lose it",
            )
            viewModelScope.launch { record.save(finished = false) }
        } else {
            diag.event(TAG, "paused at pos=${conductor.position().value}ticks (listening; nothing to save)")
        }
        _state.value = _state.value.copy(transport = conductor.state, previewing = false)
    }

    /** Leaves [PracticeUiState.refusal] alone. See `.claude/CODE-NOTES.md`. */
    public fun dismiss() {
        _state.value = _state.value.copy(result = null, notice = null)
    }

    private suspend fun load(selection: PracticeSelection, input: InputMode) {
        collection?.cancel()
        wiring.metronome.stop()
        conductor?.stop()
        val score = selection.score
        val source = wiring.sourceFor(input)
        val judge = wiring.judgeFor(score)
        this.source = source
        this.judge = judge
        this.judgeState = null
        this.record = null
        loaded = score

        val refusal = judge.accepts(score, source)
        if (refusal != null) {
            diag.event(
                TAG,
                "refused '${score.title}' src=${source.label} poly=${score.polyphony} " +
                    "polyFromBar=${score.firstPolyphonicMeasure() ?: "none"} reason=$refusal",
            )
            _state.value = _state.value.copy(
                score = score, system = null, refusal = refusal, result = null, loading = false,
                inputLabel = source.label, input = input, tempoBpm = score.defaultTempoBpm,
                targeting = selection.targeting, choiceSummary = selection.summary,
                transport = TransportState.Idle, previewing = false,
            )
            return
        }

        val system = withContext(Dispatchers.Default) { wiring.layout.layout(score, wiring.metrics) }
        val conductor = wiring.conductorFor(score).also { this.conductor = it }
        diag.event(
            TAG,
            "loaded '${score.title}' [origin=${score.origin::class.simpleName} " +
                "notes=${score.attackedNotes.size} bars=${score.measures.size} src=${source.label} " +
                "poly=${source.polyphony} tempo=${conductor.tempoBpm}bpm " +
                "lat=${source.latency.millis}ms/${source.latency.provenance} targeting=${selection.targeting}]",
        )
        diag.state(TAG) {
            val s = _state.value
            "transport=${s.transport} pos=${s.position.value} judged=${s.verdicts.size}/" +
                "${s.score?.attackedNotes?.size ?: 0} extras=${s.extras} src=${s.inputLabel} " +
                "metronome=${s.metronomeOn} echo=${s.echoOn}"
        }

        _state.value = _state.value.copy(
            score = score, system = system, transport = conductor.state, position = Ticks.ZERO,
            playheadX = StaffSpaces.ZERO, countInBeatsRemaining = 0, verdicts = emptyMap(), extras = 0,
            refusal = null, result = null, inputLabel = source.label, input = input,
            tempoBpm = conductor.tempoBpm, targeting = selection.targeting,
            choiceSummary = selection.summary, previewing = false, loading = false,
        )
    }

    private fun finish() {
        conductor?.stop()
        wiring.metronome.stop()
        wiring.tonePlayer.stopAll()
        if (_state.value.previewing) {
            diag.event(TAG, "listen finished at pos=${_state.value.position.value}ticks")
            _state.value = _state.value.copy(previewing = false, transport = TransportState.Finished)
            return
        }
        val current = judgeState ?: return
        val judge = this.judge ?: return
        val result = judge.finish(current)
        judgeState = null
        collection?.cancel()
        val record = this.record
        diag.event(
            TAG,
            "finished correct=${result.correct}/${result.notesExpected} extras=${result.extras} " +
                "accuracy=${"%.2f".format(result.accuracy)} clean=${"%.2f".format(result.cleanliness)} " +
                "src=${_state.value.inputLabel} tempo=${_state.value.tempoBpm}bpm " +
                "weakest=${result.skillOutcomes.filter { it.attempts > 0 }.minByOrNull { it.accuracy }?.tag}",
        )
        viewModelScope.launch {
            record?.save(finished = true)
            wiring.recordSkills(result.skillOutcomes)
        }
        _state.value = _state.value.copy(
            transport = TransportState.Finished,
            result = result,
            notice = null,
        )
    }

    private fun applyJudgements(settled: List<NoteJudgement>) {
        record?.add(settled)
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
                    val wrong = judgement.verdict as? Verdict.WrongPitch
                    if (wrong != null && _state.value.echoOn) wiring.tonePlayer.play(wrong.expected, ECHO_MILLIS)
                }
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

    override fun onCleared() {
        collection?.cancel()
        conductor?.stop()
        wiring.metronome.stop()
        wiring.tonePlayer.stopAll()
    }
}

/**
 * The click is armed from the bar the piece actually opens on, so a pickup bar accents its real
 * downbeat rather than one derived from tick zero.
 */
private fun armMetronome(metronome: Metronome, score: Score, conductor: Conductor, enabled: Boolean) {
    val opening = score.measures.firstOrNull()
    metronome.enabled = enabled
    metronome.configure(
        tempoBpm = conductor.tempoBpm,
        time = opening?.time ?: TimeSignature.FourFour,
        barStart = opening?.start ?: Ticks.ZERO,
    )
}

private val Conductor.isRunning: Boolean
    get() = state == TransportState.Running || state == TransportState.CountingIn

/**
 * A session ends one matching window *after* the last note, not on it.
 *
 * `PerformanceJudge.finish` deliberately settles nothing, and `advanceTime` only settles a note once
 * its window has closed — so stopping at `endsAt` leaves the final note with no verdict at all, and
 * takes away the slack every other note in the piece gets. The tail is measured through the
 * Conductor's own map so a tempo change or a pause cannot make it wrong.
 */
private fun closesAt(score: Score, conductor: Conductor, judge: PerformanceJudge?): Ticks {
    val tailNanos = ((judge?.tolerances?.maxWindowMillis ?: 0.0) * NANOS_PER_MILLI).toLong()
    return conductor.ticksAt(conductor.nanosFor(score.endsAt) + tailNanos)
}

/**
 * The worst skill of the session just played, which is what "drill the weakest" means on a results
 * sheet listing that session. A skill a mono input cannot hear is dropped here rather than being
 * generated and then refused.
 */
private fun drillTarget(result: SessionResult?, input: InputMode): SkillTag? = result?.skillOutcomes
    ?.filter { it.attempts > 0 }
    ?.filterNot { input.polyphony == Polyphony.Mono && it.tag == SkillTag.HandIndependence }
    ?.minByOrNull { it.accuracy }
    ?.tag

/**
 * One attempt, accumulated as it happens and written at pause as well as at the end.
 *
 * It holds the judgements rather than asking the judge for them, because the judge settles them
 * incrementally and a session killed mid-piece must still be able to show its working.
 */
private class SessionRecord(
    private val wiring: PracticeWiring,
    private val score: Score,
    private val source: AnswerSource,
    private val tempoBpm: Int,
) {
    private val id = SessionId(UUID.randomUUID().toString())
    private val startedAt = wiring.nowEpochMillis()

    val judgements: MutableList<NoteJudgement> = mutableListOf()

    fun add(settled: List<NoteJudgement>) {
        judgements += settled
    }

    suspend fun save(finished: Boolean) {
        wiring.save(
            StoredSession(
                id = id,
                scoreId = score.id,
                scoreTitle = score.title,
                origin = score.origin,
                inputLabel = source.label,
                polyphony = source.polyphony,
                tempoBpm = tempoBpm,
                latency = source.latency,
                startedAtEpochMillis = startedAt,
                finishedAtEpochMillis = if (finished) wiring.nowEpochMillis() else null,
                notesExpected = score.attackedNotes.size,
                correct = judgements.count { it.verdict.isClean },
            ),
            judgements.toList(),
        )
    }
}

/** Plays a score as the playhead reaches it. Given the Conductor's own reading, never its own timer. */
private class ScorePlayback(private val player: TonePlayer) {

    private var pending: List<Note> = emptyList()
    private var cursor = 0

    fun begin(score: Score) {
        pending = score.attackedNotes.sortedBy { it.onset.value }
        cursor = 0
    }

    fun advance(position: Ticks, timing: TickTiming) {
        while (cursor < pending.size && pending[cursor].onset <= position) {
            val onset = pending[cursor].onset
            val chord = mutableListOf<Midi>()
            var longest = Ticks.ZERO
            while (cursor < pending.size && pending[cursor].onset == onset) {
                val note = pending[cursor]
                chord += note.pitch.midi
                if (note.duration.ticks > longest) longest = note.duration.ticks
                cursor++
            }
            player.playChord(chord, (timing.nanosFor(onset + longest) - timing.nanosFor(onset)) / NANOS_PER_MILLI)
        }
    }
}

private const val NOTHING_TO_READ =
    "Couldn't put anything together to read. The diagnostics tab has the reason."

private const val MIC_REFUSED =
    "PLAY IT needs the microphone. Nothing is recorded or sent anywhere — the app has no internet permission."

private const val MIC_MUTES =
    "PLAY IT is listening, so the metronome and note echo are off: the mic would hear them and score them as notes."
