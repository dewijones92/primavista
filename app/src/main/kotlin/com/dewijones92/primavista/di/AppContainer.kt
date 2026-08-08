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
import com.dewijones92.primavista.database.PrimaVistaDatabase
import com.dewijones92.primavista.database.RoomSessionStore
import com.dewijones92.primavista.database.RoomSkillStore
import com.dewijones92.primavista.notation.BravuraGlyphMetrics
import com.dewijones92.primavista.notation.ClassicalStaffLayout
import com.dewijones92.primavista.pitch.YinNoteTracker
import com.dewijones92.primavista.practice.AnswerSource
import com.dewijones92.primavista.practice.Conductor
import com.dewijones92.primavista.practice.KeyboardTapSource
import com.dewijones92.primavista.practice.PerformanceJudge
import com.dewijones92.primavista.practice.SpacedPracticeScheduler
import com.dewijones92.primavista.practice.TempoConductor
import com.dewijones92.primavista.practice.Tolerances
import com.dewijones92.primavista.practice.WindowedJudge
import com.dewijones92.primavista.score.DerivedScoreSkills
import com.dewijones92.primavista.score.DomMusicXmlParser
import com.dewijones92.primavista.score.Polyphony
import com.dewijones92.primavista.score.Score
import com.dewijones92.primavista.score.SeededExerciseGenerator
import com.dewijones92.primavista.score.TimeSignature

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

    public fun conductorFor(score: Score, countInBeats: Int = DEFAULT_COUNT_IN_BEATS): Conductor =
        TempoConductor(
            clock = SystemMonotonicClock,
            tempoBpm = score.defaultTempoBpm,
            countInBeats = countInBeats,
            time = score.measures.firstOrNull()?.time ?: TimeSignature.FourFour,
        )

    /** The generator owns spec-targeting; the scheduler is handed it rather than keeping a copy. */
    public val scheduler: SpacedPracticeScheduler =
        SpacedPracticeScheduler(exerciseGenerator::specTargeting)

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

    /** One session's worth of the graph, so the view model takes a port rather than fourteen things. */
    public val practiceWiring: PracticeWiring by lazy { AppPracticeWiring(this) }

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
 */
public enum class InputMode(public val polyphony: Polyphony) {
    Tap(Polyphony.Poly),
    Mic(Polyphony.Mono),
}
