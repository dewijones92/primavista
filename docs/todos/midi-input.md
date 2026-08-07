---
title: MIDI input as a third AnswerSource
kind: todo
status: planned
priority: medium
area: input
updated: 2026-08-07
---

# MIDI input as a third AnswerSource

## Why it is worth doing

It is the only honest route to judging **polyphony**. Mic mode is restricted to monophonic
lines by decision (spec I3), which means the grand staff — the notation Dewi chose — can only
ever be *played* one hand at a time into a microphone. A MIDI keyboard removes the ambiguity
entirely: exact pitches, exact onsets, several at once, latency in single-digit milliseconds.

It is also the cheapest feature in this backlog, and that is the point of the seam. `AnswerSource`
already emits `PlayedNote`; a MIDI adapter declares `Polyphony.Poly` and everything downstream
— judge, scheduler, UI, diagnostics — works unchanged. If implementing this turns out to need
changes outside the adapter, the seam was wrong and that is the finding.

## What to do

- Android's `MidiManager` for USB-OTG and BLE MIDI. Both are the same API surface; BLE adds
  pairing and a worse latency figure that must be measured, not assumed
  (see [measure-audio-latency](measure-audio-latency.md) — same rule, different transport).
- Timestamps come from the MIDI framework's own nanosecond stamps, converted into the
  Conductor's timebase at the boundary. Do not restamp on arrival.
- Handle NoteOn/NoteOff pairs into held durations, so a judgement can eventually be about
  note *length* as well as onset — which the mic path cannot support reliably and the tap path
  can only approximate.
- Sustain pedal (CC64) changes what "released" means. Ignore it initially, but log that it is
  being ignored rather than letting it silently distort durations.

## Open question for Dewi

Does he have, or intend to get, a MIDI keyboard? He picked tap + mic when MIDI was offered as
its own option, so this is speculative until he says otherwise. It stays medium priority and
unbuilt until then — an unbuilt feature nobody asked for is not a gap (see
[`../spec.md`](../spec.md)).
