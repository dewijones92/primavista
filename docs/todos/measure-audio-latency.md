---
title: Measure and compensate the mic path's round-trip latency
kind: todo
status: in-progress
priority: high
area: audio
updated: 2026-08-16
---

# Measure and compensate the mic path's round-trip latency

## The problem

`PerformanceJudge` reports timing errors in milliseconds (`dt=+38ms`). On the tap path that
number is trustworthy: `MotionEvent.eventTime` is stamped by the input system close to the
touch. On the **mic path it is not**.

Between Dewi's string sounding and a `PlayedNote` existing there is: acoustic travel, the
mic's own buffering, the audio HAL's input latency, our capture buffer size, and the
analysis window YIN needs before it can commit to a pitch. On a typical Android device
that total is tens of milliseconds and **device-specific**.

The consequence is a **systematic bias, not noise**: every mic note reads late by roughly
the same amount. Averaged over a session that looks exactly like "Dewi plays behind the
beat", which is a plausible enough diagnosis that he would believe it and try to fix a
problem he does not have. That is the app teaching the wrong thing, which spec I2 exists to
prevent.

## Where it stands (2026-08-16)

The mechanism is built, wired and tested. What is left is **one number**, and only Dewi's
phone can produce it.

Until 2026-08-16 the situation was worse than "unmeasured", and worth recording because of
the shape of it: every part of calibration existed and none of it was connected. The
`LoopbackCalibrator` played a click and timed it, `MicTimestampCorrection` applied a figure
at the one boundary, `:core:database` had a per-route table with a DAO, a store, converters
and instrumented tests, and the Settings screen rendered the stored figures with provenance
badges. Nothing ever called any of it. `calibrateLatency()` had no production caller,
nothing constructed an `AudioRoute`, and the table Settings displayed could only ever be
empty — so every mic verdict used a hard-coded 60ms assumption for ever.

**A display with no producer** is the tell to remember. Each piece was correct and each
piece was covered; what was missing was the edge between them, which is invisible from every
angle a test looks from.

## What is done

1. **The path identifies itself.** `AudioRecord.getRoutedDevice()` at capture-open, not
   `AudioManager`'s list of what *could* be used — the figure has to match the path the
   session really ran on. The stored key is kind + product name, never `AudioDeviceInfo.id`,
   which the platform reassigns on every reconnect.
2. **`LatencyPolicy` is the one place the figure is chosen**, so a Bluetooth headset cannot
   inherit the built-in mic's number. An unmeasured radio path assumes far more than an
   unmeasured wired one; both stay flagged `Assumed`, because a kind-specific number is
   meant to be *less wrong*, not right.
3. **The route can move mid-session** — Android reroutes a live capture when a headset
   connects — and the figure moves with it, logged as `input rerouted A -> B mid-session`.
4. **Compensation happens at the boundary**, where `AudioRecord`'s frame position converts
   into the Conductor's timebase. Nothing downstream knows latency exists.
5. **The analysis delay is reported and deliberately not applied.** The tracker's onset frame
   already is the onset, so subtracting how long YIN took to confirm the pitch would bias every
   note *early* by the analysis window. It is carried in the diagnostics line as
   `detect=…ms(reported)` so a report can still see it.
6. **Settings can run a measurement**, which is the button that was missing. The wording comes
   from one pure function so a live "Measure it" cannot sit beside "the microphone is off", a
   refusal reaches Dewi word for word, and a measured figure never appears without its
   confidence.
7. **Provenance is in the report.** `lat=…ms src=Measured|Assumed` on every mic session, with
   the route and the sentence explaining which figure was chosen and why.

## What is left

- **Run it on Dewi's phone.** Settings → Audio timing → Measure it, in a quiet room. The
  figure and its confidence are logged under `dewidebug.loopback` and stored per route.
- **Re-measure per route** as he uses them — a wired headset and a Bluetooth one are separate
  rows and each needs its own run.

Automatic re-measurement on a route change was considered and **deliberately not built**: it
would play a loud click unprompted the moment a headset connected, possibly mid-session. The
invariant that matters — never applying another path's figure — is held without it, and a new
path shows as unmeasured in Settings, which is the honest prompt.

## Done when

- ~~The measured offset for the current device is stored, and the wrong route's figure is never
  applied.~~ Done.
- ~~An instrumented loopback test on a real device.~~ Done, and split honestly:
  `LoopbackCalibratorInstrumentedTest` asserts on any device that the path identifies itself,
  that the outcome is either a plausible figure or a refusal **with a reason**, and that the
  correction moves a known onset by exactly the figure and nothing else. On the emulator it
  takes the refusal branch — the emulator's microphone cannot hear its own speaker
  (`peak 6.1E-5 never reached the audible floor 0.02`), which is precisely why the acoustic
  half cannot be asserted anywhere but a real phone.
- ~~Every mic verdict in a report carries the latency figure and its provenance.~~ Done.
- **A measured figure exists for Dewi's phone's built-in mic.** Outstanding.
