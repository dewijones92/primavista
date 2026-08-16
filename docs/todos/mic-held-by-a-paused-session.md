---
title: A paused mic session keeps the microphone, so calibration refuses
kind: todo
status: planned
priority: medium
area: audio
updated: 2026-08-16
---

# A paused mic session keeps the microphone

## The problem

`MicPitchAnswerSource` allows one listener at a time, guarded by an `AtomicBoolean`. Both the
listening flow and `calibrateLatency` clear it in a `finally`, so it cannot leak on an exception or
a cancellation.

It leaks a different way: **the collection is never cancelled when a session pauses.**
`PracticeViewModel.play` starts `collection = viewModelScope.launch { source.notes().collect … }`,
and `collection?.cancel()` appears only in `load`, `finish` and `onCleared`. `pause()` pauses the
Conductor, stops the metronome and saves — and leaves the collector running, so a paused PLAY IT
session is still holding an open `AudioRecord`.

The visible consequence: after pausing a mic session and going to Settings, *Measure it* refuses
with "the mic is in use; calibrate before a session starts", which is true but reads as a bug —
nothing on screen is using the microphone.

Found by the adversarial sweep of every calibration refusal path on 2026-08-16, alongside the two
defects that were fixed there. It is separated out because it is a change to the **session
lifecycle**, not to calibration, and a paused session that stops listening is a behaviour decision
rather than a bug fix: it is almost certainly right, but it belongs in its own change with its own
tests rather than bundled into an audio fix.

## What to do

- Cancel the collection in `pause()` and restart it in `play()`, so a paused session holds no
  hardware. Check the resume path re-establishes the flow before the transport starts moving.
- A test that a paused session releases the capture, and one that calibration succeeds after a
  session has been paused.
- Consider whether the "the mic is in use" refusal should name *what* is using it.

## Done when

- Pausing a PLAY IT session releases the microphone.
- Calibration works immediately after pausing a session, with a test that fails without the change.
