---
title: Repertoire — real pieces, placed on the path
kind: feature
status: shipped
area: score
updated: 2026-08-15
---

# Repertoire

Twenty-five nineteenth-century songs ship with the app, alongside the three hand-authored
openings. Schubert's *Gretchen am Spinnrade*, Satie's *Socrate*, Haydn, Mendelssohn, Bizet,
Fauré, Schumann, Amy Beach, Chiquinha Gonzaga, Marie Jaëll, Josephine Lang, Augusta Holmès,
Pauline Viardot, Clémence de Grandval. All CC0, from the
[OpenScore Lieder Corpus](https://github.com/OpenScore/Lieder) at commit `6b2dc542`.

This is the app's stated purpose made real: *read real pieces, in time*.

## How a piece gets here

`tools/repertoire` — an author-side Gradle module outside `:app`'s dependency graph, so none
of it ships. It is deliberately built on the app's **own** parser, grader and curriculum
rather than a tool-side copy: a piece it accepts is one the phone will read identically,
which is the only claim worth making.

```bash
./gradlew :tools:repertoire:run --args="report --corpus <dir> --commit <sha>"
./gradlew :tools:repertoire:run --args="import --corpus <dir> --commit <sha> --out core/score/src/main/resources/corpus"
```

The `.mxl` is copied **byte for byte** as published — provenance survives, and the file the
app parses is the file that was screened. Selection is written to
`corpus/lieder/manifest.tsv`; the three hand-authored pieces live in `corpus/manifest.tsv`.
An import is a data change and touches no Kotlin.

## Four things this needed, each found by measuring

| Problem | Answer | Evidence |
|---|---|---|
| "The first part" of a song is the **singer** | `PartChoice.Keyboard` — the first part written on two or more staves | Two staves *is* keyboard writing, so it survives parts named `Klavier`, `Pianoforte` or nothing |
| Screening on "was anything dropped" rejects nearly everything | `DropKind` — the question is whether a drop leaves a **hole in the music** | The parser advances its cursor by the file's own duration even for notes it refuses, so no drop shifts the timeline. Reclassifying grace notes, cue notes, noteheads and plain repeats took the corpus from **41% to 93%** usable |
| A whole song cannot be graded — it contains every skill somewhere | `Score.excerpt` / `Score.passages` | **8,744 of 8,798** passages were unplaceable when graded whole. Windowed, they place across stages 5–10. Window length is itself difficulty: **735** passages placed at 4 bars, **140** at 8, **17** at 16 |
| Grading by the skills a stage *declares* cannot work | `DifficultySpec.admits` | A stage's skill set is a teaching claim, not an enumeration — no stage lists `Leap(5)`, so the subset test rejects every bar of real music |

## What the reader sees

- **The Repertoire tab** lists pieces easiest first, each with the rung it becomes readable
  at, its length, and how many bars will actually open. Rows appear as each piece finishes
  parsing rather than all at once.
- **Material loss is separated from decoration.** "487 markings dropped" on every real song
  was alarming and wrong; the app draws from the parsed score, so a missing slur leaves the
  page and the expectation agreeing. Only a hole in the music gets the error colour.
- **"Practise this" opens a passage, not a song.** `Repertoire.passageFor` gives the most of
  the piece the rung you stand on admits, falling back to its easiest passage — a beginner
  asking for Schubert gets its easiest four bars, not a refusal.

## Seams

- `Corpus` — reads manifests; difficulty is **not** stored, because the app derives it with
  the same grader the import used.
- `Score.passages` / `Score.excerpt` (`:core:score`) — a passage of a Score is a Score.
- `DifficultySpec.admits` (`:core:score`) — is this music at this level.
- `Repertoire` (`:core:practice`) — what a piece offers, and which passage to open.
- `ShippedRepertoire` (`:app`) — parses and windows once for the whole app, publishing as it
  lands.

## Held by

- `PartChoiceTest` (9), `ExcerptTest` (11), `AdmissionTest` (9), `CorpusTest` (11),
  `RepertoireTest` (10).
- The property that ties the generator to the grader: *every exercise a spec generates is
  admitted by that spec*. It caught the accidental check reading the note instead of the key.
- *A drill aimed at anything the shipped repertoire can teach still fills its bars* — the
  scheduler will be handed whatever real music made weak, so every skill the corpus can
  produce must yield a spec that writes complete bars.
- `StaffRenderProofTest` renders all 28 pieces to PNG on the JVM and asserts none is blank.

## Known limits

- Nothing places below **rung 5**. Real piano writing is not beginner material; the generator
  covers the lower rungs, which is what it is for.
- The curriculum tops out at **one sharp**, so most of the corpus grades past the last rung.
  That is a fact about the path, not the music — see `../todos/`.
- Parsing the corpus costs real time on first use. See `../todos/repertoire-load-cost.md`.
