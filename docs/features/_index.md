---
title: Features
kind: index
updated: 2026-08-15
---

# Features

What the app does today, the seam each thing is built on, and what holds it. A row here is a claim
that something works; if you find one that does not, the row is the bug.

| Feature | Status | Seam | Held by |
|---|---|---|---|
| Grand-staff engraving from Bravura's own metrics | shipped | `StaffLayout` | 79 JVM geometry tests; a JVM PNG render of the whole corpus asserted non-blank; seen on a device |
| Scrolling playhead at tempo, with clef/key/time pinned | shipped | `Conductor` + `StaffLayout.xOf` | `xOf` agrees with note placement (spec I1); seen on a device |
| Judging pitch and timing, note by note | shipped | `PerformanceJudge` | 60 JVM tests incl. a perfect-performance round trip and the live-vs-batch equality (spec I2) |
| Honest refusal on material the input cannot hear | shipped | `AnswerSource.polyphony` + `Score.polyphony` | overlap-based, defined once in `:core:score` (spec I3) |
| Tap input on a multi-touch piano keyboard | shipped | `AnswerSource` | timestamps taken from the touch event, not from when the app noticed |
| Mic (PLAY IT) input with YIN pitch + onset detection | shipped | `AnswerSource` | 60 JVM DSP tests against synthesised tones; **latency still unmeasured** — see the todo |
| Metronome, driven by the Conductor and not its own timer | shipped | `Conductor` | one clock, per spec I1 |
| Reference tone playback | shipped | `TonePlayer` | JVM envelope/mixing tests |
| MusicXML subset parsing, loud about what it dropped | shipped | `MusicXmlParser` | hardened DOM; `DropKind` separates a hole in the music from lost decoration, and both are shown |
| Real repertoire: 41 CC0 songs, screened and placed by the app's own grader | shipped | `Corpus` + `Repertoire` | [`repertoire.md`](repertoire.md); 40 JVM tests across part selection, passages, admission, the corpus and what it offers |
| A passage of a piece is a piece | shipped | `Score.passages` | a whole song grades as one difficulty; windowed, the same song places across five rungs. 11 JVM tests |
| "Is this music at this level" | shipped | `DifficultySpec.admits` | the dual of the generator, tied to it by a property test that caught a real defect |
| Procedurally generated graded exercises | shipped | `Score` (same type as a parsed piece) | determinism from seed+spec, so a report can replay one |
| The ladder: the scheduler choosing what to read next | shipped | `PracticeScheduler` | 105 JVM tests incl. "a mono input is never handed material its own judge would refuse" (spec I5) |
| Practice history persisted across restart and update | shipped | Room stores | 74 instrumented tests incl. a real v1→v2 migration (spec I4) |
| Per-skill results rather than one percentage | shipped | `SkillTag` on notes | skills derived once in `:core:score` |
| Progress, repertoire and settings screens | shipped | — | verified by looking; no instrumented UI tests (a stated gap) |
| Diagnostics report, shareable with no network | shipped | `Diag` + `SessionReplay` | 12 JVM tests on the buffer; 10 more that re-judge a session **from its own report** and reach the same verdicts (spec I7) |
| Signed APK per push, installable via Obtainium | shipped | GitHub Actions | `v0.1.<run>` releases; `/releases/latest/download/primavista.apk` verified 200 |
| Trill, the mascot | shipped | `MascotMood` | drawn in code from one boolean-union silhouette, seven moods, scales 24dp→200dp; her mood is derived from the same tone as the score so she cannot contradict it |
| Reading ahead: a card over the music at the playhead | shipped | `ReadingLead` | [`reading-ahead.md`](reading-ahead.md); 8 JVM tests plus a v3→v4 migration proving it arrives off |
| Opening a MusicXML file from the phone | shipped | `PracticeRequest` carries a `Score` | [`open-any-score.md`](open-any-score.md); 9 JVM tests over the shipped corpus's own bytes; no `INTERNET` permission involved |
| A first run that teaches | shipped | — | Trill introduces the one idea (height on the stave is pitch) interactively, then asks for one note and names what you did. Skippable at every step |
| Placement read | shipped | `PlacementRead` | adaptive probes climbing stages 1→2→4→7→10, stopping at the first that does not go well; seeds skills from the judge's own verdicts, so it measures rather than asks |
| The path — ten stages | shipped | `Curriculum` | progress is "n of m skills solid" and can go down; `Curriculum.isPassed` is the only answer to whether a rung is behind you |
| Streak | shipped | `Streak` | days read, derived from session timestamps; never a threat, never something lost, hidden entirely at zero |
| Stage-focused sessions | shipped | `PracticeFocus` | a stage narrows the field; the scheduler still picks and the generator still writes |

## Not built, deliberately

- **MIDI input** — the third `AnswerSource` adapter and the only honest route to judging polyphony.
  Speculative until Dewi says he has a keyboard; see `../todos/midi-input.md`.
- **Passing stage 4 on the microphone.** Not a gap to be closed: a phone mic genuinely cannot judge
  two hands, so the app says "this rung needs the tapped keyboard" and offers to switch, rather than
  guessing. Same principle as the polyphony refusal (spec I3).
- **Score editing, composing, sharing, accounts, sync.** One user, one phone. An unbuilt feature
  nobody asked for is not a gap (see [`../spec.md`](../spec.md)).

Keep a row's status equal to reality. A disagreement between this index and the code is a bug in
both, and a stale `shipped` is worse than no row at all.
