---
title: A diagnostics report that can reconstruct a session
kind: todo
status: planned
priority: high
area: ui
updated: 2026-08-07
---

# A diagnostics report that can reconstruct a session

## The problem

Spec I7 says every invariant must be checkable after the fact from a report alone, and it is
currently proven by nothing. This is the item that makes the rest of the spec verifiable
rather than aspirational.

The bar is not "there are logs". It is: **a report Dewi shares a week later must let a fresh
session re-judge the performance and reach the same verdicts**, or explain why it cannot.
Totum's report 0.1.346 is the cautionary example — it carried a 97-item queue and every
setting, and still could not answer "was the item downloaded?", so the diagnosis had to come
from reading code instead.

## What the report has to carry

- **The build**: git SHA, version, device, Android version. A finding is only as current as
  the build it came from.
- **The score identity**: which piece or, for a generated exercise, the **seed and difficulty
  spec** — because a generated `Score` is reproducible from those two things exactly. This is
  why the generator is seeded, and it is the single highest-value line in the report: it turns
  "an exercise went wrong" into a replayable case.
- **The session's inputs**: adapter (`tap`/`mic`), its declared polyphony, tempo, count-in,
  the measured or assumed audio latency and which of the two it was.
- **Per-note verdicts with their inputs** — expected pitch, heard pitch, `dt` in ms, and
  confidence. Not a summary percentage: a total cannot say whether the note that *felt* wrong
  was judged wrong.
- **Counted, not per-event, for anything hot.** Position ticks and pitch frames are periodic
  aggregates with a count. A bounded buffer plus a per-event line destroys the history it is
  meant to preserve — Totum lost sixteen minutes of it that way.
- **Refusals, loudly.** `PLAY IT refused: score is polyphonic at bar 3` is exactly the line
  that stops a future session guessing why nothing happened.

## Done when

- A test takes a report from a completed session, feeds the seed and the recorded played notes
  back through `PerformanceJudge`, and asserts it reproduces the verdicts the report claims.
  That test is what makes spec I7 real; without it the report is decoration.
- Sharing works with no `INTERNET` permission (share sheet only — spec I6).
