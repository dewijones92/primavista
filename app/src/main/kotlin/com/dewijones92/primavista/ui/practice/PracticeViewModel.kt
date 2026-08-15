package com.dewijones92.primavista.ui.practice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dewijones92.primavista.audio.Metronome
import com.dewijones92.primavista.audio.TonePlayer
import com.dewijones92.primavista.common.Diag
import com.dewijones92.primavista.database.PracticeSettings
import com.dewijones92.primavista.database.SessionId
import com.dewijones92.primavista.database.StoredSession
import com.dewijones92.primavista.di.InputMode
import com.dewijones92.primavista.di.LastSession
import com.dewijones92.primavista.di.PracticeSelection
import com.dewijones92.primavista.di.PracticeWiring
import com.dewijones92.primavista.di.openingInput
import com.dewijones92.primavista.di.sessionTempoBpm
import com.dewijones92.primavista.notation.GlyphMetrics
import com.dewijones92.primavista.notation.StaffSpaces
import com.dewijones92.primavista.notation.StaffSystem
import com.dewijones92.primavista.practice.AnswerSource
import com.dewijones92.primavista.practice.ClaimedVerdict
import com.dewijones92.primavista.practice.Conductor
import com.dewijones92.primavista.practice.JudgeState
import com.dewijones92.primavista.practice.NoteJudgement
import com.dewijones92.primavista.practice.PauseLeg
import com.dewijones92.primavista.practice.PerformanceJudge
import com.dewijones92.primavista.practice.PlayedNote
import com.dewijones92.primavista.practice.ReadingLead
import com.dewijones92.primavista.practice.RefusalReason
import com.dewijones92.primavista.practice.ScoreRef
import com.dewijones92.primavista.practice.SessionReplay
import com.dewijones92.primavista.practice.SessionResult
import com.dewijones92.primavista.practice.Stage
import com.dewijones92.primavista.practice.TickTiming
import com.dewijones92.primavista.practice.TransportState
import com.dewijones92.primavista.practice.Verdict
import com.dewijones92.primavista.score.CorpusPiece
import com.dewijones92.primavista.score.Midi
import com.dewijones92.primavista.score.Note
import com.dewijones92.primavista.score.Score
import com.dewijones92.primavista.score.ScoreId
import com.dewijones92.primavista.score.ScoreOrigin
import com.dewijones92.primavista.score.SkillTag
import com.dewijones92.primavista.score.Ticks
import com.dewijones92.primavista.score.TimeSignature
import com.dewijones92.primavista.ui.results.drillTarget
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

/**
 * A session preference the reader changed mid-run.
 *
 * Sealed rather than an enum-plus-a-second-method because they are one thing — "this changed, apply
 * it, remember it" — and the third one is not a toggle at all. Adding a fourth adds a case, not an
 * entry point.
 */
public sealed interface PracticeChange {
    public data object Metronome : PracticeChange

    public data object Echo : PracticeChange

    public data class ReadAhead(val lead: ReadingLead) : PracticeChange
}

/** Which rung of [com.dewijones92.primavista.practice.Curriculum] Dewi is on. Null when unknown. */
public typealias StageSource = suspend () -> Stage?

public data class PracticeUiState(
    val score: Score? = null,
    val system: StaffSystem? = null,
    val transport: TransportState = TransportState.Idle,
    val position: Ticks = Ticks.ZERO,
    val playheadX: StaffSpaces = StaffSpaces.ZERO,
    val countInBeatsRemaining: Int = 0,
    /** How many beats this count-in started with, so the screen can show the ones already spent. */
    val countInBeats: Int = 0,
    val verdicts: Map<Int, Verdict> = emptyMap(),
    /** Notes played that answered to nothing written. Counted, because they have no notehead to colour. */
    val extras: Int = 0,
    val refusal: RefusalReason? = null,
    val result: SessionResult? = null,
    /** The run behind the results sheet, kept after it is dismissed. See `.claude/CODE-NOTES.md`. */
    val lastRun: SessionResult? = null,
    /** Where this session sits on the path. Null means it could not be read, never "stage one". */
    val stage: Stage? = null,
    val inputLabel: String = "",
    val tempoBpm: Int = 0,
    val input: InputMode = InputMode.Tap,
    val metronomeOn: Boolean = true,
    /** Play the written note after a wrong one. Muted for the mic, which would hear it and judge it. */
    val echoOn: Boolean = true,
    /** How far ahead of the playhead the page is covered. See [ReadingLead]. */
    val readingLead: ReadingLead = ReadingLead.Off,
    /**
     * Where the reading-ahead card sits, in staff spaces, or null when it is off.
     *
     * Carried on the state rather than computed by the screen: it is `xOf(position + lead)`, and
     * `xOf` is the layout engine's answer. A screen working it out from pixels would be a second
     * layout engine, which is the mistake `StaffCanvas` exists to avoid.
     */
    val coverX: StaffSpaces? = null,
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
public class PracticeViewModel(
    private val wiring: PracticeWiring,
    private val stages: StageSource = { null },
) : ViewModel() {

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
    private var opened = false
    private var runOpenedAtNanos = Long.MIN_VALUE
    private val playback = ScorePlayback(wiring.tonePlayer)

    public val glyphMetrics: GlyphMetrics get() = wiring.metrics

    /**
     * The ladder. Every entry to the screen asks the scheduler rather than opening the same piece.
     *
     * The **first** call also resolves what is listening from the stored preference, and does it
     * before anything is chosen — see `.claude/CODE-NOTES.md`.
     */
    public fun choose(intent: PracticeIntent) {
        if (_state.value.loading) return
        // The reset below clears it.
        val finished = _state.value.result
        val repeating = loaded?.let { PracticeSelection(it, _state.value.targeting, _state.value.choiceSummary) }
        _state.value = _state.value.copy(loading = true, notice = null, result = null)
        viewModelScope.launch {
            if (!opened) {
                opened = true
                val stored = wiring.preferences.settings()
                val granted = wiring.microphoneGranted()
                val opening = openingInput(stored, granted)
                diag.event(
                    TAG,
                    "opens on input=${opening.mode.name} [stored=${stored.inputLabel ?: "(unchosen)"} " +
                        "micGranted=$granted revoked=${opening.revoked}]",
                )
                _state.value = _state.value.copy(
                    input = opening.mode,
                    notice = if (opening.revoked) MIC_REVOKED else null,
                )
            }
            val input = _state.value.input
            val seed = wiring.nowEpochMillis()
            val selection = runCatching {
                wiring.selectionFor(intent, input, seed, repeating, finished)
            }.getOrElse {
                diag.event(TAG, "intent=$intent produced nothing: ${it::class.simpleName} ${it.message}")
                null
            }
            if (selection == null) {
                _state.value = _state.value.copy(loading = false, notice = NOTHING_TO_READ)
                return@launch
            }
            diag.event(TAG, "intent=$intent input=${input.name} seed=$seed -> '${selection.score.title}'")
            load(selection, input, mayListenFirst = intent != PracticeIntent.Again)
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
            load(selection, _state.value.input, mayListenFirst = true)
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
        if (muted) wiring.metronome.enabled = false
        _state.value = _state.value.copy(
            input = mode,
            metronomeOn = _state.value.metronomeOn && !muted,
            echoOn = !muted,
            notice = if (muted) MIC_MUTES else null,
        )
        viewModelScope.launch {
            wiring.preferences.remember { it.copy(inputLabel = mode.label) }
            val score = loaded ?: return@launch
            load(
                selection = PracticeSelection(score, _state.value.targeting, _state.value.choiceSummary),
                input = mode,
                mayListenFirst = false,
            )
        }
    }

    /** The metronome is a stored preference and is written back here; the echo is per-session only. */
    /**
     * Applies a changed preference and remembers the ones worth keeping. Echo is deliberately not
     * persisted: it is muted for the mic by the session itself, so a stored value would fight it.
     */
    public fun change(change: PracticeChange) {
        val current = _state.value
        val next = when (change) {
            PracticeChange.Metronome -> current.copy(metronomeOn = !current.metronomeOn)
            PracticeChange.Echo -> current.copy(echoOn = !current.echoOn)
            is PracticeChange.ReadAhead -> current.copy(readingLead = change.lead, coverX = null)
        }
        wiring.metronome.enabled = next.metronomeOn
        diag.event(
            TAG,
            "$change applied: metronome=${next.metronomeOn} echo=${next.echoOn} " +
                "lead=${next.readingLead} src=${next.inputLabel}",
        )
        _state.value = next
        remembering(change, next)?.let { remember -> viewModelScope.launch { wiring.preferences.remember(remember) } }
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
            lastRun = null,
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
                runOpenedAtNanos = conductor.nanosFor(conductor.position())
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
                runOpenedAtNanos = conductor.nanosFor(conductor.position())
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
                        if (note.atNanos < runOpenedAtNanos) {
                            diag.counted("input", "notesPlayedBeforeThisRunBegan")
                            return@collect
                        }
                        record?.heard(note)
                        val (next, settled) = judge.advance(current, note)
                        judgeState = next
                        if (settled.isNotEmpty()) applyJudgements(settled)
                        diag.counted("input", "notes-${source.label}")
                    }
                }
            }
        }
        armMetronome(wiring.metronome, score, conductor, _state.value.metronomeOn)
        _state.value = _state.value.copy(
            transport = conductor.state,
            previewing = false,
            result = null,
            lastRun = null,
            countInBeats = maxOf(_state.value.countInBeats, conductor.countInBeatsRemaining()),
        )
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
            coverX = readingCover(wiring, _state.value.readingLead, loaded, system, position),
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
            rememberReplay(record, conductor)
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

    /**
     * Applies what Dewi asked for, every time a session opens. See `.claude/CODE-NOTES.md` for what
     * each stored preference means and why [mayListenFirst] is not simply the stored flag.
     */
    private suspend fun load(selection: PracticeSelection, input: InputMode, mayListenFirst: Boolean) {
        if (conductor?.isRunning == true) pause()
        collection?.cancel()
        wiring.metronome.stop()
        conductor?.stop()
        val score = selection.score
        val stage = stages.read(diag)
        val stored = wiring.preferences.settings()
        val source = wiring.sourceFor(input)
        val judge = wiring.judgeFor(score)
        this.source = source
        this.judge = judge
        this.judgeState = null
        this.record = null
        loaded = score

        val refusal = judge.accepts(score, source)
        if (refusal != null) {
            this.conductor = null
            diag.event(
                TAG,
                "refused '${score.title}' src=${source.label} poly=${score.polyphony} " +
                    "polyFromBar=${score.firstPolyphonicMeasure() ?: "none"} reason=$refusal",
            )
            _state.value = _state.value.copy(
                score = score, system = null, refusal = refusal, result = null, loading = false,
                inputLabel = source.label, input = input,
                tempoBpm = sessionTempoBpm(score.defaultTempoBpm, stored.tempoBpm),
                targeting = selection.targeting, choiceSummary = selection.summary,
                transport = TransportState.Idle, previewing = false,
                stage = stage, lastRun = null, countInBeats = 0,
            )
            return
        }

        val system = withContext(Dispatchers.Default) { wiring.layout.layout(score, wiring.metrics) }
        val conductor = wiring.conductorFor(score, stored.tempoBpm).also { this.conductor = it }
        val micMutes = input == InputMode.Mic
        val metronomeOn = stored.metronomeOn && !micMutes
        wiring.metronome.enabled = metronomeOn
        diag.event(
            TAG,
            "settings applied tempo=${conductor.tempoBpm}bpm " +
                "[written=${score.defaultTempoBpm}bpm ceiling=${stored.tempoBpm}bpm " +
                "capped=${conductor.tempoBpm < score.defaultTempoBpm}] " +
                "metronome=$metronomeOn [stored=${stored.metronomeOn} micMutes=$micMutes] " +
                "input=${input.name} [stored=${stored.inputLabel ?: "(unchosen)"}] " +
                "listenFirst=${stored.listenFirstOn} [applies=$mayListenFirst] " +
                "readingLead=${ReadingLead(stored.readingLeadBeats)}",
        )
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
                "metronome=${s.metronomeOn} echo=${s.echoOn} tempo=${s.tempoBpm}bpm " +
                "lead=${s.readingLead} coverX=${s.coverX?.value?.toInt()}"
        }

        _state.value = _state.value.copy(
            score = score, system = system, transport = conductor.state, position = Ticks.ZERO,
            playheadX = StaffSpaces.ZERO, coverX = null, countInBeatsRemaining = 0, countInBeats = 0,
            readingLead = ReadingLead(stored.readingLeadBeats),
            verdicts = emptyMap(), extras = 0,
            refusal = null, result = null, lastRun = null, inputLabel = source.label, input = input,
            tempoBpm = conductor.tempoBpm, metronomeOn = metronomeOn, echoOn = !micMutes,
            targeting = selection.targeting, choiceSummary = selection.summary,
            stage = stage, previewing = false, loading = false,
        )

        when {
            stored.listenFirstOn && mayListenFirst -> listen()
            stored.listenFirstOn ->
                diag.event(TAG, "listen-first is on but this load is a repeat or an input switch, so nothing plays")
        }
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
            conductor?.let { rememberReplay(record, it) }
            record?.save(finished = true)
            wiring.recordSkills(result.skillOutcomes)
        }
        _state.value = _state.value.copy(
            transport = TransportState.Finished,
            result = result,
            lastRun = result,
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
 * What each intent means. [finished] is the session the results sheet is showing, and the drill it
 * resolves to is the one that sheet already named — `ui.results.drillTarget` decides it once.
 */
private suspend fun PracticeWiring.selectionFor(
    intent: PracticeIntent,
    input: InputMode,
    seed: Long,
    repeating: PracticeSelection?,
    finished: SessionResult?,
): PracticeSelection = when (intent) {
    PracticeIntent.Next -> chooseNext(input.polyphony, seed)
    PracticeIntent.Again -> repeating ?: chooseNext(input.polyphony, seed)
    PracticeIntent.DrillWeakest -> finished?.let { drillTarget(it, input.polyphony) }
        ?.let { chooseDrill(it.tag, input.polyphony, seed) }
        ?: chooseNext(input.polyphony, seed)
}

/**
 * Where Dewi stands on the path — the same rung the wiring narrowed its choice to, read again for
 * the header. A failed read leaves it unknown rather than quietly reading as stage one.
 */
private suspend fun StageSource.read(diag: Diag): Stage? {
    val stage = runCatching { this() }.getOrElse {
        diag.event(TAG, "the stage could not be read: ${it::class.simpleName} ${it.message}")
        null
    }
    diag.event(
        TAG,
        "stage=${stage?.id?.number ?: "unknown"} '${stage?.title ?: "-"}' " +
            "stageSkills=${stage?.skills?.size ?: 0} (shown in the header)",
    )
    return stage
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

    /** Every note heard, kept because a verdict cannot be re-judged from its own outcome (spec I7). */
    val played: MutableList<PlayedNote> = mutableListOf()

    fun add(settled: List<NoteJudgement>) {
        judgements += settled
    }

    fun heard(note: PlayedNote) {
        played += note
    }

    /** What the report carries, so a week later this run can be re-judged rather than guessed at. */
    fun replay(legs: List<PauseLeg>): SessionReplay = SessionReplay(
        score = scoreRefOf(score),
        tempoBpm = tempoBpm,
        time = score.measures.firstOrNull()?.time ?: TimeSignature.FourFour,
        legs = legs,
        inputLabel = source.label,
        polyphony = source.polyphony,
        latency = source.latency,
        played = played.toList(),
        claimed = judgements.map(ClaimedVerdict::of),
    )

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

private const val MIC_REVOKED =
    "PLAY IT is your saved input, but the microphone permission is off, so this session opens on TAP. " +
        "Tap MIC to grant it again."

private const val MIC_MUTES =
    "PLAY IT is listening, so the metronome and note echo are off: the mic would hear them and score them as notes."

/**
 * Where the reading-ahead card sits, or null when it is off — nothing drawn rather than a cover
 * parked at the far left.
 *
 * A free function because it is geometry, not session state: it asks [ReadingLead] for a musical
 * position and the layout engine for the x of it, and owns neither.
 */
private fun readingCover(
    wiring: PracticeWiring,
    lead: ReadingLead,
    score: Score?,
    system: StaffSystem,
    position: Ticks,
): StaffSpaces? {
    if (!lead.isOn) return null
    val time = score?.measures?.firstOrNull()?.time ?: TimeSignature.FourFour
    return wiring.layout.xOf(system, lead.coversUpTo(position, time))
}

/**
 * What to persist for a change, or null for one that is only for this run.
 *
 * Echo is deliberately not stored: the session mutes it for the mic by itself, so a remembered
 * value would fight that every time the input switched.
 */
private fun remembering(
    change: PracticeChange,
    next: PracticeUiState,
): ((PracticeSettings) -> PracticeSettings)? = when (change) {
    PracticeChange.Metronome -> { settings -> settings.copy(metronomeOn = next.metronomeOn) }
    is PracticeChange.ReadAhead -> { settings -> settings.copy(readingLeadBeats = change.lead.beats) }
    PracticeChange.Echo -> null
}

/**
 * How to name this score in a report so a later build can rebuild it.
 *
 * A generated exercise carries its seed and spec and is reproducible exactly; a parsed one carries
 * the id it was read under, and a passage's id encodes the bars it came from (see `Score.excerpt`).
 */
private fun scoreRefOf(score: Score): ScoreRef = when (val origin = score.origin) {
    is ScoreOrigin.Generated -> ScoreRef.Generated(origin.seed, origin.spec)
    is ScoreOrigin.Parsed -> passageRefOf(score.id) ?: ScoreRef.Shipped(score.id)
}

private fun passageRefOf(id: ScoreId): ScoreRef.Passage? {
    val piece = id.value.substringBeforeLast(PASSAGE_MARK, missingDelimiterValue = "")
    if (piece.isEmpty()) return null
    val bars = id.value.substringAfterLast(PASSAGE_MARK).split(PASSAGE_RANGE)
    val from = bars.firstOrNull()?.toIntOrNull() ?: return null
    val last = bars.getOrNull(1)?.toIntOrNull() ?: return null
    return ScoreRef.Passage(ScoreId(piece), fromBar = from, bars = last - from + 1)
}

private const val PASSAGE_MARK = "#"
private const val PASSAGE_RANGE = "-"

/**
 * Puts the run where the diagnostics report can find it (docs/spec.md I7).
 *
 * Called at pause as well as at finish, for the same reason the session itself is written down
 * twice: a run killed mid-piece is exactly the one worth being able to re-judge.
 */
private fun rememberReplay(record: SessionRecord?, conductor: Conductor) {
    record?.let { LastSession.remember(it.replay(conductor.pauseLegs())) }
}
