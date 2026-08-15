---
title: Open any score Dewi already has
kind: todo
status: planned
priority: medium
area: ui
updated: 2026-08-15
---

# Open any score Dewi already has

## The problem

The shipped repertoire is fixed at build time. If Dewi has a `.musicxml` or `.mxl` of
something he is actually learning — a piece his teacher set, something exported from
MuseScore — there is no way to read it in the app.

## What to do

Open it through the **system file picker**. That keeps spec I6 intact: the app still has no
`INTERNET` permission and never fetches anything; the user hands it a file it already has
access to.

The pipeline is already in place and needs no new music code:

1. `DomMusicXmlParser.parseCompressed` handles `.mxl`, `parse` handles `.musicxml`.
2. `PartChoice.Keyboard` picks the piano part out of a multi-part score.
3. `MusicXmlResult.Parsed.material` says whether anything a reader would notice was lost —
   which is exactly the warning to show before practising against it.
4. `Repertoire.offers` places it on the path and windows it.

What is missing is the Android side: an `ACTION_OPEN_DOCUMENT` intent, reading the bytes
through `ContentResolver`, and somewhere to keep it (Room, or re-pick each time).

## Done when

- Dewi can pick a file and practise it in the same session.
- A file that loses music says so plainly before he reads it, naming what went.
- A file that is not MusicXML at all is refused with a reason, not a crash.
