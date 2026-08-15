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
| [midi-input](midi-input.md) | medium | input | The third `AnswerSource` adapter: exact, zero-ambiguity, and the only honest route to judging polyphony |
| [diagnostics-report](diagnostics-report.md) | high | ui | Spec I7 is currently proven by nothing; a report has to be able to reconstruct a session's verdicts |
| [repertoire-load-cost](repertoire-load-cost.md) | medium | score | Reading the corpus holds the emulator in GC for 12 seconds; unmeasured on the real phone |
| [generate-in-more-than-one-key](generate-in-more-than-one-key.md) | medium | score | A stage called *Keys* writes every exercise in G major; it can now read four accidentals but never writes one |
| [open-any-score](open-any-score.md) | medium | ui | The pipeline already parses, grades and windows any MusicXML — all that is missing is the file picker |


## Done

| Item | Area | Outcome |
|---|---|---|
| [expand-repertoire](expand-repertoire.md) | score | Twenty-five CC0 songs ship, screened and placed by the app's own parser, grader and curriculum. Two departures from the plan, both forced by measurement — see the file |
| [retime-the-judge-after-a-pause](retime-the-judge-after-a-pause.md) | practice | A pause no longer makes the notes after it read late, and a note that was overdue-but-not-yet-missed when the phone rang is not marked Missed. Held by three tests, including one on an *imperfect* performance — the earlier all-correct fixtures agreed trivially |
