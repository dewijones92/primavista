---
title: Remove the superseded RepertoireStore
kind: todo
status: planned
priority: low
area: database
updated: 2026-08-16
---

# Remove the superseded RepertoireStore

## What it is

`RepertoireStore`, `RoomRepertoireStore`, `RepertoireEntry` and the Room table behind them are a
complete, instrumented-tested subsystem that **nothing constructs**. `AppContainer` has skill,
session, settings and journey stores; there has never been a repertoire one.

It was found by the sweep described in `.claude/CODE-NOTES.md` — the one that also found the audio
calibration wiring — and its interface still records what it was for: `summaries()` is documented
as "exactly what `PracticeScheduler.next` wants for its `available` argument".

## Why it is superseded rather than missing

That job was taken by `AppRepertoire`, which parses every library and hands the scheduler real
passages rather than stored summaries. And the thing the store could not do — say how to *re-read*
a piece — is what kept scores actually needed, so those use a `manifest.tsv` in the same format the
shipped corpus uses (`ScoreManifest`), with no new schema at all.

So it is not that the store is unfinished. It is that two other things now do both halves of its
job, better.

## Why it was not deleted in the same pass

Dropping the table needs a schema version bump and a migration, with a migration test. That is a
separate, self-contained change and does not belong inside a feature commit — bundling a schema
migration into a UI feature is how a migration goes unreviewed.

## Done when

- `RepertoireStore`, `RoomRepertoireStore`, `RepertoireEntry`, the entity and the DAO are gone.
- The schema is bumped with a migration that drops the table, and a migration test covers it, in
  the style of the existing v1→v2 and v3→v4 tests.
- `RepertoireStoreTest` is deleted with them, and `docs/tests/_index.md` re-counted.
