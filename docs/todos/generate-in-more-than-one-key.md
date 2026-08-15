---
title: A stage that teaches keys should write in more than one
kind: todo
status: planned
priority: medium
area: score
updated: 2026-08-15
---

# A stage that teaches keys should write in more than one

## The problem

`DifficultySpec.key` is a single `KeySignature`, so a stage generates every exercise in the same
key. Stage six is called *Keys* and writes only in G major. The reading ceiling
(`maxKeyAccidentals`) now lets that rung *read* up to two accidentals, which is what unlocked the
repertoire — but the material the stage writes for itself never varies.

The practical consequence is narrow rather than fatal: flats are met through repertoire and
through `specTargeting(KeyReading(f))`, which does generate in the targeted key when a key reads
weak. So the skill is reachable, just not by the stage's own material.

It also shows up as a rule the curriculum has to obey: `CurriculumTest` asserts that every skill a
stage **claims** is one its own material actually tests, so stage six can only claim
`KeyReading(1)`. That is honest, and it is also the tell that the model is one dial short.

## What to do

Give a spec a **set** of keys to draw from rather than one, chosen per exercise from the seed so
determinism survives (`generate(seed, spec)` must stay reproducible — a diagnostics report carries
only the seed and the spec). Then:

- a stage can claim every key it draws from, and the existing test proves the claim;
- `maxKeyAccidentals` becomes the *reading* ceiling for repertoire and the *bound* on that set;
- the property `every exercise a spec generates is admitted by that spec` keeps them honest.

Touches `DifficultySpec`, `SeededExerciseGenerator`, `DifficultyCodec` (another additive field with
a default, as `maxKey` was) and the stage evolutions.

## Done when

- Stage six writes in more than one key, and claims each one it writes.
- A report can still replay a generated exercise exactly from its seed and spec.
