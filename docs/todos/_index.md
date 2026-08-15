---
title: Backlog
kind: index
updated: 2026-08-15
---

# Backlog

One file per item. An item is either in progress, planned, or dropped-with-a-reason —
never silently abandoned.

## Open

| Item | Priority | Area | Why it matters |
|---|---|---|---|
| [measure-audio-latency](measure-audio-latency.md) | high | audio | Until the mic path's real round-trip latency is measured, every mic verdict carries a timing bias of unknown size — the most likely way the app lies to Dewi |
| [expand-repertoire](expand-repertoire.md) | high | score | "Read real pieces" is the whole point, and a handful of hand-authored excerpts is not a repertoire |
| [midi-input](midi-input.md) | medium | input | The third `AnswerSource` adapter: exact, zero-ambiguity, and the only honest route to judging polyphony |
| [diagnostics-report](diagnostics-report.md) | high | ui | Spec I7 is currently proven by nothing; a report has to be able to reconstruct a session's verdicts |


## Done

| Item | Area | Outcome |
|---|---|---|
| [retime-the-judge-after-a-pause](retime-the-judge-after-a-pause.md) | practice | A pause no longer makes the notes after it read late, and a note that was overdue-but-not-yet-missed when the phone rang is not marked Missed. Held by three tests, including one on an *imperfect* performance — the earlier all-correct fixtures agreed trivially |
