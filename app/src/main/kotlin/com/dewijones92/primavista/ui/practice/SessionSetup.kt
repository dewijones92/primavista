package com.dewijones92.primavista.ui.practice

import com.dewijones92.primavista.di.InputMode

/**
 * What you change between runs: what is listening, and what to read next.
 *
 * Null on [PracticeScreen] hides the panel — a caller that drives the session itself, such as a
 * placement probe, must not offer "something else to read" in the middle of a measurement.
 */
public class SessionSetup(
    public val onInput: (InputMode) -> Unit,
    public val onListen: () -> Unit,
    public val onNext: () -> Unit,
    public val onDismissNotice: () -> Unit,
)
