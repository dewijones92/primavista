package com.dewijones92.primavista.di

import com.dewijones92.primavista.database.DifficultyCodec
import com.dewijones92.primavista.practice.SessionReplay
import com.dewijones92.primavista.practice.SessionReplayCodec
import com.dewijones92.primavista.practice.SpecText
import com.dewijones92.primavista.score.DifficultySpec

/**
 * The last session, in the form that lets a report re-judge it (docs/spec.md **I7**).
 *
 * Held in memory for exactly one session rather than persisted: a report answers *what just went
 * wrong*, and the practice history in `:core:database` is where sessions live for the long term.
 * Held here rather than on the view model because the Diagnostics screen is a different screen with
 * a different lifetime, and a report Dewi opens after leaving Practise must still carry the run.
 */
public object LastSession {
    private var replay: SessionReplay? = null

    public fun remember(session: SessionReplay) {
        replay = session
    }

    public fun forget() {
        replay = null
    }

    /**
     * The replay block for the report, or a line saying why there is none. A blank is not an
     * option: "nothing has been played yet" and "the run was lost" are different situations, and a
     * report that showed neither would leave a future session guessing.
     */
    public fun block(): String = replay
        ?.let { SessionReplayCodec.encode(it, StoredSpecText) }
        ?: "(no session has been played since this app started, so there is nothing to replay)"
}

/**
 * [SpecText] over the codec `:core:database` already owns.
 *
 * `:core:practice` cannot see the database, and the app should not gain a second encoding of a
 * difficulty spec — that duplication is the one this repo has already been bitten by twice. So the
 * app, which can see both, hands the existing one across.
 */
internal object StoredSpecText : SpecText {
    override fun encode(spec: DifficultySpec): String = DifficultyCodec.encode(spec)

    override fun decode(encoded: String): DifficultySpec? = DifficultyCodec.decode(encoded)
}
