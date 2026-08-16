---
title: Reading the corpus costs seconds on first use
kind: todo
status: planned
priority: medium
area: score
updated: 2026-08-15
---

# Reading the corpus costs seconds on first use

## The problem

The app parses all twenty-eight shipped pieces before it can offer any of them, and MusicXML
DOM parsing of real songs is not cheap. Measured on the api35 emulator by the GC log:

| | span of back-to-back GC | collections |
|---|---|---|
| sequential parse | **24s** | 65 |
| parallel parse (`async` per piece) | **12s** | 27 |

Each collection freed 30–50MB, so this is allocation-bound rather than CPU-bound — the DOM,
not the windowing. On the JVM the same work is ~470ms warm, and the emulator is a
software-rendered, memory-capped VM, so a real phone will sit somewhere between. **It has not
been measured on Dewi's phone**, and that is the first thing to do before choosing a fix.

The user-visible symptom is softened but not removed: rows now stream into the Repertoire tab
as each piece lands, so the screen is never blank. The scheduler's first choice still waits
for the whole corpus.

## What was already done

- One owner (`AppRepertoire`) rather than the tab and the scheduler each doing the work.
- Parallel parse.
- `DifficultySpec.covers` — a lazy sequence that stops at the first refusal, instead of
  building the full reason list only to discard it. Windowing 543ms → 253ms cold, 59ms warm.
- `Passages` sorts a score's events once and binary-searches each window, instead of
  filtering the whole event list per window.

## Options, if the phone measurement says it matters

1. **Cache the derived facts in the manifest** — rung, bars, notes, tempo, polyphony — and
   parse a piece only when it is opened. The DRY objection (a stored copy of a derivation can
   drift) is answerable: a JVM test re-derives every value from the shipped file and asserts
   equality, so the cache cannot drift silently. This is the strongest option and the most work.
2. **Parse on demand only.** The Repertoire tab can show manifest metadata with no parse at
   all; the scheduler needs candidates, which is what makes (1) attractive.
3. **Persist parsed summaries in Room** after the first run. First run stays slow.

## Done when

- The cost is measured on Dewi's actual phone, not an emulator.
- Either it is small enough there to leave alone — recorded, with the number — or one of the
  options above is taken and the number falls.
