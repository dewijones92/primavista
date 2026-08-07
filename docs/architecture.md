---
title: Architecture — the unified seams and module map
kind: reference
status: living
updated: 2026-08-07
---

# Architecture

`CLAUDE.md` holds the decisions; this holds the shape. The organising idea is the twin
laws: **one seam per capability**, and knowledge in exactly one place.

## The five seams

Every feature in this app is built on one of these. If a feature needs a sixth, that is a
design conversation, not a file.

| Seam | Type | Lives in | Answers |
|---|---|---|---|
| `Score` | data | `:core:score` | "what am I supposed to play?" |
| `AnswerSource` | port | `:core:practice` | "what did I actually play?" |
| `Conductor` | class over a `NanoClock` port | `:core:practice` | "where in the music are we, right now?" |
| `PerformanceJudge` | pure function | `:core:practice` | "was that right?" |
| `StaffLayout` | pure function | `:core:notation` | "where does every glyph go?" |

The reason each is exactly one thing:

- **`Score`** — a **generated exercise and a parsed Bach minuet are the same type**. The
  scrolling loop, the judge and the layout engine cannot tell them apart, so a feature built
  on one works on the other for free. This is the repo's most important unification.
- **`AnswerSource`** — tap, mic and (later) MIDI all emit `PlayedNote`. Everything
  downstream of it is input-agnostic, so adding MIDI is a new adapter and zero changes
  elsewhere. An adapter declares its `Polyphony`, which is how the judge can refuse
  honestly instead of mis-scoring (see spec I3).
- **`Conductor`** — the scroll offset, the judging window and the metronome click all read
  time from here. Three independent derivations of "now" is the bug class this prevents
  (spec I1).
- **`PerformanceJudge`** — pure, so every verdict is reproducible from its inputs, which is
  what makes a diagnostics report enough to re-judge a session after the fact.
- **`StaffLayout`** — treble, bass and the grand staff are one engine with different clef
  mappings. Two renderers would be the classic pillar-split failure.

## Module map

Dependencies point inward; the pure-JVM modules cannot reach Android because they do not
have the dependency.

```
                 ┌──────────────────────────────┐
                 │            :app              │  Compose UI, theme, AppContainer
                 └──────────────────────────────┘
                    │        │        │       │
       ┌────────────┘        │        │       └────────────┐
       ▼                     ▼        ▼                    ▼
┌─────────────┐   ┌──────────────┐  ┌───────────────┐  ┌──────────────┐
│ :core:audio │   │:core:database│  │:core:notation │  │:core:practice│
│  (Android)  │   │  (Android)   │  │  (pure JVM)   │  │  (pure JVM)  │
└─────────────┘   └──────────────┘  └───────────────┘  └──────────────┘
       │   │              │                 │                 │
       │   └──────────────┴─────────────────┼─────────────────┘
       │                                    ▼
       │                          ┌──────────────────┐
       │                          │   :core:score    │  (pure JVM)
       │                          └──────────────────┘
       ▼                                    │
┌──────────────┐                            ▼
│  :lib:pitch  │  (pure JVM)      ┌──────────────────┐
└──────────────┘ ────────────────▶│   :lib:common    │  (pure Kotlin)
                                  └──────────────────┘
```

| Module | Kind | Holds | Coverage gate |
|---|---|---|---|
| `:core:score` | pure JVM | `Pitch`, `MusicalTime`, `Note`, `Score`, `Clef`, `KeySignature`, `TimeSignature`; `MusicXmlParser`; `ExerciseGenerator` | Kover 75% |
| `:core:notation` | pure JVM | `StaffLayout`, glyph placement, `GlyphMetricsSource` port over Bravura's metadata | Kover 75% |
| `:core:practice` | pure JVM | `Conductor`, `AnswerSource` + `TapAnswerSource`, `PerformanceJudge`, `SkillTag`, scheduler | Kover 75% |
| `:lib:pitch` | pure JVM | YIN monophonic detection; speaks hertz, not notation | Kover 75% |
| `:lib:common` | pure Kotlin | shared value types, `Diag` ring buffer + report builder | Kover 75% |
| `:core:audio` | Android lib | metronome, tone playback, PCM capture, `MicAnswerSource`, monotonic clock | exempt — hardware bridge, instrumented |
| `:core:database` | Android lib | Room entities, DAOs, stores | exempt — instrumented |
| `:app` | Android app | Compose screens, theme, `AppContainer` | report-only |

## Why the pure/Android split is drawn where it is

The interesting correctness in this app — rhythm arithmetic, judging, layout geometry,
pitch detection, scheduling — has nothing to do with Android. Putting it in pure-JVM
modules means it is tested in milliseconds, in fake time, without a device, and the tests
are cheap enough that there is no excuse for a behaviour landing without one.

What genuinely needs Android is narrow: two audio classes, Room, and the UI. Those are the
modules exempt from the coverage gate, and they are exactly the ones that must be verified
on a real device instead — the split is what makes that verification a short list rather
than the whole app.

`:lib:pitch` speaking hertz rather than `Pitch` looks like a missed reuse. It is
deliberate, following Totum's `:lib:ytdlp`: a DSP library that knows nothing about notation
is independently reusable and independently testable against synthesised tones. The
conversion is one function in the adapter.
