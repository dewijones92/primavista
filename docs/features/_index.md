---
title: Features
kind: index
updated: 2026-08-08
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
| MusicXML subset parsing, loud about what it dropped | shipped | `MusicXmlParser` | hardened DOM; the dropped count is shown on the Repertoire screen |
| Procedurally generated graded exercises | shipped | `Score` (same type as a parsed piece) | determinism from seed+spec, so a report can replay one |
| The ladder: the scheduler choosing what to read next | shipped | `PracticeScheduler` | 60 JVM tests incl. "a mono input is never handed material its own judge would refuse" (spec I5) |
| Practice history persisted across restart and update | shipped | Room stores | 39 instrumented tests incl. a real v1→v2 migration (spec I4) |
| Per-skill results rather than one percentage | shipped | `SkillTag` on notes | skills derived once in `:core:score` |
| Progress, repertoire and settings screens | shipped | — | verified by looking; no instrumented UI tests (a stated gap) |
| Diagnostics report, shareable with no network | shipped | `Diag` | 12 JVM tests on the buffer; **that a report can reconstruct a session is not yet proven** (spec I7) |
| Signed APK per push, installable via Obtainium | shipped | GitHub Actions | `v0.1.<run>` releases; `/releases/latest/download/primavista.apk` verified 200 |

## Not built, deliberately

- **MIDI input** — the third `AnswerSource` adapter and the only honest route to judging polyphony.
  Speculative until Dewi says he has a keyboard; see `../todos/midi-input.md`.
- **Score editing, composing, sharing, accounts, sync.** One user, one phone. An unbuilt feature
  nobody asked for is not a gap (see [`../spec.md`](../spec.md)).

Keep a row's status equal to reality. A disagreement between this index and the code is a bug in
both, and a stale `shipped` is worse than no row at all.
