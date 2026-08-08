# PrimaVista

**PrimaVista** — *a prima vista* is the musical term for playing something at first
sight. One Android app whose entire purpose is making Dewi **fluent at reading music
notation**: a real piece scrolls past at tempo, he plays or taps along, and the app
tells him — note by note — whether he was right and whether he was in time.

Provisional name, chosen 2026-08-07 while scaffolding; `applicationId` is
`com.dewijones92.primavista`. Renaming is free until a remote exists, so say the word.

The original brief lives in `init` at the repo root, with Dewi's four scoping answers
recorded in the Decisions table below. Decisions here supersede the brief where they
conflict.

**Living docs:** `AGENTS.md` → `docs/` holds a maintained hierarchy (frontmatter'd
markdown) documenting features, the backlog (`docs/todos/`), tests, and architecture.
Keeping them current is part of "done": ship/change a feature → update
`docs/features/<name>.md`; start/finish/drop a backlog item → update `docs/todos/`;
change coverage → update `docs/tests/`. Bump each doc's `updated`.

## What it is, in one sentence

Not a quiz app that shows you a notehead and asks its name. **A sight-reading trainer**:
notation moves, you keep up or you don't, and the app is honest about which.

## Decisions (agreed with Dewi, 2026-08-07)

| Decision | Choice | Why |
|---|---|---|
| Core skill | **Read real pieces, in time** — notation scrolls at tempo, no stopping, no going back | Dewi's explicit choice from four options. It is the top rung, so see *The ladder problem* below |
| Clefs | **Grand staff — treble + bass together**, from day one | Dewi's choice. It is the notation everything is actually written in |
| Input | **Two adapters behind ONE seam**: `TAP` (multi-touch on-screen keyboard) and `PLAY IT` (microphone). MIDI is a third adapter, not a rewrite | Dewi chose both. Tap gives sub-second reps anywhere; mic transfers to real playing |
| Mic + polyphony | Mic judges **monophonic lines only**. Polyphonic material in mic mode is **refused with a stated reason**, or offered as hands-separate practice | Polyphonic transcription from a phone mic is research-grade; a silent mis-score is worse than an honest refusal. Refusal-with-reason is lifted from Totum's `routeNow` |
| Repertoire | A MusicXML subset parser **plus** a procedural graded-exercise generator **plus** a small hand-authored public-domain corpus | See *The ladder problem*. Public-domain only — no licensing question ever |
| Notation rendering | **Native Compose Canvas + Bravura (SMuFL)**, driven by a pure-JVM layout engine. No WebView, no VexFlow | Offline, native motion, unit-testable layout, and the only route to a genuinely beautiful staff |
| Timing | **One `Conductor`** owns musical↔wall time, fed by a monotonic clock. Nothing derives timing from recomposition | Totum paid for this twice (see *Timing is the whole game*) |
| Stack | Kotlin + Jetpack Compose, single Gradle project, manual DI | Mirrors Totum: native fit, strongest type safety, compile-time wiring |
| minSdk | **34** (Android 14) | Modern personal device; simplifies the stack |
| Network | **No `INTERNET` permission at all** | Nothing this app knows can leave the device except through a share sheet Dewi tapped. A checkable claim, not a promise |
| Brand | **Brass on ink** — warm brass hero, violet counterpart, mint/coral verdicts; **dynamic colour OFF by default** | Dynamic colour would substitute the wallpaper's palette on every modern device, so a defined brand would never actually be seen (Totum's reasoning, same conclusion) |
| CI/CD | GitHub Actions; signed APK per push to `main` on its own tagged release | No Play Store; Obtainium installs it |

### The ladder problem (why a generator exists)

Dewi picked the top rung: real pieces at tempo. The honest problem with starting there is
that **there is nothing to measure on day one** — you fail Bach, and "you failed Bach" is
not a lesson. The option's own description said so before he chose it, and he chose it
anyway, so the app's centre is the scrolling piece.

The generator is how that choice is made survivable rather than quietly narrowed: a
`Score` can be **generated** to a difficulty spec (clef set, pitch range, key, rhythm
vocabulary, hand independence) as easily as it can be **parsed** from MusicXML. Both are
the same type, so the scrolling loop cannot tell them apart. That gives infinite material
at whatever level he is actually at today, converging on the corpus.

A generated exercise and a Bach minuet being the same type is the single most important
unification in this repo. If they ever diverge, that is a design failure.

## Quality bar (non-negotiable)

- **Unified, always** — this and DRY are the project's twin laws, inherited from Totum
  where they earned their place. Every capability gets ONE seam. Before building
  anything, ask what the **input-agnostic, clef-agnostic, origin-agnostic** seam is, and
  build that; the specifics live in small adapters behind it. Concretely:
  - one `Score` (a generated exercise *and* a parsed Bach minuet),
  - one `AnswerSource` (tap, mic, and later MIDI all emit `PlayedNote`),
  - one `PerformanceJudge` (every verdict in the app comes from here),
  - one `Conductor` (every clock reading in the app comes from here),
  - one `StaffLayout` (treble, bass and the grand staff are one engine with different
    clef mappings, never two renderers).

  A feature implemented twice — once per input, once per clef — is a design failure even
  if neither copy shares a line. Where a split is genuinely unavoidable, **stop and
  surface the reason to Dewi** rather than silently building two paths.
- **Testing pyramid**: many fast JVM unit tests; fewer integration tests; few
  instrumented/UI tests. New behaviour lands with tests. The pure-JVM modules are where
  the correctness lives, and they are cheap to test, so there is no excuse.
- **Strictly DRY, and it is a hard law rather than a preference.** Knowledge lives in
  exactly one place: versions and SDK levels only in `gradle/libs.versions.toml`; Android
  build defaults only in the root build's `androidDefaults`; shared code in `:lib:common`,
  never copy-pasted. Before writing anything that resembles something already here, go and
  look for it — this repo has already shipped two bugs that were purely a second copy of a
  decision. `specTargeting` existed twice and the copies disagreed by nearly three octaves,
  with the wrong one live; the polyphony test existed twice and the weaker one was the gate.
  Both compiled, both had tests, and both were wrong.

  So when you catch yourself about to duplicate: **factor it, or record the duplication here
  with its reason.** Those are the only two options. A duplication nobody wrote down is a
  future bug with a countdown on it.
- **SOLID** — spelled out below, because each letter has already earned its place here by
  catching or causing something real.
- **Maximum compile-time safety**: sealed hierarchies, value classes over primitives
  (`Pitch`, `Midi`, `Hertz`, `Ticks` are not `Int`s and not each other), exhaustive
  `when`, illegal states unrepresentable. A `when` over inputs or clefs must fail to
  compile when a third is added.
- CI must stay green; quality gates (detekt, lint, tests, Kover) block merges.

## SOLID, in this codebase's own terms

Not a recitation — every letter below is illustrated by something that actually happened in
this repo, and that is the reason it is worth keeping.

- **S — Single responsibility.** Each seam answers exactly one question, and the question is
  written on it: `Conductor` answers *where in the music are we*, `PerformanceJudge` answers
  *was that right*, `StaffLayout` answers *where does every glyph go*. The pause bug was an
  SRP violation before it was a timing bug: the judge had been handed the live `Conductor`,
  so a type whose job was *deciding* was also able to *ask what time it is*, and the moment
  the transport moved the verdicts moved with it. Splitting the mapping out fixed the
  responsibility and the bug in the same change.

- **O — Open for extension, closed for modification.** Adding MIDI is a new `AnswerSource`
  adapter and **zero** changes downstream; adding a clef is one entry in `Clef` because the
  layout engine reads `referenceDiatonicIndex` instead of branching. The test of this is
  blunt and worth applying: *if adding the next input or the next clef requires edits
  outside its own class, the seam is wrong* — and that is a finding, not a chore.

- **L — Liskov substitution.** Any `AnswerSource` must be usable wherever another is, which
  is precisely why `Polyphony` is **declared on the port** rather than assumed by the
  caller. A mic that quietly behaved as if it were polyphonic would substitute *badly* in
  the most damaging way available — it would score Dewi on notes it never heard. The
  refusal (spec I3) is Liskov enforced at runtime where the type system cannot reach.

- **I — Interface segregation.** `TickTiming` exists as a separate, smaller interface from
  `Conductor` for one reason: the judge needs the musical↔wall mapping and has no business
  being able to start, pause or query the transport. Handing it the fat interface is what
  made the pause bug possible at all. A caller should be given the narrowest thing that does
  its job.

- **D — Dependency inversion.** Everything depends on ports; `AppContainer` is the **only**
  place in the app that names a concrete adapter. That is what makes `:core:practice` and
  `:core:notation` pure JVM, testable in milliseconds and in fake time, and it is why the
  interesting correctness in this app never needs a device to check.

## Shift left: make the compiler find it, not Dewi

The order of preference for catching a defect, strongest first, and always reach for the
strongest one available:

1. **It cannot be expressed.** A sealed hierarchy, a value class, an exhaustive `when`. The
   `-1` sentinel in `NoteJudgement` was removed for exactly this reason: it made
   `attackedNotes[-1]` one careless call away, and a sealed type made the illegal state
   unrepresentable instead of merely unlikely.
2. **It fails to compile.** Warnings are errors — see below. A pure-JVM module cannot see
   Android because it does not have the dependency, so leakage is a compile error rather
   than a code-review comment.
3. **A static analyser rejects it.** detekt (with ktlint formatting) and Android lint, both
   failing the build, on every module automatically from the root build.
4. **A test fails.** Cheap, fast, and where the correctness lives — but already a step
   behind, because a test only catches what someone thought to write.
5. **It is visible on screen.** The last four real bugs were here, and this is late.
6. **A diagnostics report from Dewi's phone shows it.** This is the last line, and by the
   time it fires he has already practised the wrong thing.

Concretely, and non-negotiably:

- **`allWarningsAsErrors` on every module**, configured once in the root build's
  `kotlinDefaults`. A warning is the compiler saying it found something and decided not to
  stop you; on a one-person app nobody triages a warning list, so it must stop you.
- **`explicitApi()` on every pure-JVM module.** Public API is deliberate, and a return type
  is stated rather than inferred.
- **Android lint with `warningsAsErrors = true` and `abortOnError = true`**, applied to
  every Android module from `androidDefaults` — a module cannot opt out by forgetting.
  Only version-freshness nags are disabled, because those go red on someone else's release
  rather than on a change here.
- **detekt with `buildUponDefaultConfig`**, and a rule is loosened in
  `config/detekt/detekt.yml` **with a written reason** or not at all. Never `@Suppress` at
  the call site to get a build through.
- **Kover at 75% on the logic modules**, ratcheting upwards and never down without a
  recorded reason. Adapter modules are exempt *and covered by instrumented tests instead* —
  exempt is not the same as untested.
- When a gate does fire, **fix the code, not the gate.** Every relaxation in this repo's
  config carries a comment explaining what it was hiding; if you add one without that, you
  have moved the problem rather than solved it. The `MagicNumber` rule was widened for named
  arguments and enum entries precisely because it was pushing real indirection into contract
  files, and that reasoning is written down next to the change.

## Timing is the whole game

This app is a stopwatch with a staff drawn on it. Every hard bug it will ever have is a
timing bug, so the rules are set before the code:

- **One clock.** `Conductor` converts musical position ↔ wall time and nothing else does.
  Tempo changes, count-in, pauses and the scroll offset are all derived from it.
- **Never time anything from recomposition or from a `StateFlow` of state.** Totum lost a
  week to exactly this: a `StateFlow` conflates equal values, so "nothing has changed" is
  indistinguishable from "no emission", and a stall watchdog that *collected* state never
  fired. Sample on a frame clock; derive edges where the edge actually happens.
- **Take timestamps from the source, not from where you noticed them.** A tap's time is
  `MotionEvent.eventTime`, not the moment a composable recomposed. A played note's time
  comes from `AudioRecord`'s frame position, not from when the read returned. Both convert
  into the Conductor's timebase at the boundary, once.
- **State the latency budget and measure it.** A judgement claiming ±20ms accuracy on a
  path with 80ms of unmeasured audio latency is a lie with a number attached.

## Diagnostics are part of every change (a MUST, not a preference)

Inherited wholesale from Totum, for the same reason and with the same force: this app will
be debugged from reports sent off a phone that is not in front of you, so **an unlogged
decision is an unanswerable question**. A feature is not done when it passes tests; it is
done when a report from a week later can settle whether it actually worked.

- **Log the inputs, not only the outcome.** `judge n=12 -> WrongPitch [expected=F#4
  heard=F4 dt=+38ms src=mic conf=0.91 poly=mono tempo=72 lat=61ms]` can be re-judged
  after the fact. "wrong note" cannot.
- **Log why nothing happened.** Silence is the hardest thing to debug, and a refusal
  (`PLAY IT refused: score is polyphonic at bar 3`) is exactly the line that stops a
  future session guessing.
- **A report must answer the obvious next question.** If it says a session scored 61%, it
  must also say which skills failed, at what tempo, on which input.
- **Bounded buffer, so chatty logs destroy evidence.** Anything firing many times a second
  (position ticks, pitch frames) is **counted and logged periodically**, never per-event
  and never dropped. Totum lost sixteen minutes of history to one per-event line.
- **Name the field and spell the unit.** `dt=+38ms` not `38`. Two different situations
  must never produce the same line.
- Debug logging is prefixed `dewidebug` and **stays committed** until Dewi says otherwise.

## Build & test

```bash
./gradlew assembleDebug                                          # build debug APK
./gradlew detekt lint test koverVerify assembleDebugAndroidTest   # the full local gate (matches CI)
./gradlew connectedDebugAndroidTest                              # instrumented (device/emulator needed)
```

- JDK 21 at `/home/dewi/code/jdk/jdk-21.0.5+11` (already `JAVA_HOME`); Android SDK at
  `/home/dewi/code/android-sdk` (see `local.properties`, not committed).
- The `android` CLI (`~/.local/bin/android`) handles emulators, screenshots, layout
  inspection and docs search.
- **This laptop's WSL VM runs close to its memory cap.** Gradle's heap is capped at 2GB in
  `gradle.properties` for that reason, and there is usually already a warm emulator plus
  another project's Gradle daemon resident. Reuse the running emulator; do not boot a
  second one; if the VM wobbles, check `journalctl -b -1` for OOM before blaming the code.
- **On-device testing matters.** Totum's precedent is exact and worth repeating: a podcast
  RSS bug passed every JVM test and only appeared on a device, because Android's XML parser
  rejects `DocumentBuilder` bean-property toggles that the JVM accepts. **This repo parses
  XML too** (MusicXML), so the parser is hardened the same way — set no optional features,
  guard the ones you must set, and verify a real parse on the emulator, not just in a JVM
  test.

## Architecture

Dependencies point inward. The pure-JVM modules know nothing about Android, and that is
enforced by them not having the dependency.

- **`:core:score`** — pure JVM. The unified music model and the only place it lives:
  `Pitch` (letter + alteration + octave, kept distinct from the sounding `Midi` — F♯ and G♭
  are different notated pitches at the same sounding pitch, and the staff draws the former
  while the judge compares the latter), `Ticks` (**integer** ticks at 10080 per quarter, a
  divisor chosen so every subdivision and tuplet real music uses divides exactly — never
  floating point, because dotted triplets do not survive doubles), `Note`, `Staff`,
  `Clef`, `KeySignature`, `TimeSignature`, `Score`. Plus the two ways a `Score` comes into
  existence: `MusicXmlParser` (hardened DOM, `.musicxml` and zipped `.mxl`) and
  `ExerciseGenerator` (deterministic from a seed + difficulty spec, so a failed exercise
  can be replayed exactly from a report). `explicitApi()` is on.
- **`:core:notation`** — pure JVM. `StaffLayout`: `Score` → positioned glyphs in staff-space
  units, with no Android or Compose types anywhere. Stems, beams, ledger lines,
  accidentals, dots, rests, barlines, the grand staff's brace and the bass clef are all
  this one engine. Glyph metrics come from **Bravura's own metadata JSON** through a
  `GlyphMetricsSource` port (the app reads it from assets), so the metrics cannot drift
  from the font. Being pure and geometric, it is golden-testable: a layout is a list of
  numbers, and numbers can be asserted.
- **`:core:practice`** — pure JVM. Where correctness lives:
  - `Conductor` — musical↔wall time, over a `NanoClock` port so tests run in fake time.
  - `AnswerSource` — the one input seam, emitting `PlayedNote(pitch, atNanos, confidence)`.
    `TapAnswerSource` is pure and lives here; the mic adapter lives in `:core:audio`
    because it needs hardware. An adapter declares its `Polyphony` so the judge can refuse
    rather than mis-score.
  - `PerformanceJudge` — expected `Score` + observed notes → a `Verdict` per note
    (`Correct` | `WrongPitch` | `Early` | `Late` | `Missed` | `Extra`) and a session
    summary. Every judgement in the app comes from here.
  - `SkillTag` + the scheduler — each note carries the reading skills it exercises (clef,
    pitch region, ledger lines, accidental, key, rhythm figure, hand independence).
    Failing a note debits its skills; the next piece or generated exercise is chosen
    weighted towards what is weak and due. This is what makes it a trainer rather than a toy.
- **`:lib:pitch`** — pure JVM monophonic pitch detection (YIN), **deliberately independent
  of `:core:score`**: it speaks hertz and confidence, not notation, so it is a standalone
  reusable library (Totum's `:lib:ytdlp` precedent). Being pure JVM, it is tested against
  synthesised tones — real DSP correctness with no device involved.
- **`:core:audio`** — Android. The adapter module where hardware meets practice:
  `AudioTrack`-based metronome and reference-tone playback, `AudioRecord` PCM capture, the
  `MicAnswerSource` that turns PCM → `:lib:pitch` → `PlayedNote`, and the monotonic clock
  the `Conductor` runs on. Kover-exempt (thin hardware bridge, instrumented-verified).
- **`:core:database`** — Android (Room via KSP). Sessions, per-note verdicts, skill state,
  repertoire and settings. The only place entities meet domain types. Kover-exempt,
  instrumented-verified.
- **`:lib:common`** — pure Kotlin, no app dependencies: shared value types and the `Diag`
  ring buffer + report builder that the diagnostics rule above depends on.
- **`:app`** — Compose UI, theme, screens, and `AppContainer` (manual DI: wiring is code,
  errors are compile-time; no Hilt, no Koin). The only place adapter choice lives.

## Working agreements

- Commit as you go — small, coherent commits at each green state.
- **Comments are rare.** Explain a non-obvious *why*, never narrate the *what*; let names
  carry the meaning. **A comment whose text exceeds ~30 characters does not go inline** —
  it goes in `.claude/CODE-NOTES.md`, grouped by file, naming the nearest symbol, with
  nothing left behind in the code. Dewi has raised comment verbosity three times in Aptem
  repos; the substance is not lost by relocating it. Build files, `CLAUDE.md` and `docs/`
  are exempt — configuration rationale belongs next to the configuration.
- **Own the repo.** Take the structurally right option over the timid one: collapse
  duplicated types, rename for honesty, move code where it belongs. Guardrails stay —
  gate green, verify on-device, one coherent commit per move — and genuinely-Dewi's
  decisions get surfaced, not assumed.
- Every push to `main` publishes a signed APK to its **own** release, tagged
  `v0.1.<run number>` to match `versionName`, deliberately **not** a prerelease so
  `/releases/latest/download/primavista.apk` stays a stable URL for Obtainium. (Totum
  learned this the hard way: a rolling `latest` tag means Obtainium's default version
  string never changes, so updates are never detected.)
- **Never re-run an older `main` CI run while one is in flight** — `cancel-in-progress`
  concurrency means re-running an old run cancels the current one, which is what publishes
  the APK. Let the newer commit prove it; the tip is a superset.
- Release signing key lives outside this repo: locally, in Actions secrets, and in a
  private backup repo. Never committed.
