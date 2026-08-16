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
| `:core:score` | 165 | — | the model, the MusicXML subset, the generator's determinism, `Score.polyphony` (spec I3's predicate), part selection, passages, and whether a spec admits a piece |
| `:core:audio` | 88 | 22 | timing/pitch-mapping/envelope/noise-floor arithmetic on the JVM; the AudioTrack and AudioRecord bridges on a device |
| `:core:notation` | 79 | — | staff geometry for both clefs, stems from font anchors, beams, leger lines, `xOf` agreeing with note placement (spec I1) |
| `:core:practice` | 139 | — | the Conductor in fake time, the judge's fold, the refusal gate, the scheduler, the stage curriculum, the placement read (spec I1, I2, I3, I5), what a piece offers to read, how far ahead the page is covered, and re-judging a session from its own report (spec I7) |
| `:lib:pitch` | 60 | — | YIN against synthesised tones, onset separation of repeated notes, vibrato held as one note |
| `:core:database` | 74 | 77 | codecs, row mapping and refusal-on-unreadable on the JVM; session and skill round-trips, the real v1→v2 and v3→v4 migrations and cascade delete on a device (spec I4) |
| `:lib:common` | 12 | — | the diagnostics buffer: bounded overflow, counted hot events, a throwing snapshot degrading rather than losing the report |
| `:app` | 105 | — | the JVM PNG render of the corpus, the path model, the staff-pitch conversion, reading a file Dewi picked, and screen logic that does not need a device |
| **Total** | **722** | **99** | |

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
- **The layout is not replayed.** `SessionReplay` re-judges a session exactly (spec I7), but it
  rebuilds the music and the clock, not the engraving — so a claim about I1's scroll geometry is
  still read out of a report rather than recomputed from it.
- **How long the corpus takes to read on a real phone.** Measured on the emulator through the GC log
  (see `../todos/repertoire-load-cost.md`); no test can measure the phone, and the emulator is a poor
  proxy for it.

## What an adversarial review found that 684 green tests did not

Six lenses over one day's work, each finding attacked by a skeptic, produced **five confirmed
defects** — and their shapes say where a test suite goes thin:

- **A boundary the fixtures never had.** Real engraving has short bars (a pickup, a bar split across
  a system); hand-written fixtures do not. Passage windows trusted the time signature and pulled the
  next bar's notes in — 126 leaked events across 17 of the 44 shipped pieces.
- **A cancellation nothing drove.** Leaving the Repertoire tab mid-parse left the arrivals behind,
  so the next visit appended all 44 again. Duplicate keys in a `LazyColumn` are a crash.
- **A build variant no test runs in.** R8 renamed every `Verdict`, so the replay block spec I7 rests
  on read `claimed=0:n22:1.5` on the phone while reading `WrongPitch` in debug.
- **A state nothing left.** The reading-ahead cover was cleared on load and on the dial, but not on
  *finish*, so the review page — the one that exists to show verdict-coloured noteheads — was blank.
- **A path a refactor split.** Merging two racing `LaunchedEffect`s meant a piece opened from the
  Repertoire tab never resolved the stored input: a saved PLAY IT session opened silently on TAP.

Every one is "the code is right in the case someone thought to write down". All five now have a
test that was **made to fail first**, and the R8 one has a CI gate that was itself made to fail
before being trusted — the obvious version of that gate, grepping the release DEX for `WrongPitch`,
passed either way, because a Kotlin data class bakes its own name into `toString()`.

## Two properties worth knowing about

Both live in `:core:score` and both earn their keep by tying two things together that could
otherwise drift apart:

- **Every exercise a spec generates is admitted by that spec.** The generator writes inside a
  spec's dials and `DifficultySpec.admits` checks music against them, so a disagreement is the
  `specTargeting` family of defect CLAUDE.md records. It found one immediately: the accidental check
  was reading the note's alteration rather than asking the key, so every exercise in G major was
  refused over an F sharp that is the signature doing its job.
- **A drill aimed at anything the shipped repertoire can teach still fills its bars.** Real music
  asks for more than a generator should write — a two-octave leap, eight leger lines, a septuplet —
  and the scheduler will hand `specTargeting` whatever the corpus made weak. A separate test
  characterises exactly how the corpus exceeds the generator, so a *new* kind of excess fails rather
  than passing unnoticed.
