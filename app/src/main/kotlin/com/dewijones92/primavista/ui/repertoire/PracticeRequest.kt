package com.dewijones92.primavista.ui.repertoire

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.dewijones92.primavista.score.CorpusPiece

/**
 * "Practise this one" crossing from the Repertoire tab to the Practise tab.
 *
 * The shell watches [count] and navigates; the practice route reads [pending] and loads it. Two
 * fields rather than one because they are consumed by different owners at different moments, and a
 * single take-once value would have the shell's navigation swallow the piece before the practice
 * route ever saw it. See `.claude/CODE-NOTES.md`.
 */
public object PracticeRequest {

    public var pending: CorpusPiece? by mutableStateOf(null)
        private set

    /** Increments on every request, so asking for the same piece twice still navigates. */
    public var count: Int by mutableIntStateOf(0)
        private set

    public fun request(piece: CorpusPiece) {
        pending = piece
        count++
    }

    /** Leaves [pending] in place: a re-entering Practise tab must load the same piece again. */
    public fun peek(): CorpusPiece? = pending

    public fun clear() {
        pending = null
    }
}
