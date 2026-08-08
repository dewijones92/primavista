package com.dewijones92.primavista.di

import com.dewijones92.primavista.common.Diag
import com.dewijones92.primavista.database.PracticeSettings

/**
 * The preferences a session reads, and nothing else. Segregated from [PracticeWiring] so a caller
 * that only wants to know the tempo cannot also reach the corpus, the stores or the judge.
 */
public interface SessionPreferences {
    public suspend fun settings(): PracticeSettings

    /**
     * Persists one preference changed mid-session, leaving the rest of the row alone. Read-modify-
     * write lives here so no caller ends up holding its own copy of [PracticeSettings].
     */
    public suspend fun remember(change: (PracticeSettings) -> PracticeSettings)
}

/** [SessionPreferences] over the one settings store the container owns. */
internal class StoredPreferences(private val container: AppContainer, private val diag: Diag) :
    SessionPreferences {

    override suspend fun settings(): PracticeSettings {
        val store = container.settingsStore
        if (store == null) {
            diag.event(
                TAG,
                "settings NOT read: the database could not be opened, so this session runs on the " +
                    "defaults (ceiling=${PracticeSettings.DEFAULT_TEMPO_BPM}bpm, metronome on, input tap)",
            )
            return PracticeSettings()
        }
        return store.settings()
    }

    override suspend fun remember(change: (PracticeSettings) -> PracticeSettings) {
        val store = container.settingsStore
        if (store == null) {
            diag.event(TAG, "preference NOT saved: the database could not be opened")
            return
        }
        store.save(change(store.settings()))
    }

    private companion object {
        const val TAG = "settings"
    }
}

/**
 * The stored tempo is a **ceiling**, never an override. See `.claude/CODE-NOTES.md`.
 */
internal fun sessionTempoBpm(writtenBpm: Int, ceilingBpm: Int): Int = minOf(writtenBpm, ceilingBpm)

/**
 * What a session opens as, once the stored preference has met the permission the phone will
 * currently allow. [revoked] is what the screen says out loud — see `.claude/CODE-NOTES.md`.
 */
internal data class OpeningInput(val mode: InputMode, val revoked: Boolean)

internal fun openingInput(settings: PracticeSettings, micGranted: Boolean): OpeningInput {
    val stored = InputMode.of(settings.inputLabel)
    val revoked = stored == InputMode.Mic && !micGranted
    return OpeningInput(if (revoked) InputMode.Tap else stored ?: InputMode.Tap, revoked)
}
