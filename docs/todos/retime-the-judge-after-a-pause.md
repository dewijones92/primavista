---
title: Re-time the judge after a pause
kind: todo
status: done
priority: high
area: practice
updated: 2026-08-15
---

# Re-time the judge after a pause

**Done 2026-08-08**, both halves. `PerformanceJudge.retime` landed in `:core:practice` and
`PracticeViewModel.resume()` calls it (`PracticeViewModel.kt:235`), logging `judge retimed` with the
position.

An adversarial review then found the fix incomplete in one direction; that gap was closed on
2026-08-15. The whole item is done — see *The gap the first fix left* below for what was missing and
what now holds it.

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

## What was done

```kotlin
public fun retime(state: JudgeState, timing: TickTiming): JudgeState
```

`WindowedJudge` implements it as a one-field copy of the fold. Getting there needed the timing to
live in exactly one place: `Windows` had captured its own reference to the map, so swapping
`Fold.timing` alone would have left every window width measured against the stale map. `Windows` now
depends only on the tolerances and is handed the map per call.

Replay is unaffected — a stored session replays from its final snapshot, which already contains every
leg.

## Proven by

- `TempoConductorTest` — *"a perfect performance played live across two pauses is all correct"*, which
  drives the live fold (`advance`/`advanceTime`) against a real `TempoConductor`, not `judgeAll`. It
  fails without the fix (notes after the resume come back as `Extra` + `Missed`, 2 correct of 4), and
  fails again if only the first resume re-times, so it holds *every* resume rather than the first.
- `WindowedJudgeTest` — *"re-timing swaps the map without disturbing what is already settled"*: a
  verdict settled before the pause survives untouched while the next note is measured against the
  newer map.
- `TempoConductorTest` — *"a perfect performance across a pause re-judges identically from its own
  snapshot"* still passes, so liveness was not traded for reproducibility.

## The gap the first fix left — closed 2026-08-15

`retime` re-times only the notes whose onset is strictly **after** the pause position. So a note that
was already overdue but **still inside its matching window** when the phone rang keeps its pre-pause
wall time: on resume it is `Missed` on the very first tick, and the note Dewi then plays becomes an
`Extra`. Reproduced at 3 correct of 4 — one `Missed` plus one `Extra` for a note played 20ms after
picking the piece back up.

That is verbatim the failure this whole item exists to remove, and it is worse than the original in
one respect: it debits the skill for a note he got **right**. It was left rather than patched because it needed a decision about crediting pause duration to
expectations that are unsettled at the moment of the pause. The principle decided it: the app must
not blame Dewi for its own bookkeeping, and **a note he had not yet missed when he paused has not
been missed**. It is now held by `TempoConductorTest` — *"a note overdue but still inside its window
when the phone rings is not missed"* — alongside *"a perfect performance played live across two
pauses is all correct"* and *"an imperfect performance across a pause is judged the same live and
from its snapshot"*, which is the spec I2 property that the earlier all-correct fixtures satisfied
trivially.

`.claude/CODE-NOTES.md` had stated the opposite assumption in writing — "a note not yet played is
about to be judged live anyway" — which is precisely the false premise that shipped the gap. It was
corrected alongside the fix, and is worth remembering as a case where a note explaining a decision
was itself the thing that made the decision look sound.
