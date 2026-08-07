---
title: PrimaVista — agent entry point
kind: index
updated: 2026-08-07
---

# AGENTS.md

Entry point for anyone (human or agent) working on PrimaVista. Start here, then read
what's relevant.

1. **`CLAUDE.md`** (repo root) — the binding project context: decisions, the quality bar
   (Unified + DRY twin laws), the timing rules, the diagnostics rule, architecture, build
   and test commands. Read it first.
2. **`docs/`** — a maintained hierarchy of living documentation. See
   [`docs/README.md`](docs/README.md) for the map. In short:
   - [`docs/spec.md`](docs/spec.md) — what PrimaVista IS and which behaviours must never
     break. Read this before deciding what to work on.
   - [`docs/architecture.md`](docs/architecture.md) — the unified seams + module map.
   - [`docs/features/`](docs/features/_index.md) — one doc per feature (status, seam, files, tests).
   - [`docs/todos/`](docs/todos/_index.md) — the live backlog, one file per item.
   - [`docs/tests/`](docs/tests/_index.md) — testing strategy + coverage map.
3. **`.claude/CODE-NOTES.md`** — the long-form *why* behind non-obvious code, kept out of
   the source per the comment rule in `CLAUDE.md`. If a file's logic looks arbitrary, look
   here before assuming it is.

## Keep the docs current (part of "done")

These docs are **living** — a change isn't done until the docs describing it are updated in
the same pass:

- Ship or change a feature → update its `docs/features/<name>.md` (and the index).
- Start / finish / drop a backlog item → update `docs/todos/`.
- Add or move test coverage → update `docs/tests/_index.md`.

Every doc carries YAML frontmatter (`status`, `updated`, …). Bump `updated` when you touch
a doc. **A `status` is a claim, and a stale claim is worse than none** — when you touch an
area, re-read its status against the code and correct it; a disagreement between a file and
the index is a bug in both.
