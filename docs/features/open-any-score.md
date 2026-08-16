---
title: Reading a score Dewi already has
kind: feature
status: shipped
area: ui
updated: 2026-08-15
---

# Something of your own

The Repertoire tab can open a MusicXML file from the phone. A piece his teacher set, something
exported from MuseScore, anything he already has — read by **exactly** the road a shipped piece
takes.

That sameness is the whole design. Same parser, same keyboard-part choice, same material-vs-
decoration split, same grading, same windowing into passages. `PracticeRequest` was changed to
carry a `Score` rather than a shipped piece, so by the time anything downstream sees it a song that
came with the app and a file off the phone are indistinguishable. Otherwise "open a file" would be
a second way to practise, which is the failure the twin laws exist to prevent.

## Spec I6 is untouched

The app still has **no `INTERNET` permission** and still fetches nothing. The file comes through the
system document picker, which means Dewi hands the app something it already had access to. The card
says so out loud, because "open a file" is exactly the moment someone wonders where it goes.

## What differs from a shipped piece, and is said out loud

- **Its rights are unknown.** A shipped piece records a licence per work; a picked one records
  "whatever Dewi's copy is", because a guess would be worse than an admission.
- **Its losses are shown before it is read, not after.** If anything material could not be parsed,
  the card says how many things and what they were. A shipped piece has already been screened; this
  one has not.
- **The keyboard part is preferred, and the first part is the fallback.** A picked file is as likely
  to be a single line written out for him as it is to be a piano score, so a file with no two-staff
  part is read rather than refused.

## Told apart by content, not by name

`isCompressedMusicXml` looks for the zip magic. A picked file's display name may be anything —
`score.xml` for a zipped one, no extension at all, a name in a script this app cannot case-fold —
so the content decides. `Corpus` uses the same entry point, so there is one dispatcher.

## A picked file is not a file this repo wrote

Every shipped `.mxl` came from `tools/repertoire`, so reading each zip entry whole was safe. Since
the picker exists, that same code takes an arbitrary archive off the phone — and a forty-kilobyte
one can inflate to gigabytes, which is an out-of-memory kill rather than an error message.

`MxlContainer` counts every byte it inflates against one budget and refuses past it **with the
limit in the reason**. Entries that cannot be music — images, fonts — are inflated and thrown away
rather than kept, but still cost the budget, because a zip stream cannot seek past an entry and a
bomb hidden in `Pictures/cover.png` has to be refused just like one in `score.xml`.

The limits come from what real files are, not a guess. Across the 41 shipped songs: largest **1.5MB**
uncompressed, at most **2** entries, worst ratio **46:1**. The caps are 32MB and 64 entries —
roughly twenty times the largest thing that has ever come through.

## Held by

- `MxlContainerTest` (8 JVM tests) including a real zip bomb built in the test, one hidden in an
  entry that would be discarded, and an assertion that every shipped file sits well inside the caps
  — because caps no real file could reach are caps nobody has checked.
- `PickedScoreTest` (9 JVM tests), whose fixtures are **the shipped corpus itself**: bytes the app
  ships and bytes Dewi picks must reach the same score. Plus the refusals — not-MusicXML, empty —
  each naming the file.
- Seen end to end on the emulator on 2026-08-15: a file pushed to `Downloads`, picked through the
  real system picker, and practised as *Minuet in G — 25 notes at 72bpm*.

## One bug this found

A fresh Practise tab ran two `LaunchedEffect`s at once — "choose something to read" and "open what
was asked for" — and whichever coroutine finished last won. Asking for a piece could land you on a
generated exercise. The race predates this feature; the picker just made it easy to hit. Now one
effect decides, and the request wins.
