---
title: Testing strategy and coverage map
kind: index
updated: 2026-08-07
---

# Tests

The pyramid, and — just as importantly — **what is deliberately not covered**, so a gap is
a recorded decision rather than an oversight.

## The gate

```bash
./gradlew detekt lint test koverVerify assembleDebugAndroidTest   # matches CI
./gradlew connectedDebugAndroidTest                              # needs a device
```

Kover holds pure-JVM logic modules at 75%. `:core:audio` and `:core:database` are exempt
(hardware/Room bridges the JVM gate cannot see) and are covered by instrumented tests
instead. `:app` is report-only — Compose UI distorts the number.

## Where each kind of test belongs

| Layer | Where | What it proves | Cost |
|---|---|---|---|
| Unit (JVM) | `:core:score`, `:core:notation`, `:core:practice`, `:lib:pitch`, `:lib:common` | rhythm arithmetic, MusicXML parsing, layout geometry, judging, scheduling, YIN against synthesised tones | milliseconds |
| Golden (JVM) | `:core:notation` | a layout is a list of numbers, so a staff's geometry can be asserted exactly | milliseconds |
| Integration (JVM) | `:core:practice` | a whole session in fake time: Conductor + AnswerSource + judge together | milliseconds |
| Instrumented | `:core:database`, `:core:audio`, `:app` | Room round-trips, real audio I/O, the real UI | seconds, needs a device |

## Deliberately uncovered (and why)

- **Real audio latency on Dewi's phone.** Unmeasured, and no automated test can measure it —
  it needs a loopback measurement on the device. Tracked as a todo; until then mic verdicts
  carry a timing bias of unknown size (see [`../spec.md`](../spec.md), *The weakness*).
- **Polyphonic pitch detection.** Not attempted, by decision. The test that matters is that
  it **refuses** (spec I3), not that it works.
- **MusicXML beyond the parsed subset.** Untested because unsupported. The parser must
  report what it dropped rather than silently approximating.

## Coverage map

| Area | Unit | Golden | Instrumented | Notes |
|---|---|---|---|---|
| — | — | — | — | *nothing yet; scaffolded 2026-08-07* |
