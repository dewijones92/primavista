package com.dewijones92.primavista.di

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import com.dewijones92.primavista.audio.AudioRecordPcmCapture
import com.dewijones92.primavista.audio.AudioTrackTonePlayer
import com.dewijones92.primavista.audio.ClickMetronome
import com.dewijones92.primavista.audio.MicPitchAnswerSource
import com.dewijones92.primavista.audio.MicrophonePermission
import com.dewijones92.primavista.audio.SystemMonotonicClock
import com.dewijones92.primavista.common.Diag
import com.dewijones92.primavista.common.RingBufferDiag
import com.dewijones92.primavista.data.AssetGlyphMetricsSource
import com.dewijones92.primavista.database.DatabaseOpening
import com.dewijones92.primavista.database.PracticeSettings
import com.dewijones92.primavista.database.PrimaVistaDatabase
import com.dewijones92.primavista.database.RoomJourneyStore
import com.dewijones92.primavista.database.RoomSessionStore
import com.dewijones92.primavista.database.RoomSettingsStore
import com.dewijones92.primavista.database.RoomSkillStore
import com.dewijones92.primavista.database.SkillUpdateRule
import com.dewijones92.primavista.notation.BravuraGlyphMetrics
import com.dewijones92.primavista.notation.ClassicalStaffLayout
import com.dewijones92.primavista.pitch.YinNoteTracker
import com.dewijones92.primavista.practice.AdaptivePlacementRead
import com.dewijones92.primavista.practice.AnswerSource
import com.dewijones92.primavista.practice.Conductor
import com.dewijones92.primavista.practice.Curriculum
import com.dewijones92.primavista.practice.KeyboardTapSource
import com.dewijones92.primavista.practice.PerformanceJudge
import com.dewijones92.primavista.practice.PlacementRead
import com.dewijones92.primavista.practice.SkillOutcome
import com.dewijones92.primavista.practice.SkillState
import com.dewijones92.primavista.practice.SpacedPracticeScheduler
import com.dewijones92.primavista.practice.Stage
import com.dewijones92.primavista.practice.TempoConductor
import com.dewijones92.primavista.practice.Tolerances
import com.dewijones92.primavista.practice.WindowedJudge
import com.dewijones92.primavista.score.DerivedScoreSkills
import com.dewijones92.primavista.score.DomMusicXmlParser
import com.dewijones92.primavista.score.Polyphony
import com.dewijones92.primavista.score.Score
import com.dewijones92.primavista.score.SeededExerciseGenerator
import com.dewijones92.primavista.score.TimeSignature
import java.time.ZoneId

/**
 * The whole object graph, wired by hand. Construction is code, so a missing dependency is a compile
 * error rather than a crash on the screen that needs it — the reason there is no Hilt or Koin here.
 *
 * This is also the **only** place that knows which concrete adapter is in play. Everything else
 * takes a port, which is what makes MIDI a new class rather than a change everywhere.
 */
public class AppContainer(private val context: Context) {

    public val diag: Diag = RingBufferDiag()

    // --- notation -------------------------------------------------------------------------------

    /**
     * Parsed once and held: the Bravura metadata is 1.2MB, so re-parsing it per layout would be
     * visible while scrolling. Lazy so a launch that never opens a staff never pays for it.
     */
    public val glyphMetrics: BravuraGlyphMetrics by lazy {
        BravuraGlyphMetrics.from(AssetGlyphMetricsSource(context))
    }

    public val staffLayout: ClassicalStaffLayout = ClassicalStaffLayout(diag)

    // --- score ----------------------------------------------------------------------------------

    public val scoreSkills: DerivedScoreSkills = DerivedScoreSkills()
    public val musicXmlParser: DomMusicXmlParser = DomMusicXmlParser(diag)
    public val exerciseGenerator: SeededExerciseGenerator = SeededExerciseGenerator(diag)

    // --- practice -------------------------------------------------------------------------------

    public val tolerances: Tolerances = Tolerances()

    /**
     * A judge is per-score because skills are per-note, and the map is built once here rather than
     * derived per verdict — a session judges every note it hears, and re-deriving a note's skills on
     * each one would do the same work hundreds of times.
     *
     * Keying by the `Note` itself is safe because a note carries its own onset, so two notes in one
     * score can never be equal.
     */
    public fun judgeFor(score: Score): PerformanceJudge {
        val skillsByNote = score.attackedNotes.withIndex().associate { (index, note) ->
            note to scoreSkills.skillsOf(score, index)
        }
        return WindowedJudge(tolerances) { note -> skillsByNote[note].orEmpty() }
    }

    /** [tempoCeilingBpm] is Dewi's stored top tempo; a piece written slower keeps its own. */
    public fun conductorFor(
        score: Score,
        tempoCeilingBpm: Int,
        countInBeats: Int = DEFAULT_COUNT_IN_BEATS,
    ): Conductor =
        TempoConductor(
            clock = SystemMonotonicClock,
            tempoBpm = sessionTempoBpm(score.defaultTempoBpm, tempoCeilingBpm),
            countInBeats = countInBeats,
            time = score.measures.firstOrNull()?.time ?: TimeSignature.FourFour,
        )

    /** The generator owns spec-targeting; the scheduler is handed it rather than keeping a copy. */
    public val scheduler: SpacedPracticeScheduler =
        SpacedPracticeScheduler(exerciseGenerator::specTargeting)

    // --- the path -------------------------------------------------------------------------------

    public val curriculum: Curriculum = Curriculum.Standard

    public val placementRead: PlacementRead = AdaptivePlacementRead(curriculum)

    /** Parsed and windowed once for the whole app: the tab and the scheduler ask the same object. */
    public val shippedRepertoire: ShippedRepertoire = ShippedRepertoire(musicXmlParser, diag, curriculum)

    /** Supplied rather than read inside the fold, so two adjacent reads cannot disagree after a flight. */
    public val zone: ZoneId get() = ZoneId.systemDefault()

    // --- input ----------------------------------------------------------------------------------

    public val tapSource: KeyboardTapSource = KeyboardTapSource()

    public val tonePlayer: AudioTrackTonePlayer = AudioTrackTonePlayer(diag)

    public val metronome: ClickMetronome = ClickMetronome(diag)

    private val microphonePermission = MicrophonePermission {
        context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Built lazily because constructing it touches audio hardware, and a session that only ever taps
     * should not open the microphone — nor prompt for a permission it is not going to use.
     */
    private val micSourceDelegate = lazy {
        MicPitchAnswerSource(
            capture = AudioRecordPcmCapture(
                micPermission = microphonePermission,
                diag = diag,
                audioManager = context.getSystemService(AudioManager::class.java),
            ),
            trackerFor = { sampleRate -> YinNoteTracker(sampleRate, diag = diag) },
            tonePlayer = tonePlayer,
            diag = diag,
        )
    }

    public val micSource: MicPitchAnswerSource by micSourceDelegate

    public fun sourceFor(mode: InputMode): AnswerSource = when (mode) {
        InputMode.Tap -> tapSource
        InputMode.Mic -> micSource
    }

    /** Asked before offering PLAY IT, so the app never opens a record that would return silence. */
    public fun microphoneGranted(): Boolean = microphonePermission.isGranted()

    /** What a session would open on right now: the stored choice, met by the permission it has today. */
    public suspend fun currentInput(): InputMode =
        openingInput(settingsStore?.settings() ?: PracticeSettings(), microphoneGranted()).mode

    /** Where the curriculum says Dewi stands, read fresh. There is no stored answer to this. */
    public suspend fun standingStage(): Stage =
        curriculum.currentStage(skillStore?.states().orEmpty())

    // --- storage --------------------------------------------------------------------------------

    /**
     * An unreadable database is surfaced, never silently replaced. Dropping the file would take the
     * practice history with it, which is precisely what docs/spec.md I4 forbids — so the failure is
     * carried in the type and the recovery stays Dewi's decision.
     */
    public val databaseOpening: DatabaseOpening by lazy { PrimaVistaDatabase.open(context, diag) }

    public val database: PrimaVistaDatabase?
        get() = (databaseOpening as? DatabaseOpening.Opened)?.database

    public val skillStore: RoomSkillStore? by lazy {
        database?.let { RoomSkillStore(it, scheduler::update, diag) }
    }

    public val sessionStore: RoomSessionStore? by lazy {
        database?.let { RoomSessionStore(it, diag) }
    }

    /** Constructed here and nowhere else: two settings stores would be two answers. */
    public val settingsStore: RoomSettingsStore? by lazy {
        database?.let { RoomSettingsStore(it, diag) }
    }

    public val journeyStore: RoomJourneyStore? by lazy { database?.let { RoomJourneyStore(it, diag) } }

    private val wholeWiring: AppPracticeWiring by lazy { AppPracticeWiring(this) }

    /** One session's worth of the graph, so the view model takes a port rather than fourteen things. */
    public val practiceWiring: PracticeWiring get() = wholeWiring

    public val journeyWiring: JourneyWiring by lazy { AppJourneyWiring(this) }

    /** One rung's worth of the same graph. Only the narrowing differs — see `PracticeWirings.kt`. */
    public fun practiceWiringFor(stage: Stage): PracticeWiring = StagePracticeWiring(wholeWiring, stage)

    public fun probeWiring(probe: () -> PracticeSelection?): PracticeWiring = ProbeWiring(wholeWiring, probe)

    /**
     * Writes what a placement measured over whatever is already stored.
     *
     * The store persists what an update rule hands back, so seeding is that rule saying "these are
     * the states now" — which is why there is no second write path and no second transaction. See
     * `.claude/CODE-NOTES.md`.
     */
    public suspend fun seedSkills(seed: List<SkillState>, evidence: List<SkillOutcome>, nowEpochMillis: Long) {
        val opened = database
        if (opened == null || seed.isEmpty() || evidence.isEmpty()) {
            diag.event(
                "journey",
                "no skills seeded [database=${opened != null} seeding=${seed.size}states " +
                    "evidence=${evidence.size}outcomes]",
            )
            return
        }
        val replaced = seed.associateBy { it.tag }
        val rule = SkillUpdateRule { before, _, _ -> before.filterNot { it.tag in replaced } + seed }
        RoomSkillStore(opened, rule, diag).record(evidence, nowEpochMillis)
        diag.event(
            "journey",
            "seeded ${seed.size} skill states from the placement read, " +
                "${seed.count { it.isSolid }} of them solid, at now=$nowEpochMillis",
        )
    }

    public fun release() {
        tonePlayer.release()
        metronome.release()
        // Asking the delegate rather than the property: touching micSource here would open the
        // microphone in order to close it.
        if (micSourceDelegate.isInitialized()) micSource.release()
        diag.event("app", "released audio resources")
    }

    public companion object {
        public const val DEFAULT_COUNT_IN_BEATS: Int = 4
    }
}

/**
 * [polyphony] is what the adapter behind the mode can actually hear, and it travels with the mode so
 * the scheduler and the judge are asked the same question about the same thing. Ten fingers on the
 * on-screen keyboard are genuinely polyphonic; one microphone is not (docs/spec.md I3).
 *
 * [label] must equal the adapter's own `AnswerSource.label` — see `.claude/CODE-NOTES.md`.
 */
public enum class InputMode(public val polyphony: Polyphony, public val label: String) {
    Tap(Polyphony.Poly, "tap"),
    Mic(Polyphony.Mono, "mic");

    public companion object {
        /** The stored [com.dewijones92.primavista.database.PracticeSettings.inputLabel], read back. */
        public fun of(label: String?): InputMode? = entries.firstOrNull { it.label == label }
    }
}
