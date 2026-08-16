---
title: Backlog
kind: index
updated: 2026-08-16
---

# Backlog

One file per item. An item is either in progress, planned, or dropped-with-a-reason —
never silently abandoned.

## Open

| Item | Priority | Area | Why it matters |
|---|---|---|---|
| [measure-audio-latency](measure-audio-latency.md) | high | audio | Three defects that made it refuse on every device are fixed (2026-08-16); still needs one successful run on Dewi's phone, at high media volume |
| [mic-held-by-a-paused-session](mic-held-by-a-paused-session.md) | medium | audio | A paused PLAY IT session keeps the microphone open, so calibration refuses with "the mic is in use" when nothing visibly is |
| [midi-input](midi-input.md) | medium | input | The third `AnswerSource` adapter: exact, zero-ambiguity, and the only honest route to judging polyphony |
| [repertoire-load-cost](repertoire-load-cost.md) | medium | score | Reading the corpus holds the emulator in GC for 12 seconds; unmeasured on the real phone |
| [remove-superseded-repertoire-store](remove-superseded-repertoire-store.md) | low | database | A whole unwired Room store that two other things now do better; removing it needs a migration of its own |


## Done

| Item | Area | Outcome |
|---|---|---|
| [generate-in-more-than-one-key](generate-in-more-than-one-key.md) | score | A level writes in a set of keys now, so stage six covers G, F, D and B flat and may claim all four |
| [open-any-score](open-any-score.md) | ui | The Repertoire tab opens a MusicXML file from the phone, by exactly the road a shipped piece takes — and since 2026-08-16 keeps it, so it is an ordinary row on the shelf tomorrow |
| [diagnostics-report](diagnostics-report.md) | ui | Spec I7 is a property now: a report carries the seed, the clock and every note heard, and a test re-judges from it and reaches the same verdicts |
| [expand-repertoire](expand-repertoire.md) | score | Twenty-five CC0 songs ship, screened and placed by the app's own parser, grader and curriculum. Two departures from the plan, both forced by measurement — see the file |
| [retime-the-judge-after-a-pause](retime-the-judge-after-a-pause.md) | practice | A pause no longer makes the notes after it read late, and a note that was overdue-but-not-yet-missed when the phone rang is not marked Missed. Held by three tests, including one on an *imperfect* performance — the earlier all-correct fixtures agreed trivially |
