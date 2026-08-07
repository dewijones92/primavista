---
title: Re-time the judge after a pause
kind: todo
status: planned
priority: high
area: practice
updated: 2026-08-07
---

# Re-time the judge after a pause

## The problem

`PerformanceJudge.begin(score, timing)` takes an immutable `TickTiming`, and the view model takes
that snapshot when the transport **starts**. A pause and resume appends a new leg to
`TempoConductor`'s tempo map, and the already-begun `JudgeState` is holding the map as it stood
before that leg existed.

So every note played after a mid-session resume is measured against the pre-pause mapping, and reads
**late by the length of the pause**. Pause for ten seconds and the next note is judged ten seconds
late — which the app will report as Dewi's timing rather than its own bookkeeping. That is the same
class of fault as the bug this design already fixed once: a session that does not agree with itself.

## Why it is shaped this way

The immutability is not an accident and must not simply be undone. Handing the judge the live
`Conductor` was the original blocker: a session containing a pause then re-judged *differently from a
report of itself*, because the mapping had moved on since the notes were played, and every `Correct`
became an `Extra` plus a `Missed`. Reproducibility from a report is what docs/spec.md I2 rests on.

So the requirement is both things at once: the judge must see a map that includes the pauses that
have **already happened**, and must not see one that keeps changing under a replay.

## What to do

Add to the contract:

```kotlin
public fun retime(state: JudgeState, timing: TickTiming): JudgeState
```

It keeps everything the fold has settled and swaps in the newer map. `PracticeViewModel.resume()`
then calls it with `conductor.timingSnapshot()`. Replay is unaffected — a stored session replays from
its final snapshot, which already contains every leg.

## Done when

- A test plays a perfect performance **across a pause**, driving the live fold (not `judgeAll`), and
  asserts every note is `Correct` — it fails today on the notes after the resume.
- The existing re-judge-from-snapshot test still passes, so the fix has not traded reproducibility
  for liveness.
- `PracticeViewModel.resume()` re-times, and the diagnostics line for a resume records the new
  origin so a report can show which map was in force for which note.
