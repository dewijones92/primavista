---
title: Expand the repertoire beyond the starter corpus
kind: todo
status: done
priority: high
area: score
updated: 2026-08-15
---

# Expand the repertoire beyond the starter corpus

## The problem

Dewi's chosen core skill is reading **real pieces** at tempo. The generator covers the lower
rungs of the ladder with infinite graded material, but a generated exercise is not a piece:
it has no phrasing, no harmonic direction, and no reason to be memorable. Sight-reading
practice on synthetic material teaches note-decoding without teaching the pattern
recognition that actually makes someone fluent — real music is full of idiomatic figures
that recur, and recognising them at a glance *is* the skill.

The starter corpus is a handful of hand-authored public-domain excerpts. That is enough to
prove the pipeline, not enough to practise against.

## Constraints

- **Public domain only.** Composer died over 70 years ago, or the edition is explicitly
  CC0/PD. This is not a licensing worry to be resolved later — it is a hard filter, the same
  rule Totum applies to test media.
- **The engraving must be trustworthy.** A misencoded score teaches wrong notes, so anything
  imported needs to be checked against the printed source rather than trusted.
- **No `INTERNET` permission** (spec I6). Import happens at build time into bundled assets,
  or through the system file picker from a file Dewi already has — never a fetch from the app.

## Candidate sources

- **Mutopia Project** — free, PD/CC, LilyPond-sourced with MusicXML exports available.
- **OpenScore** (Lieder and String Quartet corpora) — CC0, MuseScore-sourced, high quality.
- Bach's *Anna Magdalena* notebook, Clementi and Kuhlau sonatinas, Burgmüller op. 100,
  Czerny — the standard graded sight-reading ladder, all long out of copyright.

## What to do

1. Grade each piece automatically from its own content — the `SkillTag`s its notes carry
   already describe its difficulty, so the scheduler can place a newly imported piece
   without anyone hand-labelling it. This is the payoff for tagging notes rather than pieces.
2. Import as a build-time step producing bundled assets, with the parser's "what I dropped"
   report checked at import time rather than at runtime. A piece that only *nearly* parses
   must fail the import, not ship.
3. Support opening a `.musicxml`/`.mxl` through the system file picker, so Dewi can throw any
   score at it without a release.

## Done when

- Enough graded material that the scheduler is choosing between pieces rather than falling
  back to the generator at every level.
- Every bundled piece records its source and licence in the repo.
- An import-time check that each bundled file round-trips through the parser with nothing
  dropped.

## What actually happened (2026-08-15)

Delivered, with two departures from the plan above, both forced by measurement:

- **"Nothing dropped" became "nothing *material* dropped."** The original done-condition —
  every bundled file round-trips with nothing dropped — would have rejected 59% of the
  corpus over slurs and dynamics. The app draws from the parsed score, so a dropped slur
  leaves the page and the expectation agreeing; only a hole in the music matters. `DropKind`
  draws that line, and the shipped pieces do satisfy the stricter reading of it: **no shipped
  piece drops anything material**, asserted by `CorpusTest`.
- **Automatic grading needed a new predicate.** Point 1 assumed a piece's `SkillTag`s would
  place it. They cannot: a stage's declared skills are a teaching claim, not an enumeration,
  so no stage lists `Leap(5)` and the subset test rejects all real music (8,744 of 8,798
  passages). `DifficultySpec.admits` grades against the dials instead.

Point 3 — opening a score through the system file picker — was **not** done and is now
[open-any-score](open-any-score.md).

See [`../features/repertoire.md`](../features/repertoire.md).
