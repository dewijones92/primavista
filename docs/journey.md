---
title: The journey — beginner to intermediate, and how it stays honest
kind: reference
status: living
updated: 2026-08-15
---

# The journey

Dewi's brief (2026-08-15): make it *"really attractive, really fun, intuitive, great for beginners,
intermediate sort of thing… like Duolingo, orbiting around a mascot"*.

Duolingo does two things this app currently does not. It gives you a **place** — a visible path you
are somewhere along — and it **meets you where you are**, so a competent speaker is not made to tap
through "the cat is black". Both matter here, because sight-reading spans from *"which line is
that?"* to reading Bach at tempo, and the same app has to serve both ends.

## The problem with what we have

The scheduler is good at *"what is weakest and due?"* and answers it well. But on its own it gives
no sense of place: every session feels like the same session, because there is no visible middle
distance between "this note" and "read real music fluently". That is the difference between a
practice tool and something you want to open.

It also opens on the same starting difficulty for everyone. For a beginner that is right. For an
intermediate reader it is insulting, and it is the fastest way to lose them.

## Stages

A **stage** is a coherent set of reading skills, in a sensible teaching order. It is a *curriculum*,
not a second scheduler — the distinction matters and is the whole of the DRY argument here:

- A **stage** decides *which skills are in play*.
- The **scheduler** decides, from those, *which to drill right now* — unchanged, still the only
  thing that answers that question.
- The **generator** turns the choice into a `Score` — unchanged.

So a stage narrows the field; it never picks. If a stage ever starts choosing material, that is two
schedulers and a design failure.

| # | Stage | What it adds |
|---|---|---|
| 1 | The five lines | Treble clef, middle of the staff, whole and half notes, C major |
| 2 | Stepping out | The whole treble staff, quarter notes |
| 3 | The other clef | Bass clef, same treatment |
| 4 | Both hands | The grand staff — two staves at once |
| 5 | Sharps and flats | Written accidentals |
| 6 | Keys | Key signatures, where the accidental is implied rather than printed. Writes in G, F, D and B flat; reads up to two accidentals |
| 7 | Off the staff | Leger lines, above and below, both clefs |
| 8 | Quicker | Eighths, sixteenths, dots, ties |
| 9 | Real music | The corpus, at tempo — in the keys real music is written in, up to four accidentals |
| 10+ | Onwards | Tuplets, wider leaps, faster tempi, every key, more repertoire |

Each stage carries a `DifficultySpec` template and the `SkillTag`s it covers. **What a rung writes
in and what it can read are different dials** — `key` and `maxKeyAccidentals`. Conflating them
capped the whole path at one sharp, so a reader could finish all ten rungs having never met a B
flat, and 44,335 passages of the shipped corpus were refused on a difficulty stage six claims to
teach. A stage is **passed**
when its skills are solid — not when a counter of sessions ticks over, because sessions completed is
a measure of showing up and this app measures reading.

## Meeting an intermediate reader where they are

A **placement read** at first run: a short, adaptive sequence that starts easy and gets harder until
it stops going well, then seeds the skill store from what it saw. Two minutes, and the app knows
roughly where you are.

Three things make it honest:

- **It is measured, not declared.** Asking "are you a beginner?" gets you an answer about confidence,
  not about reading. We already have a judge that produces per-note verdicts against skill tags — the
  placement read is that machinery pointed at a different question, not a new one.
- **It can be skipped, and skipping starts you at stage 1.** Never a wall between someone and the
  thing they downloaded the app for.
- **It seeds, it does not decide for ever.** Placement sets initial skill states; the ordinary
  scheduler takes over immediately, so an over-generous placement is corrected within a session or
  two rather than leaving someone stranded above their level.

## What keeps it honest

The rest of this repo exists to stop the app flattering Dewi about his playing, and a fun layer is
exactly where that discipline usually gets lost. So:

- **A streak counts days you practised, and nothing else.** It never counts a day you opened the app,
  and it is never used to imply you are failing. Motivation by shame is effective and unpleasant, and
  this is an app one person uses alone after work.
- **A stage is passed when the reading is solid**, never when enough sessions have happened.
- **Trill is never pleased about a bad run.** She can be kind about one. See
  `ui/mascot/MascotContract.kt`, where this is written into the type.
- **Nothing is unlocked by paying attention rather than by reading.** No consolation progress.

## Where it lives

- `Curriculum` and `Stage` in **`:core:practice`** — pure JVM, so the ordering and the pass rule are
  ordinary unit tests rather than something to be eyeballed on a device.
- The **placement read** drives the existing `Conductor`/`AnswerSource`/`PerformanceJudge`, because a
  placement that judged differently from a session would be measuring a different thing.
- The **path UI** and **Trill** in `:app`.
