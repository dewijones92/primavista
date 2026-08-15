---
title: Reading ahead — the card over the music
kind: feature
status: shipped
area: practice
updated: 2026-08-15
---

# Reading ahead

A decoder looks at the note they are playing. A **reader** has already looked at it and is looking
further on — which is why a fluent sight-reader can keep going through a page they have never seen,
and why someone who can name every notehead still stalls at tempo.

The oldest exercise for it is a card held over the music at the point of playing, so the notes are
gone by the time you need them and you must have taken them in earlier. This is that card.

## How it works

`ReadingLead(beats)` in `:core:practice` answers one question: **at what musical position does the
page become hidden?** Everything at or behind `position + beats` is covered, so a note disappears as
it comes within that distance of the playhead.

- **Musical, not pixels.** A beat is a beat whatever the tempo, the zoom or the piece — and it is
  the *time signature's* beat, so in 6/8 it is an eighth.
- **Not clamped to the start.** During the count-in the position is negative, so the cover sits at
  or before bar one and the opening is readable — which is what a count-in is for — then slides onto
  the page exactly as the music starts.
- **The cover is paper, not a shade.** A translucent wash would leave the notes half-readable, which
  trains squinting. The point is that the music is *not there*.
- **The pinned clef, key and time signature stay visible.** You are being asked to read ahead, not
  to remember what key you are in.

Off by default: it is a drill, and it is punishing before the notes are familiar. The dial offers
off, one beat, two beats, and a bar of common time.

## Seams

- `ReadingLead` (`:core:practice`) — pure, and the only place the distance is defined.
- `PracticeChange` (`:app`) — one seam for "a preference changed, apply it, remember it". The
  reading lead is not a toggle, which is why the enum became a sealed type rather than gaining a
  second entry point.
- `StaffCanvas(coverX = …)` — the canvas computes no positions of its own; `coverX` is
  `xOf(position + lead)`, the layout engine's own answer, exactly as the playhead is.

## Held by

- `ReadingLeadTest` (8 JVM tests), including that the cover never moves against the playhead at any
  offered lead, and that a count-in leaves bar one readable.
- `ReadingLeadMigrationTest` (3 instrumented) — a v3 settings row keeps every choice Dewi had made
  and reads ahead **off**, because a preference nobody set must not start covering the music.

## Not done

- **No evidence yet that it helps.** The app records which lead a session ran at in its diagnostics,
  but nothing compares scores across leads. That is the honest gap: this is a well-founded exercise,
  not a measured improvement.
