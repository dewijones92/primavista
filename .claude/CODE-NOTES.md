# Code notes

Long-form *why* that would otherwise be an oversized inline comment. See the comment rule in
`CLAUDE.md`: comments are rare, and anything over ~30 characters lives here instead, grouped by
module and naming the nearest symbol.

Agents implementing a module append their own `##` section and never rewrite another's.

## :lib:common

- **`RingBufferDiag.counted`** flushes a running-total line every `countedFlushEvery`
  increments rather than on every call or only at report time. Both extremes fail: a line per
  occurrence is what cost Totum sixteen minutes of history in a 400-entry buffer (59% of one
  report was a single repeated line), while tallying silently until the end means a report
  truncated by a crash shows nothing about the hot path at all. A periodic running total keeps
  the tally *and* leaves a trail of when it was accumulating.

- **`RingBufferDiag.state` stores the lambda, not its result.** Two reasons. It makes the call
  site nearly free, which matters because state blocks are registered once from code that may be
  on a hot path; and it means the report shows the value *at report time*, so a state block
  registered at session start is still current when Dewi shares the report ten minutes later.
  The cost is that a snapshot lambda must not capture something expensive or throwing — `report`
  guards with `runCatching` so a bad snapshot degrades to `<threw …>` rather than losing the
  whole report, which would be the worst possible trade.

- **`dropped` is reported, not just tracked.** A buffer that silently discards its oldest
  entries looks identical to one that was never written to. Stating "N dropped" is what tells a
  future session that the interesting event may have existed and been evicted, rather than
  never having happened — the difference between an unanswered question and a wrong answer.

## :app

- **`StaffCanvas` computes no positions.** Every coordinate arrives from `:core:notation`, which
  is why the engraving is unit-testable and why the drawn playhead cannot drift from the note
  being judged (docs/spec.md I1). A position calculated in the composable would be a second
  layout engine, and the two would eventually disagree — the exact failure the single-seam law
  exists to prevent.

- **`GlyphTextCache` keys on (glyph, pixel size).** SMuFL glyphs are drawn as text, and
  measuring forty glyphs per frame is the obvious way to make a scrolling staff stutter. The
  size is part of the key because a re-measure is required when the staff is resized, and
  keying on the glyph alone would silently render the old size after a rotation.

- **The `4f` in `SPACES_PER_EM`** is not arbitrary: the SMuFL specification defines a music
  font's em as exactly four staff spaces. It is the single conversion between the engraving unit
  (staff spaces, which all of `:core:notation` works in) and a text size in sp, and getting it
  wrong scales every glyph relative to the staff lines.

- **Glyphs are offset by `layout.firstBaseline`.** Bravura registers each glyph's origin on the
  staff line it belongs to, but `drawText` positions the text's top-left. Without shifting by the
  baseline, every notehead would sit roughly a staff below its line — which looks like a layout
  bug in `:core:notation` and is not.

- **Beams are filled quadrilaterals, not stroked lines.** A stroke caps the ends perpendicular to
  the slope, so a sloped beam would end in a bevel instead of the vertical cut engraving requires
  — visible precisely where the eye follows the rhythm.

- **Dynamic colour is absent deliberately.** Beyond the brand argument in `Theme.kt`, this app's
  verdict colours carry meaning: green is right, red is wrong, amber is off-time. Letting the
  wallpaper choose them is not a theming preference, it is a correctness problem.

## :app — practice session

- **`PracticeViewModel.tick()` is called from the UI's frame clock, and that is not the mistake
  `CLAUDE.md` warns about.** The rule forbids *deriving* timing from recomposition; here the time
  comes from `Conductor.position()` and the frame clock only decides when to look at it. Sampling
  is in fact the required shape: a `Missed` note is the *absence* of an event, so it can never
  arrive as one and must be found by looking. Totum's stall watchdog collected a `StateFlow` and
  therefore never fired at all, because a run of identical values emits nothing.

- **Backgrounding pauses rather than freezing.** Totum's autoplay bug was a composable effect that
  stopped with the activity, and the instinct after that is to move the loop somewhere that keeps
  running. For this app that would be wrong: nobody sight-reads a phone in their pocket. But it
  must *pause*, not merely stop being sampled — an unpaused Conductor keeps advancing, so
  returning after a phone call would jump the position forward and mark every note in between
  `Missed`, i.e. blame Dewi for the interruption.

- **`load()` checks `judge.accepts` before laying anything out.** Refusing early means no session
  state exists to be half-valid, and the refusal is logged as loudly as it is displayed — a
  silent refusal is indistinguishable from a crash in a report a week later.

## :core:audio

- **`FrameTimebase` extrapolates from a single anchor rather than stamping at read time.** A
  read returns when the app was *scheduled*, not when the sound happened, so `nanoTime()` at
  read time carries an unbounded scheduling error — everything in docs/spec.md I2 rests on not
  doing that. `AudioRecord.getTimestamp(…, TIMEBASE_MONOTONIC)` gives one (frame, nanos) pair
  from the audio clock; every other frame is that pair plus `frames × 1e9 / sampleRate`. The
  anchor is one immutable object behind `@Volatile` so a reader can never see a frame from one
  timestamp paired with the nanos from another, which would produce a plausible, wrong answer.

- **`FrameTimebase.framesToNanos` uses `Math.floorDiv`, not `/`.** Integer division truncates
  toward zero, so the rounding direction would flip either side of the anchor — a note before
  the anchor and one after would be biased in opposite directions by up to a sample, and the
  sign of the error would depend on where `getTimestamp` last succeeded. Floor keeps it
  monotonic. The multiply overflows a `Long` only past roughly fifty hours of capture.

- **`FrameTimebase` starts `ExtrapolatedFromStart`, and only `getTimestamp` upgrades it to
  `DeviceReported`.** The start-time anchor (frame 0 at `nowNanos()`) silently *contains* the
  HAL input latency, so measuring latency against it would be circular. That is why
  `LoopbackCalibrator` refuses to return a `Measured` result while the timebase is still
  extrapolated. The enum names say what the value *is* rather than how much we like it, which is
  why they are not `Measured`/`Assumed` — those words belong to `InputLatency.Provenance`, a
  different question, and having one pair of words answer both invited exactly the confusion
  this module exists to prevent.

- **`PcmCapture.start` returns a `CaptureStart` rather than throwing.** The commonest way it
  fails is Dewi declining the microphone, which is an ordinary thing a person does; the old code
  called `error(…)` and took the app down with it. `MicPitchAnswerSource.notes()` treats a
  refusal as an empty flow with a logged reason, so a denied permission produces no notes and no
  crash, and a report can tell the two apart.

- **`MicrophonePermission` is a port with no default.** On Android 10+ a denied RECORD_AUDIO does
  not reliably throw: the record opens and returns silence, which would surface as "the mic hears
  nothing" rather than "you said no". The check has to be made explicitly against a `Context`,
  which this module deliberately does not hold, so `:app` must supply it — a required constructor
  parameter rather than an optional one so the wiring cannot be forgotten. `MicRouteNegotiator`
  additionally reports a `SecurityException` on any attempt as `PermissionDenied`, because the
  grant can be revoked between the check and the open.

- **`AudioRecordPcmCapture` prefers `UNPROCESSED`, then `VOICE_RECOGNITION`, then `MIC`.** The
  default `MIC` path runs the device's voice chain — AGC, noise suppression, and often an
  aggressive high-pass. All three are tuned for speech intelligibility and all three damage a
  musical signal: AGC flattens the decay a piano note is mostly made of, noise suppression
  treats a sustained tone as stationary noise, and the high-pass eats the bass staff. None of
  it is visible in the code, which is exactly why the source that actually opened is logged as
  an event rather than left to be inferred.

- **`candidateSources()` consults `AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED`
  when it can.** Some devices accept an `UNPROCESSED` `AudioRecord` and quietly serve processed
  audio, so a successful open is not evidence of support. When no `AudioManager` was supplied
  the attempt still happens, but the log says support was *unverified* — a report should never
  imply we know something we did not check.

- **`refreshAnchor` re-anchors every eighth read rather than every read.** The audio clock and
  the frame counter drift slowly, so per-read `getTimestamp` calls buy nothing measurable and
  add a syscall to the hot path. Misses are `counted`, not logged per occurrence, for the
  bounded-buffer reason in `CLAUDE.md`.

- **`MicTimestampCorrection` subtracts input latency only.** This is the one boundary where
  capture frames become the Conductor's timebase; nothing downstream knows latency exists, and
  `Conductor` has no `setInputLatency` to correct a second time. It originally subtracted
  `detectionDelayFrames` as well, which was resolved as a defect during review:
  `YinNoteTracker` sets `TrackedNote.atFrame` to the *onset* frame and derives
  `detectionDelayFrames` as `confirmingEstimate.atFrame - atFrame`, so the onset needs no
  further shift and subtracting the delay biased every mic note early by the analysis window
  (21–53ms at the tracker's 2048/512 window and hop, and pitch-dependent, so it would have been
  invisible in a session average). The delay is still carried on `TimestampCorrection` and
  printed as `detect=…ms(reported)` because it is what a report needs to attribute a bias — it
  is reported, not applied. Held by two tests at the correction itself and one end-to-end
  through `MicPitchAnswerSource`, where two detections of the same onset with different
  confirmation delays must emit the same `atNanos`.

- **`LatencyCalibration` requires the peak to clear a measured noise floor.** The detector used
  to take the buffer's peak, apply a fixed fraction of it as a threshold, and return the first
  frame above that — so in a room with any hiss it returned a frame, and an ambient noise floor
  came back as a *measured* latency. That is precisely the failure
  docs/todos/measure-audio-latency.md exists to prevent, and it is worse than no measurement
  because the number looks authoritative. The floor is the **median** magnitude of the analysed
  window, not the mean or the RMS: a click is short relative to a buffer, so it barely moves the
  median but drags an RMS upward, which would let a loud click raise its own floor. The peak
  must clear that floor by `DEFAULT_REQUIRED_PEAK_TO_NOISE` (8×, about 18dB) or the search
  returns `NotFound` with the ratio in its reason. The onset threshold is then measured *above*
  the floor rather than above zero, so a threshold that noise alone could satisfy cannot exist.

- **`ClickSearch.Found.riseFrames` is the distance from the threshold crossing to the peak**, and
  it is the calibration's own uncertainty about where the click began: a sharp click is located
  to the frame, a smeared or reverberant one is not. It feeds `InputLatencyResult.Measured`'s
  confidence, so a measurement taken off a badly-shaped click reports as less trustworthy rather
  than identical to a clean one.

- **`ToneVoice` ramps its envelope to zero instead of ending on a non-zero sample.** A cut
  waveform is a step change, and a step change is broadband — the click is louder and more
  noticeable than the note it replaced. The attack ramp exists for the same reason at the other
  end. `beginRelease` shortens the note to a ramp rather than dropping the voice, so `stopAll`
  is also click-free.

- **`ToneVoice` decays each partial with its own per-frame multiplier.** Upper partials on a
  real string die faster than the fundamental, which is most of why a decaying sine sounds like
  a synthesiser and this sounds vaguely like a piano. Implemented as a recursive multiply
  (`level *= decayPerFrame`) rather than `exp()` per sample per partial: the recursion is exact
  for an exponential and replaces roughly a quarter of a million `exp` calls a second.
  Partials above Nyquist are muted at construction, because an aliased partial folds back to an
  audibly wrong pitch — the one artefact that would defeat the whole purpose of a reference tone.

- **`ToneMixer` exists so the playback anchor is decided in pure code.** The mixing, the voice
  list and the absolute frame count are one lock-protected unit, and that is the fix for a real
  defect: the player used to record `framesWritten` at `playChord` time, but that counter was
  incremented only *after* a blocking `AudioTrack.write` returned, and the voice list was
  snapshotted separately at the top of the render loop. A note enqueued during the write was
  therefore anchored to a buffer it had already missed — up to one render buffer early, 10.7ms
  at 512 frames and 48kHz, silently over-stating any latency measured against it. Because
  `add` and `render` now share one lock, the frame `add` returns is exactly the frame the
  voice's first sample lands on, whichever side of a render the call arrives on. Being pure, it
  is JVM-testable, and the test asserts the invariant directly: the first non-zero sample must
  fall inside the anchored buffer, never a buffer later.

- **`AudioTrackTonePlayer` writes silence while idle rather than pausing the track.** A
  streaming `AudioTrack` that runs dry underruns, and an underrun pops on some devices; a
  paused track adds start-up latency to the next note. Writing a silent buffer keeps
  `WRITE_BLOCKING` as the pacing mechanism, so the render thread costs a memcpy per buffer and
  never spins.

- **`lastPlayback` returns the moment *and* its uncertainty.** Converting the anchor frame to
  monotonic nanos goes through the track's own `AudioTimestamp`, so it is a presentation time
  rather than a write time; without it a loopback measures the *round trip* — output latency
  plus acoustic travel plus input latency — and using a round trip to correct an input timestamp
  over-corrects by the output component. What it cannot know is `AudioTimestamp`'s own accuracy,
  which the platform does not report, so the stated `uncertaintyMillis` covers only what is
  bounded here: one output frame for the anchor's granularity, plus the extrapolation distance
  times `ASSUMED_CLOCK_TOLERANCE` (1e-4, a stated crystal tolerance, not a measurement). The log
  line says so in as many words, because an uncertainty that quietly omits a term is the same
  class of claim as an assumed latency called measured. A null anchor still downgrades the
  calibration to `Unmeasurable` rather than being quietly halved.

- **`LoopbackCalibrator` is separate from `MicPitchAnswerSource`.** Measuring the path and
  reading notes off it are different jobs with different failure modes, and keeping them
  together pushed one class past detekt's function budget — which was the metric noticing a real
  cohesion problem rather than a false positive.

- **A calibration only returns `Measured` when the capture timebase is `DeviceReported` and the
  player can say when the click sounded.** Everything else returns `Unmeasurable` *with a reason
  naming the precondition that failed*, and the reason is the string a report shows. The tiers
  are deliberately coarse: a real loopback offset that conflates output latency is still logged,
  but it does not get to wear the `Measured` label.

- **A failed re-calibration drops back to the assumed figure rather than keeping the last good
  one.** Calibration is re-run when the audio route changes, and Bluetooth's latency is not the
  built-in mic's — so a stale measured number on a new route is worse than an honest assumption.
  The one exception is a refusal that never reached the audio path (the mic is already in use),
  which leaves the current figure alone and says so.

- **`ClickMetronome` has no timer, and `onPosition` is the whole design.** The Conductor samples
  its own position on a frame clock and hands it here; `BeatCrossing` decides whether a beat
  boundary was passed. A metronome with its own timer is a second clock, and two clocks
  drifting apart is the bug class docs/spec.md I1 exists to forbid — it would present as "the
  app is wrong about my playing" while being an audio fault.

- **The contract has no `start()`, so `configure` is what arms the metronome.** That follows from
  the driving model rather than being an omission: positions only arrive while the transport is
  running, and `configure` is called with the current bar's metre and origin, so it is the
  natural point to build the click tracks and reset the crossing. `stop()` silences it; a fresh
  `configure` re-arms it, and a `configure` mid-piece (a metre change) is expected rather than
  exceptional.

- **`onPosition` never blocks.** It is called from a frame callback, so the crossing decision is
  integer arithmetic on the caller's thread and the `AudioTrack` retrigger is handed to a single
  high-priority executor thread. Every reason for *not* clicking is `counted`
  (`ticksWhileStopped`, `ticksBetweenBeats`, `beatsMutedByToggle`, `beatsWithNoTrack`) because a
  silent metronome is otherwise indistinguishable from a broken one in a report.

- **`BeatCrossing` measures beats from `barStart`, not from tick zero.** Deriving the bar grid
  from absolute zero and one time signature is right only for a piece that begins with a
  complete bar and never changes metre. With a pickup, every bar line in the piece is offset by
  the pickup's length, so the accent lands on the wrong beat from the first bar to the last —
  audible, wrong, and the sort of thing that teaches the wrong downbeat. Subtracting `barStart`
  before the division fixes both cases at once, and `floorDiv`/`floorMod` make the negative
  positions of a pickup or a count-in come out as the *end* of the implied preceding bar: a
  one-beat pickup in 4/4 reports `indexInBar = 3`, which is what a musician would call it.

- **`BeatCrossing.crossed` fires on the first sample after a start only if it lands inside the
  opening quarter of a beat.** At a session start the sampled position is near the beat, so the
  count-in downbeat clicks on time; after a resume part-way through a beat it stays silent until
  the next boundary instead of clicking off-beat. A backwards jump re-syncs silently: a seek is
  not a beat.

- **`ClickMetronome` uses two `MODE_STATIC` tracks, not the streaming mixer.** A pre-loaded
  static buffer retriggered with `stop`/`reloadStaticData`/`play` has the lowest jitter
  available without an audio callback, and the accent needs to be a different sound rather than
  a louder one, which two buffers give for free.

- **`MicPitchAnswerSource` counts detections and keeps one `state` snapshot rather than logging
  a line per note.** A detection fires several times a second; per-event lines are what
  destroyed sixteen minutes of Totum's history. Drops are counted under distinct keys
  (`dropped.lowConfidence`, `dropped.outsideMidiRange`) so a session that heard nothing can say
  *why* it heard nothing.

- **`MicPitchAnswerSource.release` releases the capture and not the `TonePlayer`.** The capture
  exists only to serve this source, but the player is shared with the reference-tone feature, and
  releasing a collaborator someone else still holds is how a working feature dies silently.

- **A tracker whose sample rate disagrees with the capture's is logged loudly.** Every
  frame-to-nanos conversion below that point would be wrong by the ratio, and it would look like
  a tempo problem rather than a wiring problem.

- **`PitchMapping.estimate` returns null outside MIDI 0..127 instead of constructing a `Midi`.**
  `Midi`'s `init` throws, and a rumble or a cymbal is a routine input from a phone microphone —
  an exception from the audio thread on a detection that was never a note would take down a
  session for a non-event.

- **The module's unit tests cover the parts with no Android dependency** — the timebase, the
  pitch mapping, the correction boundary, the envelope, the mixer and its anchor, the click, the
  click detector's noise floor, the beat crossing, `LoopbackCalibrator` and
  `MicPitchAnswerSource` itself, none of which import anything from `android.*`. The
  `AudioTrack` and `AudioRecord` bridges are androidTest-only and have **still not been
  executed** — no device has been available in any session that has touched this module. The
  instrumented assertions that matter are named in the tests themselves: a capture whose
  timebase never becomes `DeviceReported`, and a player that cannot report a playback anchor,
  are both real findings about the device rather than broken tests, and both mean every mic
  verdict on that device carries unmeasured timing bias.

## :core:database

- **`SkillTagKeys` is one pair of functions, and the keys are literal strings per case.** The
  key is a primary key, so if the encoding of an existing `SkillTag` ever changes, the row for
  the old spelling is orphaned and Dewi's progress on that skill silently resets to nothing —
  no crash, no log, just a strength back at zero. Two consequences shaped the code. The
  discriminator is a hand-written literal (`"clefRegion"`), never an `ordinal` or a class name,
  so **adding a case to the sealed hierarchy cannot change any existing key**; and enum
  components are stored by `name` for the same reason. Renaming a `Clef` or `PitchBand`
  constant *would* still break stored keys, which is why `SkillTagKeysTest` asserts the exact
  strings — the rename has to fail a test rather than reset a strength.

- **`decode` returns null instead of throwing.** A key this build cannot read means the row was
  written by a different schema; crashing the app on read is a worse answer than skipping the
  row. `RoomSkillStore.readStates` logs the skipped keys, because a silently shorter list of
  skill states is exactly the invisible degradation spec I5 depends on not happening.

- **`DifficultyCodec` exists because the seed alone is not enough to replay an exercise.**
  `ExerciseGenerator.generate(seed, spec)` needs both, so both are stored, and the spec's text
  form is a named-field list (`bars=8;tempo=76;…`) rather than positional. Named fields mean a
  later build can add a dial without making every stored row unreadable — the unknown field is
  ignored on the way in. The encoding is also **canonical**: maps and sets are sorted before
  joining, so two equal specs always produce the identical string and the column can be
  compared or de-duplicated. It is hand-rolled rather than `kotlinx.serialization` because this
  module does not carry that dependency, and a stored format that a future build must still
  read is worth writing explicitly and testing exhaustively either way.

- **A session row survives an unreadable spec.** `StoredSession.origin` is nullable and
  `originDescriptor` always carries what the row literally held (`kind=… seed=… spec=…`).
  Dropping the session would be the tidier code and the wrong behaviour: spec I4 says what was
  practised is not lost, and a session whose *origin* cannot be rebuilt still has its verdicts,
  its tempo, its input and its accuracy. The descriptor is what a diagnostics report prints so
  the seed is recoverable by hand even when the spec is not.

- **`NoteVerdictEntity` carries `atTicks` as well as `dtMillis`.** `Verdict.Extra` is positioned
  in musical time, not as an offset from an expected note, so reusing the `dtMillis` column for
  it would store ticks in a column named milliseconds — precisely the "name the field and spell
  the unit" failure in `CLAUDE.md`. One extra nullable column keeps the round-trip lossless,
  which is what lets a report be re-judged (spec I7).

- **The verdict table has a synthetic `id`, not `(sessionId, noteIndex)`.** A trill produces
  several `Verdict.Extra`s that can legitimately share a note index, and a composite key would
  have made the second one either collide or silently replace the first — losing exactly the
  evidence that per-note storage exists to keep.

- **Foreign keys: three assertions, because one can pass while the cascade never fires.** SQLite
  ignores `ON DELETE CASCADE` unless `PRAGMA foreign_keys` is ON, and a delete-then-count test
  passes either way when the parent row is also gone. Room's generated `onOpen` does execute the
  pragma (confirmed in `PrimaVistaDatabase_Impl`), but it is generated code that has changed
  across Room versions and the pragma is a silent no-op inside a transaction, so
  `PrimaVistaDatabase.ForeignKeysOn` states it explicitly for every builder including the
  in-memory test one. `ForeignKeyCascadeTest` then proves all three things separately: the
  pragma reads back as 1, an orphan verdict is *refused*, and the cascade removes children.
  Enforcement being off cannot pass that set.

- **`RoomSkillStore` takes the update rule as a parameter (`SkillUpdateRule`).** The
  spaced-repetition fold lives once, in `PracticeScheduler.update`; a second copy here would
  drift, and the drift would be invisible because both would keep producing plausible
  strengths. The store reads, hands the states to the rule, and persists what comes back —
  all inside one `withTransaction` so a concurrent read cannot see a half-applied fold.

- **`save` is an upsert plus a delete-and-reinsert of the verdicts.** Spec I4 requires writing
  at pause as well as at finish, so the same session id is written repeatedly; replacing the
  verdict set is what makes the second write idempotent rather than doubling every note. The
  whole thing is one transaction because a session claiming 61% with half its verdicts missing
  would be a summary that cannot show its working.

- **Entities hold primitives where the domain holds value classes.** `Midi`, `Ticks` and
  `ScoreId` are value classes in the domain and plain columns here, with the conversion in
  `Mappers.kt`. That is deliberate: the entity is a storage projection, the mapper is the one
  documented place entities meet domain types (`docs/architecture.md`), and it keeps Room's
  code generation off the value-class path entirely. Enums *are* stored through
  `PrimaVistaConverters`, by name rather than ordinal.

- **`SettingsStore.latency` returns null for an unmeasured route.** Defaulting to `0.0` would
  present an unmeasured latency as a measured zero, which is the specific lie
  `docs/todos/measure-audio-latency.md` is about. Latency is keyed per audio route rather than
  being a column on the single settings row because a wired headset and the built-in speaker
  are different paths with different figures, and `InputLatency.Provenance` is stored with each
  so a report can say which numbers were actually measured.

- **`SkillStoreTest.halvingRule`** is a deliberately crude stand-in for the real scheduler rule.
  The test is about the store — that the rule sees what was already persisted, that its output
  is written, that untouched skills are left alone — so a simple arithmetic rule makes those
  assertions readable. Testing the actual spaced-repetition curve belongs in `:core:practice`,
  where it lives.

- **The instrumented tests were NOT executed**: no device or emulator was available to the
  session that wrote them. They compile (`assembleDebugAndroidTest`) and nothing more is
  claimed.

### Adversarial-review fixes (schema export, migrations, sealed judgements, key format v2)

- **`exportSchema` is ON and the JSON is committed under `core/database/schemas/`.** It was off,
  which meant the first schema change would have thrown `IllegalStateException` on Dewi's phone
  with no recovery but a reinstall — and a reinstall takes the practice history, which is exactly
  what docs/spec.md I4 forbids. The exported JSON is the artefact a migration is written
  *against*: without it a reviewer is reading a diff of Kotlin data classes and guessing what SQL
  Room will emit. `SchemaAndMigrationTest` asserts the file for the current `DATABASE_VERSION`
  exists, so turning the export off again, or bumping the version without rebuilding, fails a
  test. It has to check the *file* rather than the annotation because `@Database` has binary
  retention and `getAnnotation` returns null at runtime.

- **There is no destructive fallback, and that is the whole point.**
  `fallbackToDestructiveMigration` is the one-line fix for a missing migration and it silently
  deletes everything Dewi has practised — the failure I4 exists to prevent, dressed as a
  convenience. So `PrimaVistaMigrations.ALL` is the registry every version bump must extend, and
  `strandedVersions` computes which stored versions could not reach the current one by any chain
  of migrations. A non-empty result is a failing test, which turns "we forgot the migration" from
  a crash on his phone into a red build. Wiping is still *reachable* —
  `resetDiscardingHistory` — but it is a separate function with its own loud log line, callable
  only because someone chose it, never as a fallback the builder takes on its own.

- **`open` returns `DatabaseOpening`, and forces the file open before returning.** Room's
  `build()` opens nothing; the migration failure surfaces at whichever query happens to run
  first, which in a UI is some unrelated screen minutes later. Touching `openHelper.writableDatabase`
  moves the failure to the one place equipped to explain it. A failed open deliberately does *not*
  close the helper: closing something that never opened can throw a second exception that masks
  the first, and the first is the one that names the version mismatch.

- **`NoteJudgement` is stored as the judge produced it; `StoredVerdict` is gone.** It held a
  non-null `noteIndex`, so an extra note had to be written under a `-1` sentinel — the illegal
  state the sealed `NoteJudgement` was introduced to abolish. Re-modelling it here would have
  reintroduced it at the storage boundary and left two answers to "which note was that?". The
  column is now `noteIndex INTEGER` (nullable), null exactly for `Unexpected`, and a stored extra
  that *does* carry an index is refused with a reason rather than read as note -1 — an old
  sentinel row must not quietly become a judgement about the last note of the piece.

- **Skill-key format v2, and a v1 leger-lines key is discarded rather than upgraded.**
  `SkillTag.LegerLines` gained `above`, and the v1 key (`legerLines|Bass|3`) recorded an
  *unsigned* count, so the side genuinely is not in the data. The three options were: guess
  (silently moves a below-the-staff strength onto an above-the-staff skill Dewi has never read),
  crash, or discard with a stated reason. Only the third is honest, so `read` returns
  `SkillKeyReading.Unreadable` naming the format and what was missing. **The row is left on
  disk**, not deleted: a later build that learns to recover it still can, and an unreadable row
  costs nothing but a log line. The arity check is a second, independent guard — a v1 key fails
  it too — so the format check being removed cannot silently re-enable the guess.

- **Every dropped row now carries a reason, not just a count.** `SkillKeyReading.Unreadable`,
  `SkillRowReading.Unreadable` and `VerdictRowReading.Unreadable` exist so `RoomSkillStore`,
  `RoomSessionStore` and `RoomRepertoireStore` can log *why* a row was skipped. The previous
  version logged "3 rows unreadable", which tells a future session that something was lost and
  nothing about what — the shape of report CLAUDE.md's diagnostics rule is written against.

- **`SkillTagKeys` and `DifficultyCodec` tests moved to `src/test` (JVM) and actually run.** They
  were instrumented tests of pure string handling, so on a machine with no device they were
  compiled and never executed — the testing-pyramid rule inverted. The fixture `sampleSpec()` is
  now duplicated between `src/test` and `src/androidTest`: the two source sets cannot share code
  without a test-fixtures module, and adding one means editing `settings.gradle.kts`. Recorded
  here per CLAUDE.md's rule on deliberate duplication.

- **`androidTest` assets do not include the schema directory.** The usual
  `sourceSets["androidTest"].assets.srcDir(...)` wiring that `MigrationTestHelper` needs throws a
  `ClassCastException` under AGP 9.3, and nothing reads the schemas on-device yet (`room-testing`
  is not a dependency). Wire both up together when the first real migration lands.

## :lib:pitch

- **`YinPitchDetector.interpolatedLag`** is not polish. Without it the estimate quantises to an
  integer lag, and at G6 the nearest integer lag to 1567.98 Hz is 28 frames, which *is* 1575 Hz —
  7.7 cents sharp from arithmetic alone, before any signal imperfection. With interpolation the
  worst error measured across every semitone from 82 Hz to 1568 Hz is 0.05 cents. The parabola is
  fitted to the raw difference function rather than to the normalised one: at the true lag the
  difference is near zero so the two curves agree there, and the raw one is the cleaner parabola.

- **`YinPitchDetector.lowestLagBelowThreshold` is the octave guard.** The difference function has
  minima at every integer *multiple* of the period and none at its sub-multiples, so taking the
  lowest lag that clears the threshold cannot pick a sub-octave, which is YIN's known failure.
  Descending into the local minimum after the crossing matters too — otherwise the reported lag is
  wherever the curve happened to cross the threshold rather than where it bottoms out. Between
  them this is why a tone whose fundamental is *weaker* than its second and third partials is
  still reported at the fundamental (measured: 0.0 cents error at 196 Hz with partials at 2.5x).

- **The 0 dB SNR test asserts the refusal, not a property of the estimates.** Its first version
  looped over the returned estimates checking each was near A4 — and the detector returns *none* at
  0 dB SNR, so the loop body never ran and the test asserted precisely nothing while reading as
  coverage of the hardest case in the module. It now asserts the empty result directly, paired with
  the same tone minus the noise, so it cannot pass because the fixture produced nothing to look at.
  Any test whose only assertion is inside a `forEach` over a possibly-empty list has this bug.

- **`YinPitchDetector.analyse` returns null rather than a low-confidence number.** A wrong
  frequency with a caveat attached is worse than no frequency, because everything downstream has
  to remember to check. Three ways a window earns nothing: RMS below the silence floor; no lag
  anywhere below the aperiodicity threshold (white noise sits near 1.0 at every lag); and a
  difference function that is identically zero, which is what a DC offset produces. A consequence
  worth knowing is that `confidence` on a returned estimate always exceeds `1 - threshold`.

- **`atFrame` is the window's geometric centre**, per the contract, even though the analysed span
  is `innerFrames + maxTau` rather than the whole window, so the centre of evidence is slightly
  earlier. It is left alone because latency correction is per note through
  `TrackedNote.detectionDelayFrames`, not per window.

- **Cost dial.** The difference function is O(`innerFrames` x `maxTau`) — about 0.8M multiply-adds
  per window, 86 windows a second at 44.1 kHz with a 512 hop. `minHertz` sets `maxTau` and is
  therefore the knob that decides whether this fits in the audio budget on a phone.

- **`EnergyOnsetDetector` works in log energy**, which is what lets a single threshold serve a
  fortissimo entry and a pianissimo one: amplitude 0.9 and amplitude 0.01 produce the same onset
  frame. The adaptive median term on top is what stops a tremolo's own envelope ripple reading as
  a string of attacks.

- **`EnergyOnsetDetector.primed`: the first block sets the envelope and can never fire.** Without
  it every stream opens with a spurious onset, because the envelope starts at silence and the
  first block of ordinary room noise is a large log jump. The price is that a note beginning
  inside the very first block is missed, which is acceptable because a real capture is running
  before the player starts.

- **An onset frame is the start of the block the rise was seen in**, so it is within one
  `blockFrames` (5.8 ms at 44.1 kHz) either side of the attack: early for a sharp attack (measured
  75-249 frames), late by up to a block when the attack's own first block is under the silence
  floor and so is skipped (measured up to 181 frames). The tests state +/- one block, not better.
  `hopFrames` on the `OnsetDetector` contract is exactly this number, so a tracker can state its own
  precision instead of each caller rediscovering it.

- **What the detector cannot hear, measured rather than implied.** A re-attack that never returns to
  silence is only visible as a step in log energy above the smoothed envelope, so it needs the level
  to **roughly double — about +6 dB within one 5.8 ms block**. Measured minimum ratios by how long
  the rise is spread over: 0.7 ms 1.7x, 2.9 ms 1.9x, 5.8 ms 1.8x, 11.6 ms 2.2x, 23 ms 3.0x. The
  arithmetic floor is `thresholdMargin` = 0.45 nepers = 1.57x = +3.9 dB per block; the rest is the
  0.5-per-block envelope smoothing chasing the rise. A repeated note played *legato* on the same
  pitch, with no more than a 40% swell, is therefore genuinely inaudible to this module, and the
  paired test `a re-attack that never falls silent needs the level to roughly double` records that
  as a limit rather than leaving it to be discovered on a phone.

- **The repeated-note fixtures come in two families on purpose.** The originals separate notes with
  100 ms of digital silence, which a microphone never produces: the envelope is reset to an
  impossible zero between notes, so every attack is a rise from nothing. `Signals.struck` plus
  `Signals.noiseFloor` give the honest version — a -54 dBFS room hiss throughout and an exponential
  tail that is still sounding when the next note arrives. Both fixture families pass at four strikes
  0.25 s and 0.35 s apart, with half-lives of 0.05 s and 0.12 s.

- **The silence gate must not latch the rising edge (`evaluate`).** `aboveThreshold` records
  "an onset-worthy rise was already seen", so it may only be set when the rise *was* onset-worthy.
  Setting it from `flux > limit` alone let a block that failed the `silenceRms` check consume the
  edge, after which the audible blocks behind it were no longer a rising edge and the note vanished
  with only a `noOnset.belowSilenceFloor` tally to show for it. Any note quiet enough that its
  first attack block sits under the floor hit this: a -50 dBFS tone with a 50 ms attack produced
  zero onsets, so four repetitions of it read as one held note. Regression tests: `a quiet gradual
  attack is still an onset` and `four quiet notes with gradual attacks are four tracked notes`.

- **The minimum inter-onset interval is protecting against two different double-fires**: one
  attack's own energy ripple, and the release-then-re-attack of a fast repeated note. The paired
  tests keep it honest — the same signal must produce two onsets once the interval is relaxed, so
  the suppression test cannot pass merely because there was no second edge.

- **`YinNoteTracker.emitIfNew` reports when the note began, not the frame the pitch was confirmed**,
  and hands back the gap as `detectionDelayFrames`. That is this module's whole contribution to
  docs/spec.md I2: the note began at the attack and we merely learned what it was later. Reporting
  the gap per note lets the adapter correct each note instead of applying one average latency,
  which would leave a pitch-dependent timing bias that a session average cannot show.

- **The two emission paths are de-biased before they are compared, because they are quantised
  differently.** `atFrame = onset ?: runFirstFrame` looks symmetrical and is not. An onset frame is
  the *start* of the block the rise was heard in, so a struck note read ~3.6 ms early (measured -75
  to -249 frames over the sharp-attack fixture); a pitch-change frame is a window *centre*, and a
  window cannot report a new pitch until it is filled with it, so a slur read ~1000 frames (23 ms)
  late. Two paths that disagree by 25 ms about the same instant means the verdict a player gets
  depends on whether they tongued the note or slurred into it — the exact class of lie I2 exists to
  forbid. So each path subtracts its own known quantum, taken from the detector rather than from a
  constant: `onsetDetector.hopFrames / 2` centres the onset inside its block, and
  `pitchDetector.windowFrames / 2` is the analysis lag a window must accumulate before it can name a
  new pitch. Measured after the fix, over six change offsets: slur -232..+202 frames, strike
  +115..+350, and the two agree to within 384 frames (8.7 ms) — inside the stated bar of one pitch
  hop (512 frames, 11.6 ms), which is the coarser quantum and therefore the floor on agreement.
  `detectionDelayFrames` still ends at the confirming estimate, so `atFrame + detectionDelayFrames`
  remains the frame the pitch was actually known and the adapter's correction is unchanged in kind.

- **The price of that correction is paid by gradual attacks, and it is stated rather than hidden.**
  Centring inside the block removes the early bias for a sharp attack but adds half a block to a
  slow one, where the detector already fires late because the rise only becomes visible once it
  clears the silence floor. `four quiet notes with gradual attacks are four tracked notes` therefore
  states two blocks of tolerance, not one: a 50 ms ramp has no instant to be right about.

- **`rawFrame` is in the note diagnostic alongside `atFrame`.** Two paths with different corrections
  means a timing complaint from a report is unanswerable unless the line says which path emitted the
  note and what it was corrected from. `src=onset|pitchChange` plus `rawFrame=` makes the correction
  re-derivable after the fact, which is docs/spec.md I7 applied to the change above.

- **Why a low note is genuinely identified later than a high one, with a fixed window.** A window
  straddling the onset has `k` frames of silence at its head. The mismatch energy that survives
  into the difference function at the true lag is capped at `min(k, period)`, so the normalised
  value clears 0.15 while roughly `k < 270` at 82 Hz (period 535) but while roughly `k < 940` at
  1568 Hz (period 28). The high note is therefore identifiable from a window that is mostly
  silence and the low note is not: same window, pitch-dependent first valid frame. Measured
  2048-2560 frames of delay for low E against 1280-1536 for G6, across six onset alignments.

- **The stabilisation run, and why `startRun` runs again after every emission.** Requiring several
  consecutive estimates that agree within `stabilityCents` is what separates the note from the
  transient at its attack. Restarting the run after emitting is what stops the run's mean drifting
  across the tolerance band and re-emitting the same note: every member is within `stabilityCents`
  of the run's first estimate, so two successive means can differ by twice that, which would clear
  `pitchChangeCents` on its own.

- **The no-onset pitch-change rule is what makes legato audible at all.** A slurred line on a wind
  instrument or a voice has no energy edge at the note change, so without it the second note of a
  slur simply does not exist. It fires only past `pitchChangeCents` (90, nearly a semitone), which
  is the same number that keeps vibrato at +/- 30 cents as one note rather than a tremolo of them.

- **Estimates whose centre precedes a pending onset are dropped.** A window centre lags the stream
  head by half a window, so inside a single push an onset can be reported before estimates that
  belong to the *previous* note. `onsetGraceFrames` then allows for the onset frame itself being
  block-quantised early, so a genuinely-new-note estimate arriving just before it is not lost.

- **`confirmWithinFrames`** abandons an onset that never got a stable pitch — a thud, a key click,
  a chord — instead of leaving it pending, so it cannot later be credited to an unrelated note
  played seconds afterwards. The abandonment is logged, because "nothing happened" is the hardest
  thing to debug from a report.

## :core:practice

- **`TempoConductor.nanosFor`/`ticksAt` are absolute, not relative.** They map a musical
  position to a reading of the same monotonic clock `position()` samples, which is what lets a
  `PlayedNote.atNanos` (stamped by the input device) be compared with an expected onset without
  anyone rebasing anything. The consequence is that a pause *moves* the wall time of every later
  position, because after a five-second pause bar 3 genuinely happens five seconds later. So
  nothing may cache the result of `nanosFor` across a pause — `WindowedJudge` deliberately asks
  per call rather than precomputing onsets in `begin`, and `TempoConductorTest` asserts the shift
  so a future optimisation that caches it fails a test instead of silently mis-judging.

- **`TempoTickMap` divides in two steps** (`minutes` then `remainder`) rather than computing
  `ticks * 60e9 / ticksPerMinute` directly. The direct form overflows `Long` for a long piece at
  a slow tempo, and the two-step form is exact with both intermediates bounded. `ticksOfNanos`
  rounds to nearest while `nanosOfTicks` truncates, which is what makes the pair exact inverses:
  the truncation error is under a nanosecond and a tick is ~50µs, so the rounding always
  recovers the original tick.

- **`TempoConductor` keeps the tempo map as a list of legs, one per pause, not as a single moving
  origin.** The first version shifted one `zeroNanos` on every resume, so a pause moved the wall
  time of *every* position — including positions already played. Judging a session that contained
  a pause therefore disagreed with a report of itself: notes played before the pause were suddenly
  seventeen seconds early, so every `Correct` became an `Extra` plus a `Missed`. A leg records
  "from this position onward, position zero sits here", so a pause moves only what is still to
  come and the past keeps the moment it actually happened — which is what makes a verdict
  re-derivable later, i.e. spec I2. One list serves both the live transport and
  `timingSnapshot()`, so the two cannot drift apart.

- **`legAt` compares with `<`, not `<=`.** A pause landing exactly on a note's onset is ambiguous:
  either that note was played and the pause followed, or the pause came first. `<` protects the
  first reading, and that is the deliberate choice — a note already judged must never be re-timed,
  whereas a note not yet played is about to be judged live anyway. `pause()` freezes at whatever
  position was sampled, so exact equality is a test-only coincidence in practice.

- **`ticksAt` clamps to the next leg's start, and the live conductor clamps again while paused.**
  Wall time during a pause maps to one single position, so the mapping is deliberately not
  injective there; without the clamp a fifteen-second pause would report the music having run on
  fifteen seconds. `timingSnapshot()` is left unclamped because the judge asks it for positions
  past the end of the piece when closing the last note's window.

- **Count-in is tracked as `countInEndsAt`, not as "position is negative".** Negative-during-
  count-in is only true when starting from bar 1; `start(beat(8))` with a two-beat count-in
  begins at beat 6, where the count-in is entirely in positive territory. Testing against the
  start position instead of zero makes resuming mid-piece behave identically to starting fresh,
  which matters because that is the common case once a piece is being practised in sections.

- **The Conductor knows nothing about input latency any more.** It used to hold one
  `InputLatency` and expose a shifted `inputTiming`, which double-counted against adapters that
  already corrected their own timestamps, and could not have been right anyway with two sources
  attached — latency belongs to a device, and the Conductor deliberately cannot tell which device
  a note came from. Correction now happens once, in the adapter, so a `PlayedNote.atNanos` is
  already true time by the time anything downstream sees it.

- **`TempoConductor.stop()` goes to `Finished` and freezes where it got to** rather than
  resetting to zero. Freezing keeps "how far did he get before stopping" answerable from the
  conductor, and it is the only thing that makes `TransportState.Finished` reachable — `Idle`
  then means "never started", which is a genuinely different state.

- **`KeyboardTapSource` is a `Channel`, not a `MutableSharedFlow`.** A shared flow with `replay =
  0` **discards** an emission when nothing is collecting yet, and `tryEmit` still returns true —
  so every tap between the screen loading and the session's collector attaching was lost *and
  counted as delivered*, which surfaces later as a `Missed` for a note Dewi actually played, with
  a drop counter swearing nothing was dropped. That is the worst failure this app has, because it
  teaches. A channel is a queue: it holds taps until someone reads them, and `trySend` fails
  honestly when the buffer is genuinely full. The cost is that `notes()` is single-consumer, which
  is what the one input seam wants anyway — two collectors would each judge a subset. The buffer
  is 256 so a two-hand trill cannot reach it, and `notesDropped` exists so a diagnostics report
  can prove the buffer was never the reason a note went unjudged.

- **`WindowedJudge` prefers an exact pitch match over a nearer wrong-pitch one.** Playing the
  right note slightly late and playing the wrong note on time are different mistakes with
  different remedies, and picking purely by nearest onset would report the first as the second.
  The tie-break after that is the note index, so the choice is deterministic and a report can be
  re-judged to the same verdicts.

- **`advanceTime` is the only producer of `Missed`, and it uses a strict `>`.** An absence never
  arrives as an event, so it can only be found by sampling — the same lesson as Totum's stall
  watchdog that collected a `StateFlow` and could never fire. The comparison is strict so a note
  sitting exactly on the edge of its window is still playable; `finish` deliberately does *not*
  settle anything, because a note whose window never closed was not missed, the session was
  stopped, and calling that a miss would be the app inventing a fault.

- **`judgeAll` is a fold over `advance`/`advanceTime`, interleaved as the UI drives them.** It
  ticks time to each played note's own timestamp before folding that note in, which is exactly
  what the live path does, so the two cannot disagree. `WindowedJudgeTest` asserts that directly
  by running a simulated frame clock and comparing the judgement lists — the DRY law made
  executable, and the thing that stops the live verdicts and the final summary telling Dewi two
  different stories.

- **The matching window is a fraction of a beat, clamped, and asked per note.** A fixed 400ms is
  wider than a whole beat above 150bpm, so a wrong note played dead on time fell inside the *next*
  written note's window, matched it on pitch, and came back as "the right note, 288ms early" —
  wrong pitch, wrong note, wrong fault, all at once. `Windows.at(onset)` measures half a quarter
  note through the timing map itself, so it follows whatever tempo (or tempo map) the session is
  actually running, and clamps into 120–400ms: without a floor the window vanishes at extreme
  tempi, and without a ceiling a 30bpm exercise would match a note four seconds out. Measuring
  through the map rather than from a tempo number is also what makes a pause harmless — the
  ceiling bounds any pause that happens to fall inside the measured interval.

- **The on-time band stays absolute while the matching window scales.** They answer different
  questions: how precisely a human can place a note is a property of the human, whereas which
  written note a played note *belongs to* is a question about the music's own spacing.

- **`skillsOfNote` is a constructor parameter, not a `ScoreSkills` dependency.** It keeps the
  judge reproducible from a report (skills can be replayed as data) and keeps `:core:practice`
  from depending on a sibling module's implementation. `:app` wires `ScoreSkills::skillsOf`.

- **`accepts` asks `Score.firstPolyphonicMeasure()` and owns no polyphony test of its own.** The
  judge used to look for *simultaneous onsets*, which declares the commonest piano texture alive
  — a held left-hand note under a moving right hand — monophonic, waves it onto a mic that can
  follow one line, and mis-scores it silently. That is precisely what spec I3 exists to stop, and
  the two-line fix is only half of it: the definition now lives once, on `Score`, in terms of
  overlap, so the refusal gate, `ScoreSummary` and the scheduler cannot answer the same question
  three ways.

- **The scheduler's update rule, in full.** A session's outcome for a skill is *clean* when its
  accuracy is ≥ 0.8. Clean: `strength += (1 - strength) × 0.4` (asymptotic, so it never claims
  certainty), `repetition += 1`, and the next interval is `4h × 2^(repetition - 1)` capped at six
  doublings (~11 days). Lapse: `strength ×= 0.35` (a hard drop, because a skill you just failed is
  not nearly-learnt), due again in ten minutes, and **`repetition` back to zero**. `attempts` and
  `lapses` stay lifetime totals and count **sessions**, not notes — counting notes would send the
  interval to the cap after a single bar.

- **`repetition` is a stored field because the ladder rung cannot be derived.** The first rule
  used `attempts - lapses`, so a lapse stepped the exponent down by one instead of resetting it:
  a mature skill that had just been failed was pushed roughly ten days out by a single good
  session, which is the app hiding the thing Dewi had got wrong — spec I5 inverted. Lifetime
  totals cannot express "clean sessions *since* the last lapse", so the rung is its own field.
  It defaults to 0, which is the safe direction: a state that arrives without one is merely
  reviewed too often, never too rarely.

- **An outcome with zero attempts is not evidence and is skipped entirely.** `SkillOutcome`
  defines accuracy as 0.0 when there were no attempts, so folding it in would look exactly like
  a lapse and would punish a skill for not having been tested.

- **`next()` targets *due* skills when any are due, and only otherwise the weakest overall.**
  `weakest()` keeps its documented due-first-then-strength order for the UI, but selection uses
  the due pool, so a mastered skill that merely happens to be in the top five does not end up in
  a choice's `targeting` set and dilute what the screen says it is drilling.

- **Suitability counts *new* skills, excluding the targets.** The first rule tried was "at most
  half the piece's skills may be unfamiliar", which rejected precisely the piece that drills the
  weak skill — the weak skill is the reason to play it. What actually makes a piece wildly beyond
  someone is the *other* things it demands at once, so the gate is: at most two skills that are
  neither a target nor already familiar, plus a hard gate that polyphonic material needs
  `HandIndependence` strength ≥ 0.5. That second one is the honest version of the ladder: with no
  two-hand history, Bach is refused and a generated exercise is offered instead.

- **Pieces are ranked by the worst target they cover, then by coverage count.** Ranking by
  coverage alone treats all targets as equal, so a piece covering one mastered-ish skill could
  beat the piece covering the skill that keeps failing. Final tie-breaks are new-skill count,
  bar count and then `ScoreId`, so the choice is reproducible from the same inputs regardless of
  the order the corpus was listed in — `seed` is spent on generated material, not on shuffling
  pieces, because a scheduler nobody can predict is a scheduler nobody can trust.

- **`specTargeting` is injected and has no default.** Turning "bass-clef leger lines are weak"
  into a `DifficultySpec` is `ExerciseGenerator.specTargeting`'s job, but the contract puts the
  finished spec inside `PracticeChoice.Generated`, so the scheduler needs one. It used to carry
  its own copy as the *default*, which meant production silently ran the second implementation:
  the two disagreed by nearly three octaves on leger-line targeting, so the scheduler promised a
  drill the generator did not write. A required parameter makes the wiring visible at every call
  site and leaves exactly one implementation alive.

- **`DefaultBase`'s range is the treble staff itself**, from bottom line to top line, computed
  through `StaffGeometry` rather than by counting semitones here — one treble staff in C major,
  four bars, quarters and halves, leaps of up to four semitones at 60bpm, deliberately the bottom
  rung. It is a starting point for targeting to move, not a second band-to-range mapping.

- **`next()` is given the input's `Polyphony` and applies it to generated material too.** The
  refusal gate was on corpus pieces only, so with hand-independence weakest the scheduler
  generated a two-hand exercise, handed it to a mic, and its own judge refused it — the app
  proposing work it will not mark. Mono inputs now drop `HandIndependence` from the target list
  and force `bothHandsActive = false`, which is the same honest answer the refusal gives, arrived
  at one step earlier.

- **`judgeAll` takes the source and returns a `JudgeOutcome`, so refusal cannot be skipped.**
  When it took only a score and returned a bare `SessionResult`, `accepts` was advisory: a caller
  who forgot it got a confident percentage for a pairing the judge would have refused, which is
  the only route around spec I3. Asking for the `AnswerSource` makes the check part of the call
  rather than a convention.

- **`reportFrameDrift` detects dropped audio but deliberately does not try to correct it.**
  `AudioRecord.getTimestamp`'s `framePosition` counts frames the device *captured*; our counter
  sums frames we *read*. On an overrun the device's number runs ahead, and the gap is real audio
  that no longer exists — so a frame index we hand the tracker no longer means the device frame
  of the same number, and there is no way to know where in the stream the gap fell. Correction is
  therefore impossible and detection is the only honest option: the worst drift is logged as an
  event (once, on each new worst case) and every occurrence is counted, so a report can say "the
  timestamps on this route were off by up to N frames" instead of quietly presenting them as good.

- **`ClickMetronome.released` makes a use-after-release a logged refusal, not a crash.**
  `release()` shuts down the click executor, so a later `start()` followed by `onPosition` would
  hit a `RejectedExecutionException` from a frame callback. Refusing in `start()` with a reason
  keeps the failure diagnosable and in one place.

- **`playChord`'s `equalPowerAmplitude` divides by `sqrt(voiceCount)`, not by the count.** Summing
  n voices at full amplitude clips; dividing by n makes a five-note chord audibly quieter than a
  single note, which defeats the purpose of a reference tone. Dividing by the square root keeps
  the perceived loudness roughly constant, since power rather than amplitude is what adds when
  uncorrelated partials sum.

## :core:score

- **`StaffGeometry` exists so nothing branches per clef.** A staff is nine diatonic positions
  (bottom line 0 … top line 8) measured from `Clef.referenceDiatonicIndex`, so band, leger-line
  count and every targeting range are arithmetic on one integer. `bandOf` splits the nine
  in-staff positions three ways by division rather than by a `when` over `Clef`, which is the
  concrete form the twin laws take here: adding a clef is one enum entry and nothing else.

- **`DerivedScoreSkills.legerLines` subtracts `below` from `above`.** Only one of the two can ever
  be positive, so the difference carries both the distance outside the staff and the side, and
  halving it works because a leger line sits every second diatonic step. This is why middle C is
  one leger line in *both* clefs — `-1` in treble, `+1` in bass — with no per-clef code to keep in
  agreement. `SkillTag.LegerLines.above` is that sign, and `count` its magnitude.

- **`SkillTag.Accidental` fires only when the written alteration differs from the key.** An F#
  in G major is not an accidental to read; it is the key signature doing its job, and tagging it
  would debit a skill Dewi has not been tested on. `KeySignatureAlterations` is the single place
  that mapping lives, shared by the skill deriver and the generator's scale ladder so a
  generated exercise and its own difficulty rating cannot disagree.

- **`ScoreContext.bothHandsSound` uses overlap, not onset.** A left-hand note held under a
  right-hand run is hand independence even though the left hand attacks nothing in that bar, so
  the test is "does a note from each staff sound anywhere inside this bar", memoised per measure
  because `skillsOf(score)` would otherwise be quadratic. The bar's end is the *next* measure's
  start where there is one, not `start + time.measureTicks`: an anacrusis is shorter than its time
  signature claims, and using the nominal length let a one-beat pickup reach into the next bar and
  borrow a hand that had not entered yet.

- **`parseXmlDocument` sets no optional parser features except two guarded ones.** This is the
  Totum RSS lesson, exactly: Android's `DocumentBuilderFactory` throws on bean-property toggles
  the desktop JVM accepts, so a JVM-green parser can still fail on device. `isValidating` and the
  external-DTD feature are wrapped in `runCatching`; the real defence is the `EntityResolver`
  returning an empty source, which is plain SAX API available everywhere and which also stops a
  real-world file's `<!DOCTYPE … partwise.dtd>` reaching for a network the app has no permission
  to use.

- **The parser's element handling is a whitelist, and everything else becomes a `Dropped`.**
  Silence is the failure mode here (docs/spec.md, *the MusicXML subset is a subset*): a file that
  parses to nearly the right thing teaches wrong notes. `stem`, `beam`, `accidental` and
  `notations/tuplet` are the only silent omissions, because `:core:notation` re-derives all four
  from the model and reading them could only introduce disagreement.

- **`<type>` wins over `<duration>`, and a disagreement is recorded.** The written value is what
  gets engraved and what the cursor advances by, so taking it as the truth keeps
  `onset + duration.ticks` chaining exactly across a whole part. When the file's own
  `<duration>` disagrees the note is still read, but the mismatch is dropped-with-detail rather
  than silently reconciled — the two numbers disagreeing is a fact about the file that Dewi may
  want to go and look at.

- **A duration that does not scale to a whole tick is dropped rather than rounded.** 10080 ticks
  per quarter divides by every division count real MusicXML uses, so a remainder means the file
  is asking for something this app cannot represent. Rounding it would put every later note in
  the bar a fraction out and the error would accumulate silently, which is precisely the class of
  bug integer ticks exist to prevent.

- **`Measure.index` is 0-based; every human-facing bar number comes from `Measure.number`.**
  `index` is a list index, so it is what code indexes `Score.measures` with. `Dropped.measure`,
  `Score.firstPolyphonicMeasure()` and anything rendered are 1-based, because a human reads them
  against a printed page. `Measure.numberOf(index)` is the single `+ 1` in the module — the parser
  calls it to stamp `Dropped.measure`, `Measure.number` calls it, and nothing else adds one — so
  the two conventions can only be confused in one place. An off-by-one here sends Dewi to the
  wrong bar, which is worse than reporting no bar at all.

- **`BarFill` precomputes which remainders can still finish a bar exactly.** The generator only
  ever picks from choices that leave a reachable remainder, which is what makes "every bar sums
  to exactly `measureTicks`" true by construction rather than by luck. The table is indexed in
  units of the gcd of the candidate lengths and the bar, keeping it a few hundred entries at
  worst. When no combination fits, `generate` refuses loudly: a spec asking for whole notes in
  3/4 is a caller bug, and a short bar would be inherited by the layout engine, the judge and
  the scroll offset alike.

- **Tuplets are generated as whole groups of three.** Offering a single triplet note as a choice
  would fill bars exactly and still produce unreadable rhythm, because nothing would keep the
  three members adjacent. One choice worth three notes is the smallest change that makes a
  generated triplet a real triplet.

- **`MelodyWalker` picks its next note by weighted scale-step distance.** Working in ladder
  indices (the pitches of the key inside the staff's range) rather than semitones is what makes
  "mostly stepwise" mean musically stepwise, and filtering candidates by the actual semitone
  distance is what makes `maxLeapSemitones` a guarantee instead of a tendency. Rest bars take
  the longest value that fits and consume no randomness, so a hands-separate exercise reads as
  plain whole-bar rests.

- **`allowedAlterations` gates *extra* chromatic alterations, not the key's own.** The scale
  ladder always carries the alteration the key signature implies; `allowedAlterations` says
  which further accidentals may be written on top. That is what makes
  `specTargeting(Accidental(Sharp))` mean "drill sharps" rather than "permit the sharps you
  already had", and it keeps `Accidental` tagging consistent with the skill deriver.

- **Leger-line targeting takes its direction from the tag, not from the clef.** `withLegerLines`
  is handed `above` and builds the step window on that side. It used to infer the side by
  comparing the clef's centre against middle C, which meant "two leger lines above the bass staff"
  — the middle-C region every pianist actually struggles with — generated notes two leger lines
  *below* it instead. The window deliberately spans one leger line less than the target through
  one more, so the exercise contains the count being drilled plus enough context to be readable.

- **`withRhythmFigure` widens the note vocabulary when one value cannot fill the bar.** A whole
  note cannot fill 3/4, and narrowing to it alone would either short the bar or throw. Adding
  the shortest fallback values until the bar is fillable keeps the targeted figure dominant and
  the bar exact, which is the honest trade.

- **The corpus is three hand-authored excerpts, each asserted to parse with an empty `dropped`
  list.** That test is the parser's real contract: a piece shipped with the app must round-trip
  through the subset with nothing lost, so any future parser change that starts dropping
  something these files use fails immediately. Left hands are deliberately simplified single
  lines; `CorpusPiece.source` records the work and why it is public domain, and `licence` covers
  the engraving separately because those are two different claims.

## :core:notation

- **`BravuraGlyphMetrics` keeps SMuFL's upward y; the engine flips it.** Bravura publishes
  bounding boxes and anchors with y increasing *upwards* (hence `bBoxSW`/`bBoxNE`), while
  `Layout.kt` fixes y as increasing downwards. Converting inside the metrics class would make
  `GlyphBox.height` negative and silently disagree with the file it came from, so the flip happens
  at one place instead: `GlyphMetrics.anchorDown`. Anything reading a bbox directly (glyph extents
  in `fitVertically`) subtracts, for the same reason.

- **`BravuraGlyphMetrics.from` validates every `SmuflGlyph` up front.** A glyph missing a
  codepoint, advance or box would otherwise surface as a box character or a note at x=0 on Dewi's
  phone, a week later, with nothing in the report to explain it. Failing at construction with the
  glyph names in the message turns a rendering mystery into a startup error. `engravingDefaults`
  is equally strict: a missing key means the staff would be drawn with numbers we invented, which
  is exactly what reading the font's metadata is meant to prevent.

- **`engravingDefaults` is parsed as `Map<String, JsonElement>`, not `Map<String, Double>`.**
  `textFontFamily` is an array of strings, so a typed map fails to deserialise the whole document.
  Reading each key as a `JsonPrimitive` and asking for a double keeps the parse tolerant of
  whatever else the font ships while still refusing a non-numeric value for a key we use.

- **`clefBaselineDiatonicIndex` derives the clef's own line from its letter.** SMuFL clef glyphs
  are designed with the origin on the staff line they name: `gClef` on G, `fClef` on F, `cClef` on
  C. A `when (clef)` returning that line would be the pillar-split CLAUDE.md's first law forbids,
  so the letter comes from the clef's SMuFL name and the answer is the occurrence of that letter
  nearest the middle line. That tie-break matters: nine consecutive diatonic indices span the
  staff, so two letters appear twice, and picking the nearest to the middle is the choice that
  stays right for a clef added later.

- **`keySignatureRows` is one function parameterised by (letter, step, ceiling).** Sharps start on
  the highest F within the staff and climb a fifth at a time; flats start on the highest B and
  climb a fourth; both drop an octave when they would pass a ceiling one step (sharps) or a fourth
  (flats) above their first accidental. Those two triples reproduce the conventional treble, bass
  and alto placements exactly — including bass A♯ dropping to A2 while treble G♯ is allowed above
  the top line — without knowing which clef it is being asked about.

- **One `spacesPerQuarter` for the whole system, not one per bar.** Per-bar spacing would engrave
  better (a dense bar would only widen itself), but this app scrolls at tempo, and a per-bar scale
  makes the notation accelerate through dense bars and the playhead's velocity change mid-system.
  A single scale means constant velocity at constant tempo, and it makes `xOf`'s interpolation
  exactly equal to where the notes were placed rather than approximately. The cost is real and
  accepted: one bar of 32nds widens the entire system, so fewer bars fit on screen throughout.

- **`measureAnchors` is one anchor per measure, each carrying its own span, and x is piecewise.**
  There is no terminal anchor any more: `durationTicks` and `width` make the last bar
  interpolatable on its own, which is what the extra anchor used to be for. The reason for the
  change was a blocker rather than tidiness — a bar that opens with a clef or key change is wider
  than its duration implies, because that furniture is drawn before the first note, and a single
  global ticks-to-x scale cannot express that. It could only be made to fit by not drawing the
  furniture at all, which is what the code did: the clef in force placed the notes and was never
  drawn, so one written pitch appeared at two staff positions under an apparently unchanged treble
  clef. `MeasureSpacing.xOf` is now linear *within* a measure — `noteAreaX` plus the fraction of
  the bar elapsed, across `width` minus the furniture — and every bar butts against the next, so
  `x + width` of one anchor is exactly `x` of the following one.

- **The scroll's velocity is constant within a bar, not across a furniture change.** The single
  `spacesPerQuarter` still holds for note area of every bar, so a constant tempo scrolls at a
  constant rate; but a bar that draws a clef change is wider than its duration, so the playhead
  crosses that furniture in no musical time at all. That is inherent to drawing the change, and it
  is preferable to the alternative, which was lying about the pitch.

- **`MeasureFurniture` is one type for the system's opening and for every mid-piece change.** The
  header is simply measure 0's furniture, with every staff's clef in force plus the key and time
  signature; measure *n* draws only what differs from measure *n−1*. Widths come from the same
  object that emits the glyphs, so the space reserved and the space used cannot disagree — the
  failure mode the split version had. A key change cancels the outgoing signature's dropped
  accidentals with naturals before the new one, so a change to C major is visible rather than
  silent.

- **`fitVertically` shifts the finished system rather than guessing a margin.** Staff tops are
  laid out from y=0 and the whole system is translated afterwards by the measured extent of what
  was actually emitted — glyph ink from the font's bounding boxes, stems and beams by half their
  thickness. The alternative, reserving a fixed margin above the top staff, either wastes space or
  clips the one passage of high notes that exceeds it, and clipping is invisible in tests.

- **`tieLinksOf` resolves a tie continuation to the attack it continues, and to null when there
  is none.** A tie's second notehead is drawn but never attacked, so it has no entry of its own in
  `Score.attackedNotes`; pointing it at its origin is what makes a verdict colour both noteheads.
  Two things were wrong before. The match was keyed on `(staff, voice, pitch)`, so a tie crossing
  the staves — routine in piano writing — never matched; the staff is now the last thing compared,
  after voice, after sounding pitch. And an unmatched continuation fell back to index 0, so a
  verdict for the first note of the piece coloured an unrelated notehead somewhere else. There is
  no honest answer there, so `attackIndex` is now nullable and the layout says so. The links also
  carry `continuesFrom`, the note index the tie actually leaves, which is what draws the curve —
  chaining by voice key had the same cross-staff blindness.
  The consequence for tests: `attackedNotes[attackIndex].onset` equals the laid-out onset for
  every note *except* a tie continuation, where it is the earlier attack.

- **A chord is one stem, and `Chord` is the unit the engraver works in.** Notes sharing a staff,
  voice and onset are grouped before anything decides a direction, because deciding per notehead
  gave a chord one stem per note pointing in opposing directions on opposite sides. The direction
  comes from the note furthest from the middle line, so it is a property of the chord; the stem
  runs from the notehead at its origin (the bottom note when up, the top when down) and its length
  is measured from the notehead at its far end, which is the convention. Exactly one `LaidOutNote`
  in a chord carries the stem — the one at the origin, so "the stem starts on the notehead's own
  anchor" stays an exactly assertable claim — and the others carry null.

- **A second in a chord crosses the stem rather than overlapping.** Walking outwards from the
  stem's origin, a notehead half a space from the previous one is displaced by a notehead width
  less a stem thickness, unless the previous one already was. Accidentals are then placed left of
  the chord's *leftmost* head rather than each note's own, which stops a displaced note's
  accidental landing on top of its neighbour's notehead.

- **`LaidOutBeam`'s y is the beam's centre line.** The contract does not say which edge, and the
  centre is the only choice that stays symmetric when the renderer thickens the quadrilateral.
  Beamed stems therefore end on the centre line and are covered by the beam's own thickness.

- **Beam grouping is by beat, and the beat knows about compound metre.** A group never crosses a
  beat boundary, which is what makes 4/4 eighths beam in pairs and 16ths in fours. `beatTicks`
  treats a signature whose beats divide by three with a beat unit of an eighth or shorter as
  compound, so 6/8 groups in threes instead of the six separate eighths a naive beat-unit reading
  would produce. Runs are then capped at `LayoutStyle.beamNoteCountLimit`.

- **A beam's slope is clamped, then the whole line is pushed clear of the noteheads.** The slope
  comes from the first and last standard-length stem tips, limited to a quarter of the run and 1.5
  spaces, because a literal join of two distant noteheads produces a beam that looks like a
  mistake. The clamp can then leave a stem too short, so a second pass shifts the line until the
  worst stem is at least two spaces — that ordering matters, and doing it the other way round
  reintroduces the slope problem.

- **A tie's thickness comes from the font, like every other thickness.** `EngravingDefaults` now
  carries `tieMidpointThickness` and `slurMidpointThickness`, so the hardcoded 0.22 that used to
  shadow Bravura's own value is gone and a font version bump moves it.

- **A stem line sits exactly on the notehead's anchor, not half a stem-thickness inside it.**
  Engraving convention puts the stem's *edge* flush with the notehead, so the stroke could be
  offset by half its thickness. Keeping the line on the anchor instead makes "the stem starts where
  the font says it does" an exactly assertable claim, and leaves the renderer a 0.06-space overhang
  that is invisible at any real staff size.

- **The brace carries its own vertical scale, because the font's is four spaces and a grand staff
  is sixteen.** Bravura's `brace` is one em tall with its origin at the bottom, so leaving it at
  natural size drew a brace beside the lower staff rather than across the system. `LaidOutGlyph`
  gained `scaleY` for this one glyph: the layout computes `(bottom − top) / boxHeight` from the
  font's own bounding box and places the origin at `bottom + southWestY × scaleY`, which makes the
  stretched ink land exactly on the top and bottom staff lines. `fitVertically` multiplies glyph
  extents by `scaleY` for the same reason — otherwise the one stretched glyph is measured at its
  unstretched size.

- **`Layout.kt`'s `LayoutStyle` defaults and `Glyphs.kt`'s digit bound are named constants.** Both
  are contract files authored by the lead; detekt's `MagicNumber` rule flagged the literals, and
  the module's gate has to be green. Naming them changes no signature and no default value.

- **Known gaps, deliberately.** Two accidentals in one chord are not stacked into columns, so a
  cluster with several can overlap (each is placed left of the chord, at its own row); a mid-staff
  clef change is drawn full size rather than the conventional ~72%; rests in a second voice are not
  shifted off the middle line; and the system is one continuous line with no line breaking, which
  the scrolling design does not need. Secondary beams *were* checked rather than assumed: 16ths get
  one extra level and 32nds two, spaced by the font, a level present on a single column becomes a
  stub pointing into the group, and `BeamTest` now holds all three.

## :core:score — adversarial-review fixes

- **`widenUntilBarsFill` adds the *longest* value that fits the bar, not the shortest.** When a
  targeted rhythm figure cannot fill a bar alone (a half note in 3/4, a whole note in any bar
  shorter than four beats), the fallback vocabulary has to be widened or the bar comes out short.
  Ordering that search by ascending length reached for a 32nd first, which was exactly wrong twice
  over: a 3/4 "drill half notes" exercise came out as four halves against thirty-two 32nds, and a
  "drill whole notes" exercise contained ninety-six 32nds and no whole note at all. Candidates are
  now filtered to values that can physically fit the bar and tried longest-first, so the added
  vocabulary is the nearest readable neighbour of the figure being drilled.

- **`MelodyWalker` will not respell a note the key already contains.** `allowedAlterations` is a
  vocabulary of *extra* chromatic alterations, so applying one to any letter produced E♯ and B♯ in
  C major (56 of them across twelve seeds) and C♭, F♭ and G♭ alongside the key's own F♯ in G major.
  A written accidental is only kept when its sounding pitch class is not already a pitch of the
  key, which is the same "is this an accidental to read?" test `DerivedScoreSkills` applies — so
  the generator and the skill deriver still cannot disagree.

- **The starting tempo wins, and a `<direction>`'s `<sound tempo>` counts.** Real exporters write
  the tempo inside a `<direction>`, which this parser drops, so every downloaded file was falling
  back to the 90 bpm default while claiming to have read the piece — the worst shape of bug for an
  app whose whole loop is "scrolls at tempo". The tempo is now taken from a nested `<sound>` before
  the direction is dropped, and the *first* one found wins, because `Score.defaultTempoBpm` is the
  tempo the piece starts at and a later `rit.` must not overwrite it.

- **`scoreEventOrder` is one comparator.** The parser and the generator each had their own copy of
  the same three-key ordering; one canonical order is what lets the layout engine and the judge
  assume `Score.events` means the same thing whichever way the score came into existence.

- **`firstPolyphonicMeasure` walks *all* notes, including tied continuations.** The predicate asks
  what is *sounding*, and a tie is precisely the notation for "this sound continues". Reading
  `attackedNotes` — which drops continuations — ends a held note at the barline, so a left hand
  tied across the bar under a right hand entering on the next downbeat came out `Mono`: the exact
  texture docs/spec.md I3 exists to refuse, cleared for a microphone that can follow one line.
  `ScorePolyphonyTest` pins both that case and the plain held-note case, and both fail against the
  onset-based predicate the review condemned.

- **Comparing each note only with its immediate successor is sufficient, not a shortcut.** With the
  notes sorted by onset, if the next note starts at or after this one ends then so does every note
  after it, so a single `zipWithNext` pass finds an overlap if one exists — and because onsets are
  non-decreasing, the first pair it finds is also the *earliest* overlap. The nested loop this
  replaced had a second `if` that could never be false and a `break` on every path, so it was an
  O(n²) shape doing O(n) work while reading as though it did more.

- **`ScoreSkills.skillsOf` takes an index into `attackedNotes`, not a `Note`.** "The leap from the
  previous note" needs to know *which* occurrence this is. Locating it by scanning for an equal or
  identical note works only while the caller holds the very instance the score was built from — a
  note round-tripped through the database, or a piece that repeats a pitch at the same position in
  another voice, quietly picks the wrong predecessor and reports a leap that was never written. An
  out-of-range index is refused rather than answered, because a silently empty skill set would
  debit nothing and look exactly like a clean note.

- **`Corpus.parse` keys the score by `CorpusPiece.id`, not by its title.** The scheduler picks a
  piece by id and then loads it, so a `Score.id` made from the title could never be matched back to
  the shipped piece — and retitling a piece would silently make it a different one to the history
  already stored against it.

- **The parse and generate diagnostics lines carry `poly=` and `polyFromBar=`.** When mic mode
  refuses a piece, the report has to let a future session check the refusal was *right*; those two
  fields are what turn "PLAY IT refused" into a claim that can be argued with, and they are read
  from the same `Score.polyphony` the refusal gate uses, so the log cannot disagree with the
  decision it is explaining.

- **The judge is begun in `start()`, not in `load()`, and that ordering is load-bearing.** A
  `TempoConductor` that has not started yet has its single leg at `TempoLeg(0, 0)`, so
  `timingSnapshot()` returns a map whose origin is nanotime zero. Snapshotting at load time
  therefore converted every input's absolute timestamp into a position hours into the piece: on the
  emulator a tap at uptime 29,542 s landed at tick 555,868,801 in a 120,960-tick score, and every
  note was judged `Missed` while every tap became an `Extra`. Found on-device on 2026-08-07 by the
  diagnostics report, which is the whole argument for the report existing — the unit tests all
  passed, because in a test the conductor is always started before anything is judged.

- **A resume after a pause appends a leg the already-begun judge cannot see.** `timingSnapshot()`
  returns the legs as they stand, and `JudgeState` holds the one taken at `start()`. So notes played
  after a mid-session resume are currently measured against the pre-pause map and read late by the
  length of the pause. It is recorded in `docs/todos/` rather than silently accepted; the fix is a
  `retime(state, timing)` on `PerformanceJudge`, which keeps the fold's progress while swapping the
  map, and it needs a contract change so it is not being smuggled in here.
