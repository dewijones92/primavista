---
title: Testing strategy and coverage map
kind: index
updated: 2026-08-15
---

# Tests

The pyramid, and — just as importantly — **what is deliberately not covered**, so a gap is a
recorded decision rather than an oversight.

## The gate

```bash
./gradlew detekt lint test koverVerify assembleDebug assembleDebugAndroidTest   # matches CI
./gradlew connectedDebugAndroidTest                                            # needs a device
```

Kover holds pure-JVM logic modules at 75%. `:core:audio` and `:core:database` are exempt
(hardware/Room bridges the JVM gate cannot see) and are covered by instrumented tests instead.
`:app` is report-only — Compose UI distorts the number.

## Where each kind of test belongs

| Layer | Where | What it proves | Cost |
|---|---|---|---|
| Unit (JVM) | `:core:score`, `:core:notation`, `:core:practice`, `:lib:pitch`, `:lib:common` | rhythm arithmetic, MusicXML parsing, layout geometry, judging, scheduling, YIN against synthesised tones | milliseconds |
| Golden (JVM) | `:core:notation`, `:app` | a layout is a list of numbers, so a staff's geometry can be asserted exactly; `:app` also renders the corpus to PNG on the JVM and asserts it is not blank | milliseconds |
| Integration (JVM) | `:core:practice` | a whole session in fake time: Conductor + AnswerSource + judge together, including across a pause | milliseconds |
| Instrumented | `:core:database`, `:core:audio` | Room round-trips, real migrations, real audio I/O | seconds, needs a device |

## Coverage today

| Module | JVM tests | Instrumented | Holds |
|---|---|---|---|
| `:core:score` | 104 | — | the model, the MusicXML subset, the generator's determinism, and `Score.polyphony` (spec I3's predicate) |
| `:core:audio` | 88 | 22 | timing/pitch-mapping/envelope/noise-floor arithmetic on the JVM; the AudioTrack and AudioRecord bridges on a device |
| `:core:notation` | 79 | — | staff geometry for both clefs, stems from font anchors, beams, leger lines, `xOf` agreeing with note placement (spec I1) |
| `:core:practice` | 105 | — | the Conductor in fake time, the judge's fold, the refusal gate, the scheduler, the stage curriculum and the placement read (spec I1, I2, I3, I5) |
| `:lib:pitch` | 60 | — | YIN against synthesised tones, onset separation of repeated notes, vibrato held as one note |
| `:core:database` | — | 74 | session and skill round-trips, the real v1→v2 migration, cascade delete (spec I4) |
| `:lib:common` | 12 | — | the diagnostics buffer: bounded overflow, counted hot events, a throwing snapshot degrading rather than losing the report |
| `:app` | 85 | — | the JVM PNG render of the corpus, the path model, the staff-pitch conversion, and screen logic that does not need a device |
| **Total** | **533** | **96** | |

## Deliberately uncovered (and why)

- **Real audio latency on Dewi's phone.** Unmeasured, and no automated test can measure it — it needs
  a loopback measurement on the device. Tracked in `../todos/measure-audio-latency.md`; until then
  mic verdicts carry a timing bias of unknown size (see [`../spec.md`](../spec.md), *The weakness*).
- **Polyphonic pitch detection.** Not attempted, by decision. The test that matters is that it
  **refuses** (spec I3), not that it works.
- **MusicXML beyond the parsed subset.** Untested because unsupported. The parser reports what it
  dropped rather than silently approximating, and the Repertoire screen shows that count.
- **The Compose UI itself.** There are no instrumented UI tests. The screens are verified by
  building, installing and looking — which is how the last four real bugs were found, all of them
  invisible to the suite. That is a stated limitation, not a claim that looking is equivalent: it
  catches what is on screen and nothing about what happens when nobody is watching.
- **Spec I7.** The buffer's mechanics are covered; that a report can reconstruct a session's verdicts
  is not. See `../todos/diagnostics-report.md`.
