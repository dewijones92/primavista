---
title: Measure and compensate the mic path's round-trip latency
kind: todo
status: planned
priority: high
area: audio
updated: 2026-08-07
---

# Measure and compensate the mic path's round-trip latency

## The problem

`PerformanceJudge` reports timing errors in milliseconds (`dt=+38ms`). On the tap path that
number is trustworthy: `MotionEvent.eventTime` is stamped by the input system close to the
touch. On the **mic path it is not**, and currently nothing in the app knows by how much.

Between Dewi's string sounding and a `PlayedNote` existing there is: acoustic travel, the
mic's own buffering, the audio HAL's input latency, our capture buffer size, and the
analysis window YIN needs before it can commit to a pitch. On a typical Android device
that total is tens of milliseconds and **device-specific**. YIN's window alone imposes a
floor — you cannot know a pitch before you have seen enough cycles of it, which at low
pitches is inherently longer.

The consequence is a **systematic bias, not noise**: every mic note reads late by roughly
the same amount. Averaged over a session that looks exactly like "Dewi plays behind the
beat", which is a plausible enough diagnosis that he would believe it and try to fix a
problem he does not have. That is the app teaching the wrong thing, which spec I2 exists to
prevent.

## What to do

1. **Measure it**, don't estimate. Use `AudioTrack`/`AudioRecord`'s own reported latency
   where available, but verify with a real loopback: play a click, record it, correlate,
   and take the offset. Do this on Dewi's actual phone, not the emulator — the emulator's
   audio path has nothing to do with the device's.
2. **Separate the two components.** HAL/buffer latency is a constant offset and should be
   compensated. YIN's detection delay is a function of the window and the pitch, so it is
   computable per note rather than measured. Compensating the first and ignoring the second
   would leave a pitch-dependent bias, which is worse than a constant one because it is
   invisible in an average.
3. **Compensate at the boundary**, where the `AudioRecord` frame position converts into the
   Conductor's timebase — the one place, per the timing rules in `CLAUDE.md`. Nothing
   downstream should know latency exists.
4. **Put the number in the diagnostics report** (`lat=61ms src=measured|assumed`), and log
   which of the two it is. An assumed latency that is silently wrong is the failure mode
   this whole item is about, so it must never look like a measured one.
5. **Show the uncertainty in the UI** while it is unmeasured. If the app cannot state the
   timing accuracy of a mic verdict, it should not present that verdict with a millisecond
   number next to it.

## Done when

- The measured offset for the current device is stored, and re-measured when the audio route
  changes (headphones, Bluetooth — Bluetooth adds well over 100ms and must not be silently
  treated as the built-in mic).
- An instrumented loopback test on a real device asserts the correction brings a known-timed
  click within a stated tolerance.
- Every mic verdict in a report carries the latency figure and its provenance.
