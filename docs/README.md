---
title: PrimaVista docs
kind: index
updated: 2026-08-07
---

# PrimaVista documentation

Living documentation for PrimaVista — the Android app for becoming fluent at reading music
notation by sight-reading real pieces at tempo. `CLAUDE.md` (repo root) holds the binding
decisions and quality bar; these docs track the detail and keep it current.

## Layout

| Dir | What | Convention |
|---|---|---|
| [`spec.md`](spec.md) | What PrimaVista IS, and the invariants of its core loop | one file |
| [`architecture.md`](architecture.md) | The unified seams + module map | one file |
| [`features/`](features/_index.md) | One doc per feature — status, seam, files, tests | `<feature>.md` + `_index.md` |
| [`todos/`](todos/_index.md) | The live backlog — one file per item | `<slug>.md` + `_index.md` |
| [`tests/`](tests/_index.md) | Testing strategy + coverage map | one file |

## Frontmatter

Every doc starts with YAML frontmatter. Common fields:

```yaml
---
title: Scrolling staff
kind: feature | todo | index | reference | spec
status: shipped | in-progress | planned | dropped   # features & todos
priority: high | medium | low                        # todos
area: score | notation | practice | input | audio | ui | build
updated: 2026-08-07
---
```

## Maintenance (the one rule)

A change isn't done until its docs are updated in the same pass — see
[`AGENTS.md`](../AGENTS.md). Bump `updated`. Keep it honest: `status: shipped` means it is
on `main` and verified on a device.
