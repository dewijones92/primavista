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
  `AudioTrack` and `AudioRecord` bridges are androidTest-only, and as of 2026-08-08 they have
  finally **been executed**: 22 instrumented tests green on an API 35 emulator. The assertions
  that matter came back the right way — the capture's timebase does reach `DeviceReported` and
  the player can report a playback anchor, so neither of the two named findings is live on that
  device. Both remain worth re-reading on Dewi's phone, because they are device facts rather
  than code facts.

- **A MODE_STATIC `AudioTrack` reports `STATE_NO_STATIC_DATA` until its buffer is written, and
  that one line made the metronome silent.** `ClickMetronome.staticTrack` checked
  `state != STATE_INITIALIZED` *before* the `write`, so every click track was released at
  construction, both tracks were null, and every beat was counted as `beatsWithNoTrack` — on
  every device, not just this one (`beat click track uninitialised state=2`, emulator API 35,
  2026-08-08). The order is now build → write the whole buffer → require `STATE_INITIALIZED`,
  which is the order MODE_STATIC actually documents, and each of the three failures returns its
  own reason so a report says which one fired.

- **The click stays MODE_STATIC rather than moving to the streaming mixer.** MODE_STREAM would
  also have fixed the silence, and it is the safer default for audio that is rewritten each
  time — but the click is not rewritten. It is one fixed 30ms buffer replayed on every beat, and
  a pre-loaded static buffer retriggered with `stop`/`reloadStaticData`/`play` has the lowest
  jitter available without an audio callback and costs no render thread between beats. Streaming
  it would mean either a second always-running renderer beside `AudioTrackTonePlayer`'s, or a
  start-up on every beat, and both are worse for a thing whose entire job is landing on time.
  The trade accepted is that MODE_STATIC has this initialisation order to get right, which is
  now held by an instrumented test rather than by memory.

- **`stateName` exists because `state=2` cost a session.** The old line called state 2
  "uninitialised", which is what `STATE_UNINITIALIZED` is called — except that constant is 0,
  and 2 is `STATE_NO_STATIC_DATA`. A report that names the wrong condition is worse than one
  that prints a bare number, because it sends the next session looking in the wrong place; it
  took reading `android.jar` to find that the log was the thing lying.

- **`framesHeard` is a field surfaced through `state`, not a `counted`.** It is the honest answer
  to "did anything actually come out", read from `AudioTrack.playbackHeadPosition` before each
  retrigger and once more at `release`, so a completed click contributes its whole 1440 frames.
  It was briefly a `counted`, which was a self-inflicted version of the bounded-buffer rule in
  `CLAUDE.md`: `RingBufferDiag.counted` flushes a running-total *event* once the pending
  increment reaches 250, and an increment of 1440 clears that on every single beat — one line per
  click, roughly 270 of a 600-entry buffer over a three-minute session. A monotonic total belongs
  in `state`, which is replaced in place and costs one line however long the session runs.
  `beatsPlayed` stays `counted` because it increments by one.

- **What the instrumented proof actually proves.** `framesHeard` says the audio device's playback
  head advanced through every frame of every click — the track was accepted, started, and
  consumed at the real-time rate. It does not prove a speaker moved; nothing available from here
  can. The emulator's own report after a four-beat bar reads
  `beatsPlayed: 4`, `heard=5760frames` (4 × 1440 at 48kHz) with no `beatsWithNoTrack`, and the
  running app's report after a ten-note exercise reads `beatsPlayed: 21` against the 21 beats it
  decided (`accents: 6` + `clicks: 15`), where the same report a pass earlier read
  `beatsWithNoTrack: 21`.

- **`grantRecordAudio` uses `executeShellCommand`, not a manifest entry.** RECORD_AUDIO is a
  runtime permission, so declaring it in the androidTest manifest grants nothing and the five
  capture tests failed with `capture refused: the microphone permission has not been granted` —
  which is the capture behaving correctly and the test harness being wrong. `GrantPermissionRule`
  would be the idiomatic fix but lives in `androidx.test:rules`, which this project does not
  depend on and which is not this module's to add; the instrumentation's own `pm grant` does the
  same job with what is already here. The stream is drained rather than closed immediately,
  because that is what waits for the command to finish.

- **`AudioTrackTonePlayer` does not share the metronome's fault, and that was checked rather than
  assumed.** It is MODE_STREAM, where the state after construction genuinely is
  `STATE_INITIALIZED`, and its five instrumented tests pass on the device — including
  `lastPlayback` returning a real anchor, which requires the track to have actually presented
  frames. Two smaller things were fixed while in there: the render loop now catches
  `IllegalStateException` so a track released underneath it logs and exits instead of killing the
  process from a background thread, and `release` says so when the renderer is still alive after
  its join rather than releasing the native track in silence.

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

- **The instrumented tests now run.** 50 of them passed on the api35test emulator (API 35) on
  2026-08-08, including the migration pair below. The earlier note here said they had only ever
  been compiled; that is no longer the state.

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

### Schema v2 — the spaced-repetition rung is stored (2026-08-08)

- **`SkillState.repetition` was built by `:core:practice` and thrown away by this module.** The
  mapper never wrote it and `skill_states` had no column, so every reload put a mature skill back
  on rung 0. It compiles, it never throws, and it quietly weakens spec I5 — the rung is *clean
  sessions since the last lapse*, which is exactly the thing that cannot be recovered from
  `attempts - lapses` (those are lifetime totals, and their difference sends a just-failed skill
  out roughly ten days after one good session). So: column, `MIGRATION 1→2`, both directions of
  the mapper, and `SkillStoreTest`'s stand-in rule now moves the rung so the store tests can
  actually see it travel.

- **The column declares `defaultValue = "0"` and the migration's `ALTER TABLE` says
  `DEFAULT 0`.** Room only compares a default when the *entity* names one, so the pair is not
  redundant: without the annotation a freshly-installed table and a migrated one would differ in
  SQL while both validating, and the exported schema would stop describing what is on disk.

- **`SchemaAndMigrationTest.theCurrentSchemaStillStoresTheSpacedRepetitionRung`** greps the
  exported JSON rather than the entity. It is the cheap JVM half: deleting the field would
  regenerate the schema and fail here in seconds, without a device.

- **The migration test builds the v1 database from the committed v1 schema JSON, and runs the
  real `PrimaVistaDatabase.open`.** Not `MigrationTestHelper`: `room-testing` is not in the
  version catalogue, and `schemas/<version>.json` already carries every `createSql`, the indices
  and the `room_master_table` setup queries, so `SchemaAssets.createDatabaseAtVersion` is about
  twenty lines and needs no new dependency. Going through the shipping `open` rather than a test
  builder is the point: it proves the migration list the *app* uses carries the row forward.
  Verified negatively on 2026-08-08 — with `PrimaVistaMigrations.ALL` emptied, the test fails.

- **`androidTest` assets now include the schema directory, via the variant API.**
  `android.sourceSets["androidTest"].assets.srcDir(...)` is the AGP 8 spelling and throws
  `ClassCastException: DefaultAndroidLibrarySourceSet_Decorated cannot be cast to
  AndroidLibrarySourceSet` under AGP 9.3. The supported route is
  `androidComponents { onVariants { it.deviceTests.values.forEach { t ->
  t.sources.assets?.addStaticSourceDirectory("schemas") } } }` — note the path is **relative to
  the project directory** and the directory must already exist. Two consequences worth knowing:
  the merge task has no dependency on KSP, so what is packaged is whatever is *committed* in
  `schemas/` (which is what a migration test wants — history, not the current build's output);
  and a schema generated by the same build lands in the APK only on the next run.

### Reviewed and left alone, or knowingly limited

- **An unreadable row is now named once and counted every time.** The rows are deliberately left
  on disk, so they come back on *every* read — and `states()` runs on every session start. The
  previous code logged a line each time, which is one discarded skill key emptying a 600-entry
  ring buffer, the exact failure CLAUDE.md's diagnostics rule names. `UnreadableRowLog` emits the
  reason the first time it sees an id and `Diag.counted` thereafter, so a report says both what
  was lost and how often it is being re-read. There is still no purge, and that stays deliberate:
  a later build that learns to read a v1 key can recover the strength, and a row costs nothing.

- **`DifficultyCodec` refuses a malformed or repeated field, and still ignores an unknown one.**
  Skipping anything that was not `name=value` meant a corrupt string could decode to a *valid but
  different* spec — a replay that silently disagrees with the report it came from. An unknown
  *name* is still ignored, because that is the forward-compatibility the named-field format exists
  for. `perStaff` refuses a staff named twice for the same reason.

- **A lost origin says why.** `DifficultyCodec.read` returns `SpecReading` and `SessionEntity`
  reads through `OriginReading`, so `originDescriptor` carries ` why=…` alongside the raw row.
  It previously said only that the origin was gone, which is the "3 rows unreadable" shape this
  module had already rejected everywhere else.

- **`readOrRefuse` rethrows `CancellationException` instead of reporting it as a corrupt file.**
  `runCatching` catches `Throwable`, and every caller is a `suspend` read driven from a Compose
  `produceState` that is cancelled the moment Dewi leaves the tab. So swiping off Progress logged
  `the 30 most recent sessions could not be read at all; rows left on disk, nothing shown:
  StandaloneCoroutine was cancelled` — a false statement about his practice history in the one
  evidence a report will ever have, and the "two different situations must never produce the same
  line" failure from `CLAUDE.md`. It also swallowed the cancellation, so the coroutine finished
  normally instead of unwinding. `ReadOrRefuseTest` holds both halves: a genuine failure still
  refuses with its reason, and a cancelled read says nothing at all.

- **`PrimaVistaConverters` throws on an enum name it does not recognise, and it took the whole app
  down — observed, not theorised.** Seeding a session row with `latencyProvenance='Unmeasured'`
  (not a `Provenance` constant) crashed the Progress tab on the emulator on 2026-08-08:
  `IllegalArgumentException: No enum constant …Provenance.Unmeasured` thrown from
  `PrimaVistaConverters.toProvenance` inside `SessionDao_Impl.recent`. A `@TypeConverter` runs in
  the cursor loop, so the failure is per-**query**, not per-row, and there is no way to make it a
  reading: a converter for a non-null column has nothing to return but a value. Reaching it needs
  a downgrade after a constant is added, or a corrupted file — and Dewi sideloads through
  Obtainium, so a downgrade is not hypothetical.

  What is fixed here: `Diag.readOrRefuse` wraps every bulk read, so the query yields an empty list
  and a line naming the offending value instead of an uncaught exception. That is deliberately a
  *worse* answer than a per-row skip and a better one than a crash loop the user cannot leave —
  the file is untouched, and the reason is in the report. `SessionStoreTest`'s
  "an enum name this build does not know refuses the query…" holds it.

  What is **not** fixed, and needs a cross-module decision: making the enum columns nullable so a
  bad name becomes one skipped row. That means `SessionEntity.polyphony`,
  `SessionEntity.latencyProvenance`, `RepertoireEntity.polyphony` and
  `AudioRouteLatencyEntity.provenance` all become nullable — a NOT NULL relaxation SQLite can only
  do by rebuilding three tables — and `StoredSession`/`ScoreSummary` would need an answer for a
  session whose polyphony is unreadable. `:app` reads `AudioRouteLatencyEntity` directly, so it is
  not a change this module can make alone.

### Refusal is a value now (`StoredReading`, 2026-08-08)

- **The previous pass wrapped the reads and then threw the answer away.** `readOrRefuse` returned
  the caller's fallback, which for every bulk read was `emptyList()` — so a single corrupt row made
  the Progress screen say "nothing practised yet" over sessions sitting on disk. That is the app
  lying about Dewi's practice, which outranks every other consideration here, and it is worse than
  the crash it replaced because a crash is at least visible. So `readOrRefuse` now returns
  `StoredReading<T>` — `Readable(value)` or `Unreadable(what, reason)` — and every bulk read on
  `SessionStore`, `RepertoireStore` and `SettingsStore` returns one. It is the third sealed result
  in this module rather than a fourth shape, sitting alongside `DatabaseOpening` and
  `SkillKeyReading` and reading the same way. The change is deliberately **source-breaking**: a
  caller that used to write `.orEmpty()` now fails to compile, which is the only way to be sure
  none of them keeps showing an empty screen over real practice.

- **`SettingsStore.latencies()` exists because `:app` was reading `AudioRouteLatencyDao.all()`
  directly**, and that is the query the enum-converter defect kills — same mechanism as the
  Progress crash of 2026-08-08, one tab across, in a path that ships today. There was no safe
  accessor to point it at, so the honest fix was to add one rather than to note it. `RouteLatency`
  is the domain shape (`AudioRoute`, `InputLatency`, when it was measured) so the screen never
  touches an entity again. `SettingsStoreTest.theRawDaoStillThrowsWhichIsWhyTheStoreAccessorExists`
  keeps the reason in the suite: it asserts the DAO path really does throw, so nobody deletes the
  accessor believing it was decoration.

- **`latency(route)` is a `StoredReading<InputLatency?>`, and the nesting is the point.**
  `Readable(null)` is "nobody has measured this route" and `Unreadable` is "a row exists and this
  build cannot read it". Collapsing them to null would have made an unreadable measurement read as
  an unmeasured one — the same class of claim as an assumed latency called measured, and the
  "two different situations must never produce the same line" failure from `CLAUDE.md`.

- **`RoomSkillStore.states()` still returns a plain list because `SkillStore` is a
  `:core:practice` port.** It is now wrapped like everything else, so an unreadable store degrades
  to no known skills instead of taking the screen down, and it logs *that consequence* ("every
  skill will look brand new") rather than only the read failure. `storedStates()` is the honest
  accessor beside it, for the Progress screen, which must be able to tell a first-run device from
  a refused read. When the port can change, `states()` should go.

- **`record()` must never fold onto a refused read.** Wrapping the read alone would have handed the
  update rule an empty list of prior states and then upserted beginners' figures over mature ones —
  destroying exactly the history docs/spec.md I4 exists to keep, silently, at the end of a good
  session. So the refusal wraps the *whole* `withTransaction`: the inner read still throws, the
  transaction rolls back, nothing is written, and the line says so.
  `aFoldThatFailsWritesNothingAndLeavesTheStoredStrengthsAlone` holds it.

- **A `CancellationException` is only silent when it is *our* cancellation.** Found by a test that
  closed the database and expected a refusal: `RoomDatabase.close()` cancels Room's own coroutine
  scope, so the in-flight DAO call fails with `JobCancellationException` — a `CancellationException`
  — while the caller's job is perfectly alive. The previous rule rethrew it unconditionally, which
  cancelled the caller instead, leaving a Compose `produceState` stuck on "Reading…" for ever with
  nothing in the log: the "silence is the hardest thing to debug" failure, arrived at from the fix
  for the opposite one. The rule is now `currentCoroutineContext().isActive`: our own cancellation
  (Dewi swiping off the tab) still says nothing, anyone else's is reported with its own wording.
  `readOrRefuse` had to become `suspend inline` to ask.

- **`UnreadableRowLog`'s dedupe set is bounded at 32 names.** The set existed to stop a repeated
  row emptying the ring buffer, and was itself unbounded — a file with a distinct corrupt id per
  row grows it on every `states()` call, which is the same class of problem one layer up. Past the
  limit, rows are still *counted* exactly and one line says naming has stopped, because a tally
  that quietly understates is worse than a shorter list of names.

- **`SettingsMappers.kt` is a split of `Mappers.kt`, not a second mapping layer.** Adding
  `toRouteLatency` took that file to detekt's 20-function ceiling; the settings and route-latency
  mappers are the coherent slice to move, and both files remain "the one documented place entities
  meet domain types".

- **Knowingly left alone: `settings()` and `observe()` are unwrapped.** `SettingsEntity` has no
  enum column, so no `@TypeConverter` can fail the cursor, and the only remaining way in is a
  database-level failure that `PrimaVistaDatabase.open` has already refused. If a column with a
  converter is ever added to that table, it needs the same treatment as the rest.

### Schema v3 — the journey: dated milestones, the placement read, the streak (2026-08-15)

- **There is no stored "current stage", and that is the central decision.** `:core:practice`'s
  `Curriculum.currentStage(states)` derives where Dewi stands from the skill states, so a column
  holding it would be a second answer to the same question — one that goes stale the moment a skill
  lapses, while looking authoritative. The first draft of this pass *did* store it (a `journey`
  singleton with `currentStageKey`); it was removed once `Curriculum` landed, because two answers is
  the `specTargeting` failure this repo has already paid for. `SchemaAndMigrationTest.theSchemaStores
  NoCurrentStageOfItsOwn` greps the exported schema so it cannot come back by accident.

- **So the two tables hold only what cannot be derived: dates, and the fact of the placement read.**
  `stage_progress` says when a stage was first reached and when it was first passed; `placement_reads`
  keeps every placement as an event. Skill states carry no history, so neither date is recoverable
  any other way — which is exactly the test of whether storing something is duplication.

- **`firstPassedAtEpochMillis` is a dated event, never a claim about today.** A stage is passed when
  its skills are solid, and skills lapse, so the column records the day the curriculum first said so.
  The path may draw "passed on 12 Aug" from it; whether it is passed *now* is `Curriculum.isPassed`.
  First-date-wins on the write for the same reason: restamping would destroy the only record of when
  the reading actually became solid.

- **A stage is stored as `StageId.number`, not as an invented key.** The first draft used an opaque
  `StageKey(String)` on the theory that storing a position is fragile — true, but the alternative is
  worse: `:app` would have to map `StageId` ↔ `StageKey` somewhere nobody owns, and two screens
  spelling that mapping differently is the same bug one layer up. The renumbering hazard is real and
  belongs to the curriculum (`StagedCurriculum` already requires stages to be numbered 1..n in
  order); inserting a stage mid-path would shift the meaning of stored rows *and* of every
  `StageId` in memory, so it is a curriculum-wide decision rather than a storage trick.

- **Never taken and unreadable are opposite statements about the placement read.**
  `PlacementReading` is `NeverTaken` | `Taken` | `Unreadable`, and the unreadable case still carries
  the date, because *that* part of the row reads perfectly — only the conclusion is lost. Collapsing
  them would offer the placement read again to someone who has already sat through it, which is the
  module's oldest bug (a refusal arriving as an absence) wearing a new hat.

- **`PlacementRecord.of(placement, …)` is the one place a `Placement` becomes a row.** It stores
  `probesTaken`, the *count* of seeded skills, and the placement's own `summary` line — not the
  seeded states themselves, which belong in `skill_states` and are already there. Note their design
  deliberately concludes no stage (`Placement` is "not a stage"), so nothing here records one either.

- **The streak is derived, and the fold is not ours.** `:core:practice`'s `Streak.of` is the single
  definition of days-and-runs; this module supplies the evidence and nothing else. A first draft of
  this pass shipped a second fold (`PracticeStreaks`) written minutes before `Streak` landed in the
  sibling module — two answers to "how many days in a row", which is precisely what the twin laws
  forbid. It was deleted rather than kept-and-documented.

- **A day counts when a note was actually played** — `startedAtWhereANoteWasPlayed` selects sessions
  having a verdict whose kind is not `missed`, passed the `VerdictKinds.MISSED` constant rather than
  a literal. Two things make the obvious version wrong: a session row is written at *pause* as well as
  at finish, so opening the app and quitting stores one; and a piece that scrolled past untouched is
  judged entirely `Missed`. Counting either would make the streak a measure of showing up, which
  docs/journey.md rules out in as many words. **Cross-module wrinkle worth settling:** `Streak.of`'s
  parameter is named `finishedSessionEpochMillis`, and what this store hands it is deliberately
  broader (paused-but-played counts) and narrower (finished-but-silent does not).

- **The streak query is immune to the `@TypeConverter` defect, and a test pins that.**
  `latencyProvenance` and friends fail a whole cursor on an unknown enum name (see the 2026-08-08
  note above); this query selects one INTEGER column and touches no converter, so the streak survives
  a file that refuses `recent()`. `anEnumNameThisBuildCannotReadRefusesTheHistoryButNotTheStreak`
  exists so a later `SELECT *` cannot quietly give that property away.

- **The streak's diagnostics line carries `now=` and `zone=`, not just the answer.** `current=3d`
  cannot be checked from a report without knowing what the app thought today was, and those two
  fields are what make the line re-derivable (docs/spec.md I7).

- **The zone is a parameter, not `ZoneId.systemDefault()`.** Doing the day arithmetic in SQL with
  `date(…, 'unixepoch', 'localtime')` would bake the device's timezone into the query and make two
  adjacent reads disagree after a flight.

- **Migration 2→3 creates two tables and touches nothing else**, so every session, verdict, skill and
  settings row carries over by construction. `JourneyMigrationTest` runs the shipping
  `PrimaVistaDatabase.open` against a v2 file *and* a v1 file, the second proving the two-migration
  chain a phone that skipped an update would take. Verified negatively on 2026-08-15: with
  `AddJourney` removed from `PrimaVistaMigrations.ALL`, all three tests fail with "A migration from 2
  to 3 was required but not found", so the gate is real rather than assumed.

- **KSP will not re-export the schema if the compile task comes out of the build cache.** Deleting
  `schemas/3.json` and rebuilding left it missing and failed `SchemaAndMigrationTest`, because the
  schema directory is outside `build/` and is not a tracked output.
  `:core:database:kspDebugKotlin --rerun` is the fix.

- **`openRealDatabase` and `use` moved into `TestFixtures`.** The second migration test would have
  been their second copy, which is the duplication the DRY law names outright.

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

- **`legAt` compares with `<`, not `<=`.** A position the transport actually reached happened when
  it was reached, so the wall time of the exact pause position belongs to the leg that was running,
  not to the one that starts on resume. The second half of this entry used to read *"a note not yet
  played is about to be judged live anyway"*, and that was **wrong** — it is the assumption that
  shipped the defect below, where a note still inside its window at the moment of the pause was
  Missed on the first tick after the resume. The judge no longer depends on this comparison at all:
  it measures in music time (`elapsedNanosAt`), which knows nothing about legs. `<` now decides only
  what `nanosFor` reports for one exact position, which is a question about the transport rather
  than about a verdict.

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

- **`retime` exists because the judge's map is a snapshot and a pause appends to the real one.**
  `begin` takes an immutable `TickTiming` on purpose — handing over the live transport made a
  session containing a pause re-judge differently from a report of itself. But the snapshot the
  view model takes at `start()` predates every later leg, so without `retime` each note after a
  resume was measured against the pre-pause map and read late by the whole length of the pause:
  the app blaming Dewi for his own phone call, which is spec I2 failing in the direction that
  teaches the wrong thing. `retime` keeps everything the fold has settled and swaps in the newer
  map, so replay is untouched (a stored session replays from its final snapshot, which already
  contains every leg) while the live path sees the pauses that have already happened. It must be
  called on **every** resume, not just the first — `TempoConductorTest`'s two-pause test fails if
  the second one is skipped. A mid-session tempo change would need the same call, and today has no
  route to happen: `AppContainer` builds one Conductor per score at the score's own tempo.

- **`retime` is a one-field copy, and that is the point.** `Windows` used to capture its own
  reference to the timing map, so the fold held the same decision twice and swapping `Fold.timing`
  alone would have left every window width measured against the stale map — a `retime` that looked
  right and half-worked. `Windows` now depends only on the tolerances and is handed the map per
  call, so the fold has exactly one timing field and there is nothing else to forget. The judge
  cannot check that a caller's newer map agrees with the old one about the past; `TempoTimeline`'s
  legs guarantee it, and that guarantee is the reason a snapshot may be replaced at all.

### The judge measures in music time, not wall time (2026-08-08)

- **`retime` alone was half a fix, and the missing half debited Dewi for a note he got right.**
  Swapping the map re-times every note whose onset is strictly *after* the pause position, because
  that is what `legAt` moves. A note already overdue but **still inside its matching window** when
  the phone rang keeps its pre-pause wall time, so on resume `advanceTime` closed it as `Missed` on
  the very first tick, and the note Dewi then played answered to nothing and came back as `Extra`.
  Reproduced exactly: 3 correct of 4, one `Missed` plus one `Extra`, for a note played 20ms after
  picking the piece back up. That is the app inventing two faults out of its own bookkeeping, which
  is docs/spec.md I2 failing in the direction that teaches.

- **The decision, and why it is this one.** A matching window is a promise about how much *music*
  may pass before a note counts as missed. Wall time spent not making music is not music, so the
  whole pause is credited to every window that was still open when it began, and to nothing that had
  already closed. Put plainly: **a note he had not yet missed when he paused has not been missed.**
  The alternative — carrying an offset forward for whatever happened to be unsettled at the instant
  of the pause — was rejected because it is knowledge only the *live* fold has. `judgeAll` from the
  final snapshot could not re-derive it, so the two paths would have disagreed about the same
  performance, which is the one thing spec I2 cannot allow. Measuring in music time is derivable
  from the snapshot alone, because the snapshot's legs already record every pause.

- **So `TickTiming` gained `elapsedNanosAt`, and the judge uses it for every interval.** The delta
  behind a verdict is `elapsedNanosAt(ticksAt(note.atNanos)) - elapsedNanosAt(onset)`; a window
  closes on `elapsedNanosAt(position) - elapsedNanosAt(onset)`; `Windows.at` measures the same way.
  Wall time now enters the judge at exactly one point — `ticksAt`, turning the input device's
  timestamp into a musical position — and everything after that is music. `nanosFor` is no longer
  consulted by the judge at all, which is why the `legAt` boundary above stopped being a verdict's
  business.

- **The price is that `dtMillis` is quantised to one tick**, 0.099ms at 60bpm and 0.030ms at 200bpm,
  because the played note's timestamp goes through `ticksAt`. Stated rather than hidden: it is ~1000×
  below the on-time band and far below the mic path's unmeasured latency, and `Ticks` is the app's
  exact timebase by design. The exact-arithmetic alternative (subtracting the leg origins instead)
  was rejected for a worse failure: a note stamped on the exact tick of the resume instant falls on
  the wrong side of `legAt`'s `<` and would have read as though played at the pause, a 17-second
  error one tick wide. Quantising is uniform and has no cliff; `a note on the edge of the on-time
  band is correct, and dt is stated to one tick` is where the resolution is written down.

- **`closingPosition` steps forward in ticks instead of adding milliseconds to a wall time.** It used
  to be `ticksAt(lastWallTime + maxWindow + 1ms)`, which `ticksAt` clamps to the next leg's start —
  so a pause landing within one window of the last note left `judgeAll` closing *at the pause
  position* with the final note unsettled, while the live path had already missed it. It now asks the
  map how much music one window's worth of ticks is worth at that point and takes enough of them to
  exceed `maxWindowMillis`, so it needs no inverse and meets no clamp. Deliberately arithmetic rather
  than a `while` that walks until the map agrees: the map is a caller-supplied interface, and a loop
  that trusts it to be strictly increasing hangs the judge rather than mis-judging when it is not.

- **Two tests hold it, and the second is the one that was missing.** `a note overdue but still
  inside its window when the phone rings is not missed` is the defect itself, and it fails on the
  pre-fix judge with exactly the reported shape. `an imperfect performance across a pause is judged
  the same live and from its snapshot` is what spec I2 actually promises across a pause and what no
  test said out loud: both pause tests before it used all-correct fixtures, so the live fold and
  `judgeAll` agreed trivially. The new one carries a wrong note, a note 250ms late and two misses,
  asserts the exact judgement list, and *then* asserts the two paths match — the assertions have to
  be in that order, because two paths agreeing on a wrong answer is precisely what the old suite was
  blind to.

- **`retime` remains necessary and is not what fixes this.** Without a newer map, `ticksAt` maps a
  post-resume timestamp through the pre-pause origin and puts the note the length of the pause
  further into the piece. Its KDoc is a one-line pointer here per CLAUDE.md's comment rule; the
  substance is the two entries above and the two below.

- **One `LiveSession` drives the judge in tests, built on a real `TempoConductor` and `FakeClock`.**
  The previous harness rebuilt the UI's frame loop against a bare `TickTiming` with a synthetic
  `now`, so it could not express a pause at all — which is why a suite that already asserted "the
  live fold and `judgeAll` agree" was blind to the retime bug for as long as it existed. Driving a
  real conductor means the transport's own pause/resume is in the loop, and both tests share one
  model of how `PracticeViewModel` folds: sample the conductor per frame, fold an input in at its
  own timestamp, re-time on resume.

### The path, the placement read and the streak (2026-08-15)

- **A stage claims what it *adds*; its spec carries everything before it.** The two run in opposite
  directions on purpose. `Stage.skills` is only the new material, which is what makes "the first
  stage that is not solid" a meaningful answer — if each stage restated its inheritance, every gap
  would drag the reader back to stage one. `Stage.spec` is the opposite: written as a **delta on the
  previous stage's spec** in `CurriculumStages.kt`, so the cumulative property is structural rather
  than remembered. Stage seven cannot quietly lose stage two's quarter notes, because stage seven
  never restates the symbol set.

- **Two tests decide the stage list, and one of them found a live defect in the ladder.**
  `every skill a stage claims is one its own material actually tests` (the union over 32 seeds) stops
  a stage claiming something unreachable, i.e. a rung that can never be climbed. The stronger one is
  `a drill aimed at a claimed skill contains it more often than not`, which is what caught
  **`specTargeting(RhythmFigure(Half, dots = 1))` producing a drill that cannot contain a dotted half
  at all — 0 of 32.** `withRhythmFigure` narrows `symbols` to the one figure, and in 4/4 a dotted half
  leaves a quarter to fill that the symbol set no longer has, so `BarFill` never offers it;
  `widenUntilBarsFill` does not fire because the bar *can* be filled, just not with the target. The
  scheduler would then have offered the same untestable exercise forever, since an outcome with zero
  attempts changes no state. Stage eight's claim moved to the dotted **quarter** (32/32), which is the
  commoner figure anyway. **The underlying `withRhythmFigure` weakness is still live in `:core:score`
  and is not fixed here** — targeting any figure that cannot fill a bar alongside its own symbol has
  the same shape. The threshold is "more often than not" rather than "always" because accidental
  targeting is probabilistic (~78–94%): a bar sometimes has no room for the altered note.

- **`SkillState.isSolid` is the one definition, and `:app` still holds a second copy.** Stage passing
  and the Progress screen's "Solid" bucket must agree or the path and the screen tell two stories, and
  this repo has already shipped two bugs that were purely a second copy of a decision. The definition
  now lives on `SkillState` (strength ≥ `SOLID_STRENGTH`), and `ProgressModel.bucketOf`'s "Solid" is
  exactly this **plus not-due** — due-ness is about spacing, not about reading, so a stage must not
  un-pass itself every four hours. The duplication is recorded rather than removed because `:app` was
  frozen for parallel work in the pass that added this; the fix is one line —
  `ProgressModel.SOLID_STRENGTH` becomes `SkillState.SOLID_STRENGTH` and `bucketOf` asks
  `state.isSolid`. Same story for `SkillOutcome.CLEAN_ACCURACY`, which the scheduler's update rule and
  the placement read now share, so "that went well" cannot mean two things.

- **`PracticeFocus` carries a base spec as well as the skills, and the base is the load-bearing
  half.** Skills alone would narrow *what* is drilled while leaving the generator building from
  `DefaultBase`, so a stage-seven leger-line drill would be four bars of bottom-rung treble with one
  dial turned. Passing the stage's own spec as the base is not the stage picking material — the
  scheduler still picks the target and the generator still writes the notes — it is the stage setting
  the level, which is the only thing that makes the ladder mean anything.

- **A focused skill with no state yet joins the pool at zero strength and due now.** Filtering the
  stored states by the focus alone would make a stage's *new* skills unpickable, which is precisely
  backwards: a stage's new skills are the ones with no history, and they are the entire point of
  moving to it.

- **`SpacedPracticeScheduler.DefaultBase` and stage one's spec are deliberately different, and that
  is a duplication with a countdown on it.** `DefaultBase` is the base `specTargeting` starts from;
  stage one's spec is the first rung's material (narrower: the middle band only, whole and half
  notes). They answer different questions today, but they are both "the bottom of the ladder", and
  the honest end state is `DefaultBase = Curriculum.Standard.stages.first().spec`. That changes live
  behaviour and `AppPracticeWiring`, so it is not a same-pass change.

- **The placement climbs 1 → 2 → 4 → 7 → 10 and stops at the first probe that does not go well.**
  Accelerating rather than a binary search because docs/journey.md asks it to *start easy*: a binary
  search opens an absolute beginner on grand-staff accidentals, which is the same insult as stage one
  in the other direction. Five probes is the ceiling, so it is minutes rather than an exam. Skipping
  a rung is safe because the specs are cumulative and, more importantly, because **nothing is credited
  that was not attempted** — a probe at stage seven seeds only what its own notes actually tested.

- **The climb reads `cleanliness`, not `accuracy`.** They differ by whether notes that were never
  written count against you, and a placement that ignored a flurry of extras would place a reader
  above what they can actually play.

- **Four levers keep an over-generous placement cheap, and they are these four.** Evidence under
  `MIN_ATTEMPTS_FOR_CREDIT` (3 notes) earns nothing at all, because two notes is not a reading; a
  skill short of clean is seeded *proportionally below* solid rather than not at all, so it is
  visible without being claimed; everything seeded is **due immediately**, so the ordinary scheduler
  is free to disagree within one session; and `repetition` is always 0, so a placement never grants a
  rung on the spacing ladder. Together they mean the worst case of placing someone too high is one
  session, not a stranding.

- **`Placement` deliberately does not carry a stage.** Where the reader stands is
  `Curriculum.currentStage(placement.states)` and only ever that. A placement that also announced a
  stage would be a second answer to a question that already has one, and the two would drift the
  moment the scheduler updated a state.

- **A refused probe is no evidence and ends the read.** It is not a bad result — spec I3's whole point
  is that a refusal is a statement about the pairing, not about the playing — so it seeds nothing and
  debits nothing, and the summary names the reason.

- **A mono input can never pass stage four, and that is the honest answer rather than a bug.**
  `playableBy` forces `bothHandsActive = false`, so no measure ever has two hands sounding, so
  `HandIndependence` is never tagged and never becomes solid. The mic genuinely cannot judge two
  hands. **The path UI has to say so** ("this rung needs the tapped keyboard") rather than leaving a
  mic-only reader stuck at a rung with no explanation.

- **`Streak` takes the timezone and the current time as arguments.** `ZoneId.systemDefault()` inside
  pure code is untestable by construction, and "which day was that?" is a question about where the
  reader is. Yesterday still counts as a live run, because the day is not over until it is over. There
  is deliberately no "you broke it", no penalty and no record of what was lost: `currentDays = 0` is a
  fact about the calendar. **Which sessions counted as practice is the caller's decision, not this
  fold's**, and the parameter is named `practisedAtEpochMillis` for that reason: `RoomSessionStore`
  passes sessions where a note was actually played, which is a better answer than "finished" and one
  only the store has the evidence for. The rule this must never lose is that showing up is not
  practice.

- **`Curriculum.Standard` is `by lazy` because it is a companion property referring to a class
  declared below it.** Eager initialisation there is an initialisation-order trap for a value that is
  built once and never changes.

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

### A drill must be able to contain what it targets (2026-08-15)

- **A bar that *adds up* is not the same as a bar that *contains the figure*, and the gap between
  those two questions was a silent infinite loop.** `withRhythmFigure` narrowed `symbols` to the
  targeted value and then asked `widenUntilBarsFill` whether the bar could be filled. For a dotted
  half in 4/4 the answer was yes — with two plain halves — so nothing widened and the dotted half
  was never offered to the filler at all, in 0 of 32 seeds. `BarFill.fill` only ever picks a choice
  whose *remainder* is reachable, and with steps of 2 and 3 over four slots, three leaves a
  remainder of one that nothing can fill. The consequence is worse than a dull exercise: the drill
  records zero attempts for the skill, zero attempts changes no state, the skill stays weakest, and
  the scheduler offers the identical useless drill for ever. `widenUntilTargetFits` asks the right
  question instead — `BarFill.canPlace(target)`, i.e. "does a bar exist that contains this?" — and
  it subsumes the old one, because a bar that cannot be filled cannot contain anything either.

- **The companion search tries each candidate alone before accumulating.** Longest-first
  accumulation reached a fillable set by adding a whole note that did not help and a quarter that
  did. Both satisfy "never widen to something shorter than the spec asked for", so no test caught
  it, but the extra value dilutes the figure being drilled. Trying singles first gives the smallest
  vocabulary that can hold the target; the accumulating loop stays as the fallback for a metre
  where no single companion is enough.

- **`withBarHolding` moves the metre, and only when the bar is too short to hold the figure at
  all.** A semibreve cannot exist in 3/4 and no choice of companions changes that, so a drill aimed
  at one in 3/4 was unreachable by construction. The metre is a dial like any other, and the beat
  unit is kept while the beat count rises to the smallest that holds the figure: a whole note in
  3/4 becomes 4/4, a dotted whole in 4/4 becomes 6/4, which is where a dotted semibreve actually
  lives. The bar is left alone whenever it can host the figure, so the everyday case — a dotted
  half in 4/4 — keeps the metre Dewi was reading.

- **Tuplet choices now honour `maxDots`.** They were built with `dots = 0` hard-coded, so
  `RhythmFigure(symbol, dots > 0, tupletNumerator = 3)` — an ordinary dotted note inside a triplet,
  which any imported score can contain — was another figure the generator could record and never
  write.

- **An alteration is only readable where the key does not already spell it, so `withAccidental`
  moves the key when it must.** The walker refuses to write an accidental whose sounding pitch
  class is in the key, because that would be a respelling rather than something to read. Two
  consequences were invisible until swept: a **natural** is never an accidental in C major (nothing
  is altered to cancel), and a **double sharp** is never one in D major (every letter's double
  sharp spells a diatonic note there). Both were 0 of 32. `isWritableIn` is the one test, and
  `nearestKeyWriting` moves to the closest key that passes it — a leap of one fifth, not a redesign.

- **`extraAlterations` admits a natural only in a key that alters something.** Filtering `Natural`
  out unconditionally was what made the case above unreachable; admitting it unconditionally would
  have changed the random stream of every C-major spec in the app — for no gain, because the
  walker's own fits check rejects every natural in C anyway. Gating on `key.fifths != 0` is not a
  dodge, it is the same sentence the first bullet of this note states: a natural sign is an
  accidental exactly where the key alters that letter.

- **`MelodyWalker` can repeat a pitch, because music does.** `nextIndex` filtered `it != index`, so
  no generated exercise has ever contained two notes the same — which makes `SkillTag.Leap(0)`
  impossible to drill. That one is not hypothetical: all three shipped corpus pieces contain
  repeated notes, so failing one in Ode to Joy was enough to weaken a skill the generator could
  never exercise. `REPEAT_WEIGHT` is 2, above a leap and well below a step, which is roughly how
  often real melodies repeat.

- **`TargetedDrillTest` sweeps the skill space exhaustively rather than mirroring `Curriculum`.**
  Two reasons. `:core:score` cannot see `:core:practice`, so a mirror would be a copy that drifts;
  and the scheduler targets whatever is *weakest*, which includes skills derived from parsed pieces
  that no stage ever claims — which is precisely where the natural, the double sharp and the
  repeated note were hiding. `the sweep covers every skill this app can derive from its own
  material` is what stops the sweep drifting from reality: it derives skills from real generated
  drills and the real corpus and asserts the sweep is a superset.

- **`isEveryday`'s `when` is the compile-time half of that guard.** It is exhaustive over
  `SkillTag`, so adding a case to the sealed hierarchy fails to compile here as well as in
  `specTargeting` — the sweep cannot silently omit a new kind of skill.

- **The reachability guard re-checks a failure against `DEEP_SEEDS` before believing it.** An exact
  melodic interval is genuinely rare — `Leap(11)` lands in about one drill in twenty-four on a base
  with six notes in it — so a thin pass at 32 seeds would be luck, and the next innocuous change to
  the random stream would turn it red for no reason. Re-running only the zeroes at 256 seeds costs
  nothing while everything is reachable, and turns "0 of 32" into a claim worth failing a build for.

- **Known gap, stated rather than hidden: the generator writes only 3-in-2 tuplets.** A quintuplet
  skill can be derived from an imported score and can never be drilled. `a tuplet this generator
  cannot write is a stated gap rather than a broken bar` pins the two things that must remain true
  meanwhile — the bar still adds up, and the drill does not quietly substitute a triplet for the
  tuplet it was asked for, which the first version of the fix did by leaving `allowTuplets` on.

- **`Duration.figure` exists so the tag is spelled once.** The generator has to ask "is this choice
  the figure I was told to target?" and the deriver has to ask "which figure is this note?"; two
  constructions of `SkillTag.RhythmFigure` from the same three fields is exactly the shape of
  duplication this repo has already been bitten by twice.

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

- **A resume after a pause appends a leg the already-begun judge cannot see.** Closed 2026-08-08 by
  `PerformanceJudge.retime` (see the `retime` entries under `:core:practice`), called from
  `PracticeViewModel.play()`. Still open: a note whose onset is at or before the pause position but
  which had not been played yet keeps its pre-pause wall time, so it is `Missed` on the first tick
  after the resume and whatever Dewi then plays becomes an `Extra`.

## :app — session wiring (view model, route, container)

- **The Practise tab asks the scheduler on every entry; it never opens a fixed piece.** That is the
  ladder made real (CLAUDE.md, *The ladder problem*), and it has a consequence worth stating: on a
  fresh install there is no skill history, so `SpacedPracticeScheduler` has no targets, no corpus
  piece passes its suitability gate, and the app opens on a **four-bar generated exercise rather
  than on Bach**. That is the design working, not a corpus that failed to load — the corpus becomes
  reachable as strengths grow. `AppPracticeWiring` logs the whole decision with its inputs
  (`skills=`, `due=`, `corpus=`, `seed=`, `now=`) so a report can say which of those it was.

- **`PracticeWiring` is one port rather than fourteen constructor parameters.** The view model needs
  the layout engine, the metrics, the metronome, the tone player, a conductor factory, a judge
  factory, an input factory, the scheduler, the generator, the corpus, both stores and a clock —
  all of it or none of it. One port keeps the view model constructible in a test without an Android
  context, a database or an audio device, and keeps the container the only place that knows which
  concrete adapter is in play.

- **The metronome is fed from `tick()`, the same sample the playhead and the judge use.** It is not
  given a tempo and left to run: `Metronome.onPosition` is handed the Conductor's reading, so a
  click cannot drift from the note being judged (docs/spec.md I1). `enabled` is kept in sync with
  the UI toggle rather than skipping the call, because a muted beat is `counted` inside
  `ClickMetronome` and a skipped call would leave a silent metronome indistinguishable from a
  broken one in a report.

- **Selecting PLAY IT mutes the metronome and the note echo, and says so.** The microphone hears
  whatever the phone plays, so a click or a reference tone arrives back through `MicPitchAnswerSource`
  as a played note and is judged as one — the app scoring itself. Muting is the honest default;
  Dewi can turn either back on, and the decision is logged with the input that caused it.

- **The echo fires on `WrongPitch` only.** It answers the question a wrong note actually raises —
  *what should that have been* — and it cannot machine-gun, because every echo needs a note Dewi
  played first. Echoing `Missed` would fire once per note through a passage he did not attempt,
  which is noise at exactly the moment he is already behind.

- **"Hear it" runs through the Conductor, not a coroutine that sleeps between notes.** `ScorePlayback`
  is advanced from `tick()` with the sampled position, so the note that sounds is the note under the
  playhead by construction rather than by two clocks agreeing. Judging is switched off for a preview
  (`judgeState = null`) and the transport is marked `previewing`, so a listen can never produce a
  verdict, save a session, or fold a skill — a celebration for music the app played itself would be
  the worst thing on the screen.

- **A session is written at pause as well as at the end, but skills are folded only at the end.**
  `SessionStore.save` is idempotent per session id, so writing at every pause costs nothing and is
  what makes docs/spec.md I4 true of the app rather than only of the store's tests. `SkillStore.record`
  is not idempotent in the same way — `attempts` and `lapses` count **sessions** — so folding at each
  pause would count one session several times and drag every interval out. Backgrounding, switching
  tab and rotating all pause, and all three therefore save.

- **`SessionRecord` accumulates judgements as they settle rather than asking the judge at the end.**
  The judge settles verdicts incrementally and `finish` is the only thing that produces a
  `SessionResult`; a session killed mid-piece never reaches it. Keeping the list here is what lets a
  paused session be stored with its working shown.

- **`play()` retimes the judge on resume.** `Conductor.timingSnapshot()` taken at `begin` cannot
  contain the leg a later pause appends, so without `PerformanceJudge.retime` every note after a
  resume reads late by the length of the pause — the gap recorded in `.claude/CODE-NOTES.md` under
  `:core:score`, now closed at the one call site that can see the resume.

- **The route pauses on dispose, not only on `ON_STOP`.** Switching to another tab disposes the
  practice screen and with it the frame loop that calls `tick()`, but the Conductor keeps running:
  coming back would jump the position forward and mark everything in between `Missed`. `ON_STOP`
  alone does not cover it, because the activity never stopped.

- **`openPiece` ignores a request it has already handled.** `ui.repertoire.PracticeRequest` keeps its
  pending piece in place deliberately, so a re-entering Practise tab would otherwise reload the piece
  and discard a session in progress every time Dewi glanced at Progress. The request id is compared
  against the last one consumed, which lives in the view model and so survives the tab switch.

- **A drill for a mono input drops `HandIndependence` rather than generating two hands.** The
  scheduler already applies that rule to its own choices; `chooseDrill` is the other route to the
  generator and would otherwise propose material its own judge refuses. `monoSafe` narrows the spec
  to one staff for the same reason, and both log the drop.

- **`AppContainer.release()` asks the lazy delegate, not the property.** Touching `micSource` to
  release it would construct an `AudioRecord` in order to close it, and on a launch that never used
  the microphone that is a permission prompt for nothing.

- **`AudioRecordPcmCapture` is given the platform `AudioManager`.** Without it the capture still
  tries `UNPROCESSED` first but has to log its support as *unverified*; with it the report says
  whether the device actually claims to support the source it opened, which is the difference
  between a fact and an assumption in a timing investigation.

## :app — the staff, its motion, and how the practice screen feels

- **The clef, key and time signature are pinned, and that is a correctness feature.** They used to
  scroll away inside the first bar, so from bar 2 onwards Dewi was sight-reading in G major with
  nothing on screen saying so — an accidental he has to *infer* is not the skill being trained.
  `StaffCanvas` therefore draws the scrolling music first and overdraws an opaque strip of
  `NotationColors.paper` carrying the furniture, so notes pass behind it rather than over it.

- **`PinnedFurniture` translates a group rather than laying it out again.** The strip's contents are
  the glyphs `:core:notation` already emitted, moved by a constant `originX - anchor.x`. That matters
  because `MeasureFurniture.columnsFrom(x)` is `x` plus a fixed set of gaps, so a translation is
  pixel-identical to a re-layout — and re-laying it out in `:app` would be a second engraving engine,
  the exact thing `StaffCanvas` exists not to be.

- **A furniture group is identified by x-range, not by a tag.** `StaffSystem` does not say which
  glyphs are furniture, but it does say where each measure's note area begins, and everything a
  measure draws between its barline and its `noteAreaX` is by construction its clef, key and time
  signature. The brace is picked out by glyph and pinned unconditionally, because it belongs to the
  system rather than to any bar.

- **What the pin cannot do, stated rather than discovered later.** `at(position)` returns the last
  group at or before the playhead, so a measure that restates *only* what changed — a bare 3/4 at a
  metre change — pins only that, and the clef and key are not re-added from the earlier group. The
  current corpus and every generated exercise state all three once at bar 1, so today the pinned
  group is always complete; the honest failure mode is an *incomplete* strip, never a stale one
  showing a key that is no longer in force. Fixing it properly needs `:core:notation` to publish the
  furniture in force per measure, which is a change to that module's contract rather than to this one.

- **`PLAYHEAD_GUTTER_SPACES` exists because the pinned strip would otherwise cover the playhead.**
  The playhead sat at 28% of the viewport, which at a 13dp staff space is about eight staff spaces —
  narrower than the ten the grand staff's brace, two clefs, key and time signature occupy. The
  anchor is now `max(fraction of viewport, gutter + margin)`, so the read-ahead shrinks rather than
  the playhead disappearing.

- **The playhead's bloom is radial, not a horizontal band.** A band was tried first and looked
  wrong for a reason worth recording: it has two hard vertical edges, and once the pinned strip
  clipped one of them it read as a highlighted block sitting on the staff rather than as light. A
  radial gradient centred on the playhead has no edges at all, so it can spill over the pinned
  furniture without looking like a rectangle. The line itself stays crisp, because that is the thing
  the eye actually tracks.

- **A verdict's colour never travels through another verdict's colour.** `NoteStyling` interpolates
  from `NotationColors.upcoming` — a neutral grey — to the verdict's own colour, and never from one
  verdict colour to another. Mint→coral would pass through a muddy amber that reads exactly like
  `offTime`, so for a few frames a wrong note would look like a late one. Only the *scale* pops; the
  colour merely settles, and it settles three times faster than the pop so a wrong note is legibly
  wrong before it has finished moving.

- **Every note animates on its own timeline.** `rememberVerdictLandings` starts one `Animatable` per
  attack index rather than sharing one, because at 160bpm with sixteenths the verdicts arrive faster
  than a single 260ms animation could finish, and a shared one would queue them up behind each other —
  showing a verdict for a note two behind the one being played. 260ms is also short enough that the
  pop is over before the next note in a fast passage is judged.

- **`NotationColors.upcoming` was previously identical to `ink` in both themes**, so the field
  existed and did nothing. It is now genuinely dimmer, which is what makes reading ahead *feel* like
  reading ahead: the music you have not reached yet is quieter than the music under the playhead.
  It is deliberately a grey rather than a tint of any verdict colour — an unplayed note must not
  resemble a judged one.

- **The staff card is sized to the staff, not to the space available.** It used to `fillMaxSize`,
  which left a slab of blank paper above and below the grand staff on a tall phone; wrapping the
  engraving and centring the card in the leftover space turns that dead area into breathing room and
  lets the card read as a sheet rather than as a panel.

- **The entry reveal is a left-to-right wipe, not a fade.** `NoteStyling.revealAlpha` staggers each
  note's alpha by its own x, so a piece appears to be written onto the page in reading order. It is
  driven by one `Animatable` keyed on the `StaffSystem`, so re-loading the same piece replays it and
  a mere recomposition does not.

- **The count-in pulses per beat and the last beat is violet.** A static number gives no sense of
  pace, and not knowing which beat is the last makes the first note late every time — which the
  judge would faithfully record as Dewi's mistake. Violet is used for the final beat specifically
  because it is the one brand colour carrying no verdict meaning; mint would read as "correct".

- **Key presses have a fast attack and a slow release, and a haptic on the way down.**
  `KEY_ATTACK_MS` is 45 and `KEY_RELEASE_MS` 260 because that asymmetry is what an instrument does —
  a symmetric fade reads as a web button. `HapticFeedbackType.KeyboardTap` fires on the pointer-down
  edge, in the same branch that timestamps the tap, so the tick a finger feels and the moment the
  judge is given are the same event.

- **The keyboard's range comes from the piece, not from a constant.** It was fixed at C3–C6, and
  the scheduler's own bass-clef drill ("below the staff") generates notes underneath that floor —
  which are then untappable, so every one of them is judged `Missed`. That is the app inventing a
  fault, the exact thing docs/spec.md I2 forbids, and it was invisible until a generated exercise
  happened to sit low. `keyboardRange` snaps outward to whole octaves so the black-key pattern still
  reads as a keyboard, with a three-octave floor so a two-note exercise does not produce four
  enormous keys. The cost is narrow keys on a wide piece, which is the right trade: a small key is
  hard, a missing key is impossible.

- **`NotationColors.keyNatural` / `keySharp` do not invert with the theme, and the rest of the
  palette does.** The keyboard originally borrowed `paper` and `ink`, which is right in light mode
  and photographically inverted in dark — naturals went near-black and the sharps went white. A
  piano's naturals are pale and its sharps are dark under any lighting, so these two are their own
  colours, merely dimmed for dark mode rather than swapped. Everything else on the screen still
  inverts, because paper and ink genuinely should.

- **Every C carries a dot and only middle C carries the letter.** One landmark was enough at three
  octaves and is not at five: a hand has nothing to find its place from between two labelled keys
  four octaves apart. A dot is a landmark rather than a name, so it still cannot be read *instead*
  of the staff, which is the reason the keys are otherwise unlabelled.

- **`onToggle` on `PracticeScreen` is nullable, and null hides the chips.** The metronome and echo
  toggles are session state the view model owns; rendering a control whose callback defaults to a
  no-op would put a switch on screen that silently does nothing, which is worse than not offering it.

- **The header renders `PracticeUiState.choiceSummary`, and for a while nothing did.** The note here
  used to claim the header already showed it, which is why the control bar dropped it — and the
  result was that the scheduler's reason ("Written for you, to drill bass clef, lower staff") was
  computed, logged, and never shown. docs/spec.md I5 is about what Dewi can *see* change when he gets
  better, so `PracticeHeader` now lists composer and summary as separate one-line subtitles: a
  generated exercise has no composer and a corpus piece has both. The control bar still says nothing,
  because the same sentence twice on a phone is worse than once.

- **The metronome is driven correctly and still cannot be heard, and the reason is one line in
  `:core:audio`.** `ClickMetronome.staticTrack` rejects the `AudioTrack` when
  `state != STATE_INITIALIZED`, but a `MODE_STATIC` track legitimately reports
  `STATE_NO_STATIC_DATA` (2) until its samples have been written — which is the state the device
  actually reports (`beat click track uninitialised state=2`, emulator API 35, 2026-08-08). Both
  tracks are therefore discarded at construction and every beat is counted as
  `beatsWithNoTrack`, on every device rather than only this one. The driving side is proven by the
  same report: `configured tempo=60bpm time=4/4 barStart=0ticks` plus 20 beat crossings from
  `onPosition`, so the Conductor-driven design works and only the track creation fails. The fix
  belongs where the bug is: accept `STATE_NO_STATIC_DATA` before the write and require
  `STATE_INITIALIZED` after it.

## :app — shell, repertoire, progress, results, settings

- **`AppShell`'s `settings` parameter carries a default while its four siblings do not.** The other
  destinations are handed in by `MainActivity`; the Settings destination was added by an agent that
  did not own that file, so the slot defaults to `SettingsRoute()` and `SettingsRoute` reaches the
  container through `LocalContext.current.applicationContext as? PrimaVistaApp` instead of being
  given one. The cast is nullable on purpose: a Compose preview has no `PrimaVistaApp`, and an
  honest "settings need the running app" panel is better than a crash. If the activity ever starts
  supplying the slot, the default is the only thing to delete.

- **The bottom bar expands the selected tab into a labelled pill and collapses the rest to icons.**
  Five destinations at a fifth of a phone's width each is about 82dp per tab, and "Diagnostics" does
  not fit — the previous four-tab `NavigationBar` only worked because there were four. Expanding one
  item is also what marks the selection without relying on colour alone. The consequence worth
  knowing when driving the app from `adb`: **the tabs move**, so a tap x that hits Repertoire while
  Practise is selected misses it while Settings is selected.

- **`destinationTransition` reads the two destinations' ordinals, so the slide direction follows the
  tab order.** Motion that always travels the same way is decoration; motion that says which
  direction you moved is information, and it is the one thing a transition can add for free.

- **`PracticeRequest` has two fields, not one.** They are consumed by different owners at different
  moments: the shell watches `count` to switch tab, and the practice route reads `pending` to load
  the piece. A single take-once value would have the shell's navigation swallow the piece before the
  practice route ever composed. `peek()` deliberately leaves `pending` in place so re-entering the
  Practise tab reloads the piece Dewi last chose rather than silently reverting to the scheduler's
  pick, and `count` increments per request so asking for the same piece twice still navigates.

- **The repertoire keeps the dropped-markings count visible while the card is collapsed, in the
  error colour, and lists every `Dropped` when it is expanded.** A count alone says something is
  missing and nothing about whether it matters; the list is the difference between "this piece is
  approximate" and "the approximation is a slur I do not care about". This is the screen where
  docs/spec.md's *the MusicXML subset is a subset* is actually visible to Dewi.

- **`ResultTone` is decided from `SessionResult.cleanliness`, never `accuracy`.** Accuracy gives full
  marks to a run that played every written note *and* twenty that were not written, so celebrating on
  it would put a flourish over exactly the performance that needs correcting. Only
  `ResultTone.Excellent` celebrates, `toneOf` and `supportOf` are pure, and `ResultToneTest` asserts
  the cases that matter — a 40% run, a 100%-accuracy run with a trill of extras, and a session with
  nothing to judge. The supporting sentences are checked for congratulatory words, because the honest
  version of this screen fails by wording long before it fails by arithmetic.

- **The percentage counts up but the tone headline does not move.** The verdict is legible from the
  first frame and the animation only decorates the number it is already showing. An animation that
  delays a verdict is an animation that hides it, which is worse than no animation at all.

- **`StrengthMeter` and `meterTint` live in `ui/progress` and the results sheet imports them.** A
  skill's strength and a skill's accuracy in one session are the same quantity drawn the same way;
  before this they were two copies with different thresholds and different bar sizes, which is how
  "good" quietly comes to mean two things. The precedent for reaching across is already there —
  `ResultsSheet` imports `describe` from the same package.

- **`trendOf` returns null below four sessions and the screen says so.** Two points are a line but
  not a direction, and a "you're improving" drawn through one good session is the app flattering
  him. The bars themselves are one per stored session with nothing interpolated between them:
  smoothing would draw practice that never happened.

- **The Progress screen reads sessions as well as skill states.** `SkillState` carries no history —
  only a current strength, a due time and lifetime totals — so a direction of travel is not derivable
  from it at all. `SessionStore.recent` is, so the route maps finished sessions to `SessionPoint` and
  the screen draws those. Anything the store cannot support is stated as unavailable rather than
  approximated.

- **The settings screen reads latencies through `SettingsStore.latencies()`, and used to read the DAO
  directly.** `opening.database.routeLatency().all()` was a live crash path: a `@TypeConverter` fails
  the whole cursor, so one unreadable `route_latency.provenance` value took the app down one tab
  across from where it was stored. The port now has an accessor that returns
  `StoredReading<List<RouteLatency>>` and no entity crosses the boundary. Nothing in `:app` may go
  back to a DAO for the same reason: the store is where a refusal becomes a value instead of a crash.

- **`SESSION_READS_SETTINGS` is gone, and with it the caveat that explained the gap.** Every stored
  preference is now read when a session loads — see *The session reads the settings* below for what
  each one means. The caveat was honest while it was true; leaving it in place after the wiring
  landed would be the same lie in the other direction.

- **`latencyReading` is pure and separately tested because the wording is the failure mode.**
  docs/todos/measure-audio-latency.md exists to stop an assumed figure being presented as a measured
  one; `LatencyPresentationTest` asserts that no provenance other than `Measured` renders the word
  "measured" or sets `measured`, that an unmeasured route reads "not measured" rather than 0 ms, and
  that an assumed figure states the bias it carries. `NotApplicable` reads "nothing to correct"
  instead of a suspiciously perfect zero.

- **The stored-session count is probed with a limit and reports `N+` when it hits it.** `recent()` is
  a paged read, so printing its size as a total would quietly cap at the limit and read as an exact
  figure — the same shape of error as an assumed latency wearing a measured label.

- **What was verified on the emulator, and what was not.** Every screen in this section was read back
  from a screenshot in both themes except the results sheet, which was captured only in light. The
  results capture that matters was a real 0% run: headline "Rough one", no bloom, and the extras line
  stating the run's cleanliness. The repertoire → practice hop was driven end to end — "Practise
  this" on Ode to Joy landed on the Practise tab with that piece loaded at its own 96bpm.

- **`ProgressRoute` branches on `DatabaseOpening` before anything else, and an unreadable store gets
  its own panel.** "Practice history can't be read" and "nothing read yet" look identical on screen
  and mean opposite things — one is a fault to investigate, the other is an invitation to play — so
  the second must never stand in for the first (docs/spec.md I4). Loading is a third state again,
  which is why `states` is a nullable `produceState` rather than starting at an empty list.

### A refused read is rendered, not swallowed (2026-08-08)

- **`DatabaseOpening` was branched on; `StoredReading` was not, and that was the bigger hole.** The
  database opening is one failure at launch. A refusal from `recent()`, `storedStates()` or
  `latencies()` is a failure of *one read on a working database*, and both routes used to end it with
  `.orEmpty()` — so a single corrupt row made Progress say "Nothing read yet" over sessions sitting
  on disk, and Settings say "No sessions stored yet". Every bulk read in `:app` now carries its
  `StoredReading` all the way to the composable that draws it, and each of the three states is drawn
  differently: `null` is "Reading…", `Readable(empty)` is the invitation to play, `Unreadable` names
  what could not be read and why. `storedSessionsText` is pure so `LatencyPresentationTest` can pin
  the one that matters — a refusal must never be worded as an empty history.

- **`ui/Unreadable.kt` holds the wording once.** `ProgressRoute` and `SettingsRoute` each had their
  own "…can't be read" + "the file is still on disk" panel, differing in wording, which is how the
  same message quietly comes to mean two things. `UnreadablePanel` is the whole-screen form (the read
  the screen exists for failed) and `UnreadableNote` the inline form (one card failed, the rest of
  the screen is still true). Both carry `NOTHING_DELETED`, because the first thing a refusal has to
  answer is whether the app threw the history away to recover.

- **`ProgressRoute` asks `RoomSkillStore.storedStates()`, not `states()`.** `states()` degrades a
  refusal to an empty list by design — it implements a `:core:practice` port whose signature cannot
  carry one — and that degradation is right for the *scheduler* and wrong for the *screen*, which
  would then draw "Nothing read yet". The screen takes the wrapped accessor for exactly that reason.

- **A dismissed refusal used to leave the screen saying "Nothing loaded".** `dismiss()` cleared
  `PracticeUiState.refusal`, so closing the polyphony dialog left a loaded, titled piece over an
  empty-staff card whose text was "Nothing loaded" — the app stating something its own data
  contradicted, on the one screen docs/spec.md I3 exists to protect. The refusal now belongs to the
  loaded (score, input) pair and is cleared only by the next `load`; `PracticeScreen` keeps the
  dialog's dismissal in a local `remember` keyed on the reason. `Refusal.kt` holds the wording once
  so the dialog and the card cannot drift, and `RefusalWordingTest` asserts the polyphonic case names
  the bar, names the input, and offers hands-separate practice.

- **`SkillBucket.Building` said "Read correctly at least once".** It is the *else* branch of
  `bucketOf` — everything not due and not yet solid — so a first session where every note was missed
  put seven skills at 0% strength under a heading claiming he had read each of them correctly. It now
  says "Read at least once", which is what the bucket actually means.

- **`StrengthHero` said the list was "oldest attempt first".** `ordered()` sorts due-first then
  weakest-first. Nobody would have caught it from the code; it was visible in one screenshot.

- **`trendOf` is fed the bars that are drawn, not the thirty sessions read.** The strip draws
  `takeLast(12)` and the sentence beneath it used to be computed over all thirty, so "the last few
  sessions beat the ones before" could describe sessions not on screen. The caption is about the
  picture, so both now read the same list.

- **`keyboardRange` padded by one octave too many.** `(short / 12 + 1) * 12` turns a piece needing
  exactly one more octave into two, so a treble exercise spanning C4–G5 produced a four-octave
  keyboard instead of three and every key was a third narrower than it needed to be. Narrow keys are
  not cosmetic here: a mis-tap is recorded as a wrong note against Dewi. Rounding up properly
  (`(short + 11) / 12 * 12`) took the same exercise from 28 white keys to 21, confirmed on the
  emulator. The pad still all goes *below* the piece, so a bass drill's notes sit at the right-hand
  end of the keyboard — deliberate (reaching down is the common case) but worth revisiting.

- **`ProgressScreen`'s `LazyColumn` keys are the `SkillTag`, not `describe(it)`.** A display string as
  an identity is a crash waiting for two tags to describe the same way; the tags are data classes and
  already unique.

- **`RepertoireRoute` parses the corpus on `Dispatchers.Default`.** `produceState` runs on the
  composition's dispatcher, so every entry to the tab was doing a DOM parse of every shipped piece on
  the main thread. The re-parse per entry is gone as of 2026-08-08 — see *One corpus, parsed once*
  below.

- **`describe(SkillTag.Leap(0))` said "Leaps of 0 semitones".** A leap of nothing is a repeated note,
  and it appears in the corpus often enough to read as a bug to a musician.

### The results sheet says one number (2026-08-08)

- **The hero number was `accuracy` while the headline, the tint and the meter beside it were
  `cleanliness`.** So a run that played every written note *and* twenty that were not written showed a
  large **100%**, in red, under "Rough one" — two numbers on one screen telling Dewi opposite things
  about the same performance. `ResultTone`'s own KDoc had already argued why the verdict must come
  from cleanliness; the big number simply had not been moved with it. It is now
  `CountingPercent(result.cleanliness)`, and every mark on the sheet reads from that one quantity.

- **Accuracy is stated, not hidden.** `headlineBasis` spells out the arithmetic beside the number —
  "3 of 4 written notes" with nothing extra, "3 right, out of 4 written + 1 unwritten" when there is —
  so the denominator the headline used is visible rather than inferred. `extrasNote` then gives the
  other figure explicitly ("counted against the 60% above — the written notes alone came to 75%").
  Averaging the two into one figure was rejected: it would answer neither question.

- **`extrasNote` drops the reconciliation when the two figures round the same.** On a 0%-correct run
  both are 0%, and "counted against the 0% above — the written notes alone came to 0%" reads as
  nonsense; it now says only that the extras were counted against him. Seen on the emulator before the
  fix, which is why it is worth writing down: the failure was in the wording, not the arithmetic.

- **The percentage is `percent()` from `ui/progress`, not a second `PERCENT_SCALE` here.** The results
  sheet rounded the hero and truncated the extras figure, so the same quantity could print as 67% in
  one place and 66% in the other. One function, one rounding.

- **A session with nothing to judge shows an em dash, not `0%`.** `notesExpected == 0` makes both
  ratios 0.0, and a big red zero under "Nothing to judge" tells him he got everything wrong. The meter
  is dropped for the same reason.

- **"What held you up" is only shown when something did.** The list is the six *weakest* outcomes, so
  a clean run put six skills at 6/6 under a heading claiming they had held him up. It now reads
  "Nothing held you up" with the line "Every reading skill this piece exercised came out clean" —
  entailed, because if the weakest outcome is 1.0 then all of them are. The empty case said "not
  exercised enough to grade", implying a threshold that does not exist; it says "was not exercised".

- **`drillTarget` is one function, and the button label and the session must both use it.**
  `PracticeViewModel.drillTarget` skips `SkillTag.HandIndependence` for a mono input (spec I3 — a mic
  cannot hear both hands) while `ResultsSheet` labelled the button from the unfiltered weakest, so on
  a mic session whose worst skill was hand independence the button said "Drill both hands at once" and
  the app then drilled something else. This is reachable: `bothHandsSound` tags a measure where both
  staves have notes even when nothing overlaps, so a grand-staff piece with alternating hands is
  `Polyphony.Mono` (accepted on the mic) and still carries the tag. `ui/results/DrillTarget.kt` now
  holds the rule once. **It is not finished**: `ResultsSheet` takes `input: Polyphony` defaulting to
  `Poly` because `PractiseRoute` is another agent's file this pass, so today's behaviour is unchanged
  and the mic case still mislabels. Passing `state.input.polyphony` at the call site, and deleting the
  view model's private copy in favour of this one, is the whole remaining change.

### One corpus, parsed once (2026-08-08)

- **`ParsedCorpus` exists because the corpus was being parsed twice over, by two caches.**
  `AppPracticeWiring` cached its own `List<Score>`; `RepertoireRoute` re-parsed every shipped piece on
  every entry to the tab (three `[musicxml] parsed` lines per visit in the report). It holds
  `MusicXmlResult` rather than `Score` because the repertoire screen exists to show what the parse
  *dropped* and what *failed*, which a list of scores has already thrown away.

- **The cache is keyed on the parser instance it was built with.** A cache that ignores its input
  would hand a second parser the first one's answers, which is how two builds come to disagree
  silently. A different parser re-parses.

- **A missing corpus resource is now a failed row, not a dead tab.** `Corpus.read` throws
  `requireNotNull` when the resource is absent, and that threw straight out of `produceState`. The
  card that used to claim an empty list meant missing resources now says what an empty list actually
  means, because a missing file arrives as a `Failed` piece with its reason.

- **It lives in `ui/repertoire` only because `di/` belonged to another agent this pass.** It is not UI:
  `AppContainer` should hold it and `AppPracticeWiring.corpus()` should become
  `ParsedCorpus.of(...).mapNotNull { it.score }`, deleting that class's `corpusLock`/`parsedCorpus`/
  `parseCorpus`. Until then there are still two caches, which is the duplication this was meant to end.

- **Proven on the emulator.** `corpus/servedFromCache: 4` in the counted section after five visits to
  the tab, with one `[corpus] parsed pieces=3 readable=3 failed=0` line and no further
  `[musicxml] parsed` events.

### Sentences the data did not support (2026-08-08)

- **"…every one of them from notes you actually played."** A `SkillOutcome` counts every *judged* note,
  and `Verdict.Missed` is a note he did not play. After an all-missed session the header claimed
  twenty-one skills had come from notes he played. It now says "from notes this app put in front of
  you".

- **`SkillBucket.Due` said "What the scheduler will hand you next."** `next()` takes the weakest five
  of the due set and hands back one piece, so the bucket is what it *picks from*, not what he is
  getting.

- **The trend strip draws accuracy, and now says so.** `StoredSession` records `notesExpected` and
  `correct` and no count of extras, so the bars cannot show the results sheet's headline — and after
  that headline changed, an unlabelled strip would have been the same lie moved one screen across. The
  caption states what a bar is and that a noisy run reads higher here than it did on its own results
  sheet. Making them agree needs either an `extras` column on the session row or a
  `SessionStore.judgements(id)` read per bar; both are outside `ui/**`.

- **`trendText` said "the last few sessions beat the ones before".** `trendOf` compares the older half
  of the *drawn* bars with the newer half and drops the middle point on an odd count, so "the last few"
  was vague enough to read as a claim about his practice generally — it now names the halves. It fired
  "Improving" on a screen where every bar was near zero, which is arithmetically true and was worth
  making precise rather than louder.

- **A `Polyphony.Poly` piece whose `firstPolyphonicBar` was null claimed bar 1.** The two travel
  separately on `RepertoireRow` even though `Score.polyphony` is derived from
  `firstPolyphonicMeasure()`, so the fallback could only ever have printed a bar number that was not
  the answer. It now omits the bar rather than inventing one.

- **Knowingly left: `MIN_VISIBLE = 0.03f` draws a sliver for a 0% session.** A bar of literally nothing
  is indistinguishable from a session that is not there, and the strip's own captions give the figure.
  Worth revisiting if the sliver is ever read as a score.

### The session reads the settings (2026-08-08)

- **The stored tempo is a ceiling, not an override, and that is the whole decision.** A `Score`
  carries its own `defaultTempoBpm`, so a stored figure could have meant three things.
  `sessionTempoBpm(written, ceiling) = min(...)` was chosen because a sight-reader's need is *slower
  than written until I can read it* — never faster. An override would drag a 60bpm beginner exercise
  up to 72 because the setting said so, which nobody wants and which makes the easiest material
  harder. A percentage would need a field `PracticeSettings` does not have, and "50%" means 60bpm on
  Bach and 30bpm on a first exercise, which drills nothing. A ceiling only ever slows, so the control
  can honestly be labelled *Top tempo … bpm at most* and the screen can promise that a piece written
  slower keeps its own tempo. `AppContainer.conductorFor(score, tempoCeilingBpm)` is the only place it
  is applied; the view model calls the same pure function once more for the refusal card, which has no
  conductor to ask.

- **A preference decides how a run *starts*; it is not observed while one is running.** `load()` reads
  `SessionPreferences.settings()` once per session and applies tempo, metronome and listen-first there.
  Collecting the settings flow into the view model would have fought the mic mute below, and would mean
  changing a setting mid-piece silently retimed the music under the playhead.

- **The mic mute must not be written back.** Selecting PLAY IT forces the metronome and echo off for
  the run (the microphone would otherwise hear the click and score it as a note). That is a *forced
  state*, not a preference, so `selectInput` never saves `metronomeOn`; only the chip Dewi taps does.
  Without the split, one mic session would permanently turn his metronome off. `SessionReadsSettingsTest`
  pins it: "the mic muting the metronome does not turn his metronome preference off".

- **The input preference is resolved before the scheduler is asked anything.** A mono input must never
  be handed two-hand material (docs/spec.md I3), and `chooseNext` takes the polyphony — so the first
  `choose` reads the stored input first, then selects. That is why the resolution lives inside `choose`
  rather than in a `begin()` of its own: detekt's `thresholdInClasses: 14` fires *at* 14, and a separate
  entry point plus its shared selection body took `PracticeViewModel` to 16 functions. The selection
  `when` moved to a top-level `PracticeWiring.selectionFor` for the same budget.

- **A permission granted once can be taken back, so the stored PLAY IT is checked against the phone
  every time.** `openingInput(settings, micGranted)` returns the mode *and* whether the stored one was
  refused; a refusal opens on TAP, says so in the words Dewi sees, and **leaves the preference alone**
  so it is honoured again the moment he re-grants. Verified on the emulator by revoking `RECORD_AUDIO`
  with `adb`: the session opened on TAP with the notice and the row still read `mic`.

- **`listenFirstOn` was the fourth control nobody had noticed was dead.** The brief named tempo,
  metronome and input; deleting the caveat would have left "Listen first" as decoration with nothing
  saying so. It now runs the existing `listen()` preview at the end of `load`, and `mayListenFirst` is
  false for a repeat (`Again` starts the transport itself, so a preview would swallow the button) and
  for an input switch (Dewi already heard it). Both the firing and the suppression are logged, because
  "nothing played" is otherwise indistinguishable from a broken preference.

- **`AppContainer.settingsStore` exists so there is exactly one settings store.** `SettingsRoute` used
  to construct its own `RoomSettingsStore`; a session reading a second instance would be two answers to
  one question. The route now takes the container's, and the view model reaches it through
  `SessionPreferences` — segregated from `PracticeWiring` so a caller that wants the tempo cannot also
  reach the corpus, the stores and the judge.

- **`SessionPreferences.remember` is read-modify-write inside the store owner.** The alternative is
  handing the view model a `PracticeSettings` to edit, which is a second copy of the current tempo by
  another name. Verified on the device: toggling the metronome wrote `metronomeOn` and left
  `inputLabel=mic` untouched in the row.

- **The Settings screen used to render defaults until the row arrived.** `collectAsStateWithLifecycle`
  was seeded with `PracticeSettings()`, so for the frames before Room emitted, the screen showed 72bpm
  and "nothing chosen" — and a tap in that window would have saved those defaults over the real values.
  It now collects a nullable and shows a one-line panel until the read lands. The window is short; the
  consequence was silently overwriting every preference at once.

- **`InputMode` carries the stored label, and `InputChoice` reads it rather than spelling "tap" again.**
  The label must equal the adapter's own `AnswerSource.label`, since that is what `StoredSession` and
  `PracticeSettings.inputLabel` hold. `SessionPreferencesTest` pins the tap side against a real
  `KeyboardTapSource`; the mic side cannot be constructed on the JVM (it opens `AudioRecord`), so that
  half is held by the round-trip test and by the on-device check rather than by the compiler.

- **`PracticeIntent.DrillWeakest` never actually drilled the weakest skill.** `choose` cleared
  `result` before the coroutine read `_state.value.result`, so the target was always null and every
  drill quietly fell through to `chooseNext` — the button named a skill (`ui.results.drillTarget`) that
  the session then ignored. The result is captured before the reset now. Found while removing the
  view model's private copy of `drillTarget`, which was the same decision written twice.

- **`PractiseRoute` passes `state.input.polyphony` to `ResultsSheet`.** The parameter had a default of
  `Polyphony.Poly`, so a mic session's results sheet would offer a hand-independence drill that the
  session's own judge refuses.

- **What was verified on the emulator.** Set the tempo ceiling to 47, the metronome off and the input
  to PLAY IT; force-stopped; relaunched. The session opened on MIC at **47bpm** (written 60) with the
  metronome chip off, and the report carried
  `settings applied tempo=47bpm [written=60bpm ceiling=47bpm capped=true] metronome=false
  [stored=false micMutes=true] input=Mic [stored=mic] listenFirst=false [applies=true]`. Then: the
  metronome toggled on the practice screen survived a force-stop; `listenFirstOn` produced "PLAYING FOR
  YOU" on the next launch; revoking `RECORD_AUDIO` opened the next session on TAP with the notice and
  left the row on `mic`; and restoring the ceiling to 73 put a 60bpm exercise back at its own 60.

## :app — Trill, the mascot

- **The crest is a quaver's flag, and that is the whole reason she works as an icon.** Rounded won
  the three-way comparison on legibility, but its own honest weakness was that at rest she said
  nothing about music — off a wire she was just a plump bird. `CREST_SHAPE` replaces the quiff with
  the flag borrowed from the Notehead variant: narrow at the crown, swelling over the top, hooking
  back to a point, with the concave sweep on the underside. Brass body against a violet flag is also
  both brand colours in one silhouette, which is what lets her be a 40dp chip and a launcher icon
  rather than only a picture on a stave.

- **The flag must have air under it or it reads as a beret.** The first attempt hugged the head's
  curve and rendered as a violet cap; the fix was raising the inner edge so the crest detaches from
  the crown at about x=0.465 and only the root is buried in the head. That gap is what makes the
  shape read as an appendage with a hook rather than as a hat, and it is the thing to protect if the
  head proportions are ever changed.

- **`crestAngle` must stay inside roughly ±25°.** Wincing was first drawn at -36° and the flag folded
  flat onto the crown — the beret again, in the one mood where the reader most needs to recognise
  her. Wincing is now -14° and Sleepy -18°, and the droop is carried by the head duck and the brow
  instead. A mood that needs to look more defeated should sink or tilt, not rotate the crest further.

- **The crest is filled as `crest − (body ∪ head)`, not unioned into the brass.** The silhouette used
  for the outline is still the single boolean union of body, tail, head, crest and both mandibles, so
  one chunky stroke rims her everywhere with no seams. Only the *fill* is subtracted, which makes the
  violet meet the brass exactly on the head's own edge — otherwise the crest's root drew an arbitrary
  curve across the crown.

- **`tailShape()` computes its own attachment.** The tail's roots and both control handles are derived
  from the body ellipse at `TAIL_TOP_DEGREES` / `TAIL_FOOT_DEGREES`, so each edge leaves along the
  body's own tangent and the union is smooth by construction. The previous drawing eyeballed the join
  and left a concave notch at about 9 o'clock, visible at 200dp. Note the consequence of the geometry:
  on the left of an ellipse the tangent is nearly vertical, so a tail that points left *must* leave
  steeply and then bend — a tail authored to leave horizontally cannot be tangential, and that is why
  the shape hugs the body before it flares.

- **The bib and the feather creases are clipped to the silhouette.** They used to stay inside by
  hand-checked extents, which is a promise rather than a guarantee; any future tweak to the body
  radii or the bib would have leaked cream past the outline. `clipPath(body)` makes it unbreakable.

- **The open beak's dark gape is `MOUTH_SHAPE` minus its own rotated self.** A static mouth shape
  cannot work: the mandibles converge at the tip, so there is nothing to hide it behind when shut,
  and a fixed wedge pokes past the beak once the jaw rotates. Differencing the wedge against itself
  rotated by `beakOpen` yields exactly the opening at any angle, bounded by both mandibles' inner
  edges, so it can never escape the beak.

- **The eye is drawn as `eye − lid`, not eye-then-lid-on-top.** Painting a feather-coloured lid over
  a filled dark oval left the oval's antialiased edge showing as a thin dark ring above the lid, which
  read as a drawn-on eyelid crease at 88dp and up. One filled path has no shared boundary to alias.

- **Wincing carries a second cue on purpose.** The raised inner brow that does the sympathy is a
  stroke, and a hairline vanishes at 40dp, so the brow's weight now grows with `abs(browSlant)`
  (`BROW_BOLD`) and the wince also opens the beak onto the dark gape and deepens the blush. Colour
  and area survive shrinking; a hairline does not.

- **`CONTENT_LEFT/TOP/WIDTH/HEIGHT` is her drawn extent across *every* mood, not the resting one.**
  The box includes the crest at its highest rotation and the flourish marks, so she never clips a
  crest and — more importantly — never changes size when the mood changes. Shrinking the box to fit
  Idle would make her visibly grow and shrink as verdicts arrive.

- **What the caller owes `TrillOnStaff`.** It sizes everything from the box *height*
  (`STAFF_BOX_SPACES` staff spaces tall, bird `BIRD_STAFF_SPACES` spaces) and anchors her ankles on
  the requested `staffStep`, drawing ledger lines from `LEDGER_FIRST_STEP` outwards. A step far from
  the middle line therefore needs a taller box, and the composable will happily draw her past the top
  rather than shrink her — the pun (height on the staff *is* pitch) only works if she actually moves.
  `Trill` is the default because most callers have no staff geometry to give it.

- **`MascotContract.kt` became `MascotMood.kt`.** With the comparison gallery and its three variants
  deleted, the file's only classlike declaration is the enum, and detekt's `MatchingDeclarationName`
  fails on the old name. `MascotPainter` and `MascotMoods` stayed — the painter is still the seam a
  screen takes a mascot through.

- **What was verified on the emulator.** Every mood at 200dp, 88dp and 40dp, on the manuscript ground
  and on ink, plus `TrillOnStaff` at steps 0, 4 and 8 (two ledger lines, feet on the upper one). The
  crest survives at 40dp in all seven moods, which was the test the design had to pass.

## :app — Trill everywhere else (results, progress, repertoire)

- **A mood is a function of the verdict, never a second opinion about the run.** `moodFor` in
  `ui/results/ResultTone.kt` takes a `ResultTone` and nothing else, so the bird on the results sheet
  cannot reach past the headline into the numbers and reach a kinder conclusion than the sheet did.
  The five tones map onto five distinct faces in quality order — Curious, Wincing, Idle, Delighted,
  Impressed — which is why `Mixed` gets neutral company rather than sympathy: at 70% clean, a wince
  overstates it as surely as a cheer would understate it.

- **`MascotMood.isPleased` exists so the celebration gate is one predicate.** `ResultMoodTest` and
  `ProgressGreetingTest` both police "no pleased face over evidence that did not earn it", and two
  hand-written lists of the happy moods would eventually disagree about whether `Impressed` counts.
  It is a `when` over the enum rather than a set, so an eighth mood fails to compile until someone
  decides which side of the gate it falls on.

- **`ProgressModel` used to hold its own `SOLID_STRENGTH = 0.8`.** Two definitions of *mastered*
  meant `Curriculum` could pass a stage on a skill this screen would not colour as solid — the
  `specTargeting` failure again, one module across. `bucketOf` now asks `SkillState.isSolid`, and the
  bucket keeps its second condition (*and not due*) because dueness is about spacing rather than
  about reading: a skill can be read reliably and still be worth revisiting today.

- **A "personal best" needs four stored sessions and a difference that survives rounding.** Below
  four, "best yet" is arithmetic on nothing (the same floor `trendOf` uses, for the same reason);
  and two runs that both print 70% have not beaten each other on the screen Dewi is reading, so the
  comparison is made on `percent()` rather than on the raw doubles. The line states both figures, so
  the Impressed face is always accompanied by the evidence that earned it.

- **Delighted on Progress needs *every* tracked skill solid, not some threshold of them.** A
  proportion would need a second number nobody has justified, and "most of your skills are solid" is
  a sentence that gets less true the more skills there are. All-or-nothing is rare, which is the
  point: a mascot who is pleased most of the time is decoration.

- **The empty Progress screen is the one place a mascot is the content.** "Nothing read yet" was a
  plain sentence on the first screen a new user reaches, and the sleeping bird is what makes it read
  as *not yet* rather than as *nothing here*. It is also the first-session moment for free: storing
  one session wakes her, and no code had to detect the transition to make that happen.

- **`TrillAside` and `TrillPanel` are the only two shapes she takes beside text**, held in
  `ui/mascot` so the inline and whole-screen forms cannot drift into four slightly different
  paddings. The aside is deliberately small (44dp) — it accompanies a refusal, and a large bird over
  "the mic cannot hear two lines" would make a technical limit look like a scolding. Its `color`
  defaults to `Color.Unspecified` rather than to `onSurfaceVariant` directly, because a parse failure
  must keep the error colour it had before the bird arrived: adding a mascot to a message is not a
  reason to downgrade its signal.

- **"A skill has just gone Solid" is not derivable and is not claimed.** `SkillState` carries a
  current strength and no history, so nothing on Progress can tell a skill that crossed the line
  today from one that crossed it a fortnight ago — and a face that fired on every visit would be a
  celebration of nothing. Delight is therefore attached to the state that *is* derivable, every
  tracked skill solid at once. Detecting the crossing needs a stored previous strength (or a dated
  milestone like `stage_progress`), which is a `:core:database` decision rather than a screen's.

- **Repertoire's header bird is `Curious` and stays that way.** It is the one screen whose whole job
  is waiting on a choice, so the mood is a property of the screen rather than of any data — which is
  also why it is the only permanent, non-reactive placement in the app. Restraint is the point:
  every other bird here is answering a question about stored evidence.

- **What was verified on the emulator (API 35, light theme).** Empty Progress (Sleepy, 132dp),
  populated Progress after a real 0% session (Idle, 76dp — she is *not* pleased about a bad run),
  Repertoire header (Curious, 64dp), the polyphony refusal aside (Wincing, 44dp) and the results
  sheet after a real 0% run (Wincing, 96dp, headline "Rough one", no bloom). The pleased faces were
  read back from a temporary all-moods probe at 96dp rather than from an earned run, because a
  scripted `adb` session cannot play a piece well enough to earn one.

- **Cross-area, unresolved at the time of writing: `ui/practice/SessionMascot.kt` has its own
  `runMood(result)`.** It is a second answer to "which face for a finished run" and it disagrees with
  `moodFor` — an 88% run is `Idle` there and `Delighted` here, a 70% run is `Wincing` there and `Idle`
  here — so the practice screen's bird and the results sheet's bird can contradict each other about
  the same session. `runMood` should be deleted in favour of `moodFor(toneOf(result))`, which is
  `internal` and already visible from that package (it imports `ResultTone` and `toneOf` from it
  today). It also reaches past the tone into `result.extras`/`correct`, which is precisely the second
  opinion `moodFor` exists to prevent.

## :app — the introduction and the path

- **The introduction runs once, and the thing that records it is the placement row.** There is no
  "intro seen" flag, deliberately: every exit from `IntroductionRoute` writes a `PlacementRecord` —
  taking the read writes `Completed`, declining writes `Skipped` — so a separate flag would be a
  second answer to one question, and the stale one would be the one the app believed. `IntroGate`
  therefore has three values rather than two: an **unreadable** journey resolves to `Skip`, because
  dragging a returning reader back through the introduction on a bad row is worse than the
  introduction being missed, and "Meet Trill again" at the foot of the path covers that case.

- **`MIDDLE_LINE_STEP` is the one conversion between Trill's perch and a pitch.** `TrillOnStaff`
  counts half-spaces from the middle line; `StaffGeometry` counts diatonic steps from the bottom
  line. They are the same unit with different origins, four steps apart, and `StaffPitchTest` pins
  the conversion against `StaffGeometry.stepOf` rather than against a table of note names — a table
  would have been a second copy of the staff's geometry, which is exactly the duplication that has
  already cost this repo two bugs.

- **`FocusedSession` is one session driver used twice, not a second one.** A placement probe and a
  stage drill both run `PracticeViewModel` + `PracticeScreen` with the app's own judge, conductor
  and answer source; the only difference is which `PracticeWiring` answers "what should he read".
  That identity is the whole reason a placement is allowed to seed the skill store: it measured the
  same thing an ordinary session measures. **Knowingly duplicated:** its ~50 lines of route plumbing
  (lifecycle pause, key taps, the results dialog) restate `PractiseRoute`'s, because that file is
  another module's to own and the shared part is the *screen*, not the route. If both ever need the
  same change, extract the plumbing rather than editing it twice.

- **`SessionOwner` gives each focused session its own `ViewModelStore`.** Without it a probe's view
  model either outlives the screen — still collecting taps, still holding a conductor — or never has
  `onCleared` called at all, and the transport of a finished probe keeps running under the next one.
  Clearing it in `onDispose` is what makes the placement's five probes five clean sessions.

- **`ProbeWiring` differs from an ordinary session in exactly three ways, and each is deliberate.**
  It reads the probe the `PlacementRead` chose rather than asking the scheduler; it forces
  `listenFirstOn` off, because hearing the music first measures memory rather than reading; and it
  does **not** fold the probe into the skill store. The last is the important one: the placement's
  own conclusion is what seeds the store, and folding each probe as well would count one performance
  twice — under two different rules, which is worse than counting it twice under one.

- **Seeding goes through `SkillUpdateRule`, so there is no second write path.** `RoomSkillStore`
  persists whatever an update rule hands back, so "these are the states now" is just a different
  rule (`AppContainer.seedSkills`), inside the same transaction with the same refusal guard. The
  evidence passed alongside is the probes' `SkillOutcome`s **concatenated, not merged**: the only
  consumer sums them, so merging would be a second tally of numbers `AdaptivePlacementRead` has
  already tallied.

- **`StageAware` is a second, smaller port rather than a `focus` parameter on `chooseNext`.** The
  first draft added `focus: PracticeFocus?` to `PracticeWiring.chooseNext`, which forced every
  implementation — including test fakes in another module's area — to accept a narrowing it had no
  way to honour. A fake that silently ignored it would return material from the wrong stage while
  looking perfectly correct. Only the path ever asks "what now, on *that* rung", so only the app's
  own wiring implements it.

- **An ordinary session is narrowed to the rung Dewi stands on, and that is resolved rather than
  passed.** `AppPracticeWiring.chooseNext` asks `Curriculum.currentStage(states)` itself. Null focus
  means "wherever he stands" and is deliberately *not* `PracticeFocus.None` — "no narrowing at all"
  and "narrowed to the current stage" are different requests, and only one of them is what a session
  wants.

- **The path stores no position and derives everything.** `pathOf` reads the skill states through
  `Curriculum`; `PathRow.passedOnEpochMillis` is a dated event from `stage_progress` and never a
  claim about today, which is why `standing` and `passedOnEpochMillis` are separate fields. A stage
  passed out of order still reads as passed while `current` stays the first gap — held by a test,
  because it is the case where a progress bar and a curriculum disagree.

- **`Trill`'s mood on the path is about the calendar, never about quality.** Curious on a first run,
  Sleepy when no run is going, Idle otherwise — and never Delighted, because a bird pleased on the
  path would be pleased about nothing in particular. The same rule caught a real defect on the
  placement summary: it showed `Impressed` after a read where every note was missed, on the grounds
  that four skills had been *measured*. Measuring is not a result; `placedMood` now sparkles only
  when the read actually landed above the first rung.

- **The keyboard wall is docs/spec.md I3 applied to progress.** A microphone cannot hear two hands,
  so `playableBy` forces `bothHandsActive = false` and `HandIndependence` is never tagged — meaning
  a mic reader can never pass stage 4. `isHearableBy` is the one definition of that rule, asked by
  the drill route (which must not propose it) and by the path (which must say so out loud). Without
  the sign, the honest refusal becomes a silent wall, which is the failure I3 exists to prevent.

- **Diagnostics has no tab.** Six tabs to fit a developer tool would crowd the five things the app is
  for; `Destination.inBar` keeps it in the enum and out of the bar, reachable from the foot of the
  path where a "something is wrong" tool belongs. The path leads because it is the front door: it is
  where Dewi is on the journey and where the next session starts.

- **Sessions started from the path run inside the path.** Tapping a rung does not throw him at a tab
  — finishing it puts him back where he chose it, which is most of what makes the path feel like a
  place rather than a menu.

- **The introduction paints its own ground and its own insets.** Inside the shell a `Scaffold` does
  both; the introduction is the whole window and nothing does, so without the `Surface` the bare
  window background shows through — dark ink under a light theme, which is exactly how the placement
  read looked on the emulator before it existed, with the banner under the status bar as well. Both
  were invisible to every test and obvious in one screenshot.

## :app — the practice screen and the session (2026-08-15)

- **Trill is present during a read, and small.** She sits in the transport row, *below* the staff, at
  44dp while the notation moves and 68dp when it is stopped (`animateDpAsState`, so the row never
  jumps — it is a fixed 84dp tall). The reasoning against putting her beside the staff is the one
  written into `MascotMood.Listening` itself: reading ahead is the whole skill, and anything moving
  next to the notation competes for the attention the app exists to train. Below it she is company,
  and between runs — where the eye already goes for the play button — she is allowed to be bigger.
  She reacts only once the run ends, which is what `lastRun` is for.

- **`PracticeUiState.lastRun` survives `dismiss()` and `result` does not.** The results sheet is a
  dialog over the practice screen, so at the moment a run settles her reaction is hidden behind it;
  the state Dewi actually sees her in is the one *after* he dismisses the sheet. Clearing the mood
  with the sheet would leave her Curious over a run that had just gone badly. `listen()` and every
  `load()` clear it, because a preview that reached the end is not a run she has an opinion about.

- **`sessionMood` defers to `ui/results/moodFor` for a finished run.** That function is the app's one
  mapping from a verdict to a face; a second one here disagreed with it by two whole tones (an 88%
  run was `Idle` on one screen and `Delighted` on the other) before it was deleted. `SessionMascotTest`
  asserts the agreement directly over every score from 0 to 12 out of 12, so the two cannot drift
  apart again in silence.

- **The setup panel folds away while the music moves, and that is not hiding a control.** "Hear it"
  and "Try another" are actively wrong mid-run — both replace what is loaded and end the run — and
  the input chips reload the session. Everything the panel offers is a between-runs decision, so it
  collapses on `Running`/`CountingIn` and returns on pause or finish, which is also what gives the
  staff its extra height at the one moment height matters. Nothing *true* is hidden: the extras
  count, the refusal and the notice all stay.

- **A run in progress is paused — and therefore saved — before anything replaces it.** `load()` now
  begins by pausing a running conductor. Switching input, opening a piece from Repertoire or asking
  for something else used to call `conductor.stop()` straight through `load`, so the session's
  verdicts were dropped without ever reaching `SessionStore.save` — docs/spec.md I4 failing on three
  ordinary routes rather than on a crash.

- **A tap made before the run began is not judged in it.** `KeyboardTapSource` is a 256-deep
  `Channel` shared by the whole app, so a key pressed while the transport was idle waits there and is
  delivered the instant a session starts collecting — where it is judged as `Verdict.Extra`, i.e. the
  app reporting a note Dewi did not play in that run (docs/spec.md I2). `play()` records
  `runOpenedAtNanos` from `conductor.nanosFor(conductor.position())` immediately after `start()` and
  after each `resume()`, and the collector drops anything stamped earlier, counted as
  `input/notesPlayedBeforeThisRunBegan` rather than silently. The two timebases really are
  comparable: taps carry `MotionEvent.uptimeMillis` and the Conductor runs on `System.nanoTime()`,
  both CLOCK_MONOTONIC on Android — which is exactly what the `dewidebug tap … at=… now=…` line was
  added to let a report check. `StaleTapsTest` was verified by deleting the guard and watching it
  fail. The deeper fix (draining the channel when a session opens) belongs to `:core:practice`.

- **A finished run turns the page back.** The last thing a run does is scroll the music off the left,
  so the screen it *ends* on is a blank sheet — the least useful moment to show nothing. While
  `transport == Finished` and not previewing, the staff renders from `Ticks.ZERO` with the playhead
  at the opening note, so the whole exercise is visible with its verdict colours still on it. It does
  not weaken docs/spec.md I1: that invariant is about the three readings agreeing *while a note is
  being judged*, and here the transport is stopped and `judgeState` is null. Judged noteheads keep
  their colours because `NoteStyling` only consults `position` for notes with no verdict.

- **The keyboard is hidden in PLAY IT mode.** With the mic as the answer source nothing collects from
  `KeyboardTapSource`, so every key press was a control that silently did nothing — and now it also
  fills the tap buffer that the guard above has to throw away. `InputMode.needsKeyboard` is a `when`
  rather than `!= Mic`, so a third adapter fails to compile until someone decides.

- **The toggles are brass, never mint.** They were `tertiaryContainer` for one build, which is the
  same mint that means *correct* on a notehead; a green chip beside a staff reads as a verdict.
  Brass is the transport's own colour and carries no judgement.

- **The stage is read for the header, and the wiring narrows the choice separately.** `StageSource`
  is `suspend () -> Stage?`, supplied by `PractiseRoute` as `container.standingStage()` — the same
  accessor `AppPracticeWiring` uses, so there is one derivation of "where does he stand" rather than
  a private copy of `Curriculum.currentStage`. Null means it could not be read and the pill is absent;
  it never falls back to "stage 1", which would be a claim about his reading drawn from a failed query.

- **The header lost its `InputChip` and the reason line gained a second line.** The chip repeated the
  TAP/MIC state that the setup panel's own chips already carry — the same fact twice on one screen —
  and the scheduler's sentence was being cut mid-skill at one line ("…to drill treble clef, middle of
  t…"), which is precisely the evidence docs/spec.md I5 asks Dewi to be able to *see*.

- **The count-in is Trill's.** She dips on every beat (`translationY` driven by the same pulse the
  ring uses) and goes still on the last one, where the caption says "Bar 1 on the next beat" and the
  ring fills violet — the one brand colour carrying no verdict meaning. The beat dots exist because
  the digit alone says how many are left but not how long the count-in *was*; `countInBeats` is
  captured in `play()` from `conductor.countInBeatsRemaining()` rather than inferred from the highest
  value the UI happened to observe.

- **`SessionSetup` is nullable on `PracticeScreen`, and `ui/journey`'s `FocusedSession` relies on it.**
  That screen drives its own session for a placement probe and supplies its own header; offering it
  "something else to read" mid-measurement would corrupt what is being measured. The screen's flat
  parameter list was kept for the same reason — it is another area's call site, and breaking it to
  tidy my own signature would have been a change to their file by proxy.

- **`SessionFakes.kt` is the test graph, extracted from `SessionReadsSettingsTest`.** Three test
  classes in this package now need it, and the second copy would have been the duplication the DRY
  law names outright. `FakeWiring.tapSource` is held rather than constructed per `sourceFor` call, so
  a test can push a tap into the very source the session collects.

- **What was verified on the emulator (API 35, light theme), 2026-08-15.** The idle screen with the
  stage pill and the full reason line; the count-in card with Trill dipping (beat 3 of 4); a run
  under way with the setup panel folded and Trill at 44dp; the results sheet over it; the finished
  screen with the page turned back, Trill Wincing over a real 0% run and the readout in coral; the
  PLAY IT notice; and PLAY IT with the keyboard gone. The pleased faces were not earned on the
  device — a scripted `adb` session cannot play an exercise well — so they rest on
  `SessionMascotTest` and on the results sheet's own device check.

- **Her cost on a scrolling frame was measured, not assumed — and it is not nothing.** Back-to-back
  `dumpsys gfxinfo` over a 14-second run on the api35 emulator, the only difference being whether
  `Trill(sessionMood(state), …)` in `TransportCluster` was a `Spacer` of the same size:

  | | total frames | janky | 50th | 90th | slow UI thread |
  |---|---|---|---|---|---|
  | with Trill | 459 | 418 (91%) | 81ms | 109ms | 405 |
  | without | 504 | 276 (55%) | 65ms | 85ms | 266 |

  She rebuilds a boolean-union path on every draw and her breathing invalidates every frame, so a
  44dp bird costs roughly 16ms of median frame time *there*. Two things stop that being a verdict.
  The baseline is already 65ms — this is a software-rendered emulator on a memory-capped WSL VM, not
  a phone — and **no verdict depends on it**: the judge and the metronome read `Conductor`, never the
  frame clock, so a dropped frame moves the drawn playhead and nothing else. The decision taken is to
  keep her, because the emulator is a poor proxy for the phone and the brief asks for company during
  the read. **If the scroll stutters on Dewi's phone, the fix is one line** — wrap that call as
  `if (running) Spacer(Modifier.size(bird)) else Trill(…)`, which removes her for the length of a run
  and leaves her reacting at the end, the behaviour the brief already blesses as legitimate. Re-run
  the same A/B on the phone before changing it; do not decide it from the emulator numbers above.


## core/score/src/main/kotlin/com/dewijones92/primavista/score/Admission.kt

- **Why `admits` exists at all, and why it is not built on `SkillTag`.** The obvious way to place a
  real piece on the ladder is to compare the skills it demands against the skills a stage teaches:
  `skillsOf(score) ⊆ curriculum.skillsThrough(stage)`. That does not work, and the reason is worth
  keeping. A `Stage.skills` set is a **teaching claim** — "this rung introduces the bass clef" —
  not an enumeration of everything material at that rung may contain. No stage lists
  `Leap(semitones=5)` or `Accidental(Natural)`, because the generator's `DifficultySpec` governs
  those, so the subset test rejects every bar of music ever written. Measured on the OpenScore
  Lieder corpus: **8,744 of 8,798 passages were unplaceable that way**, and the 54 that placed were
  windows containing nothing but rests. The `DifficultySpec` states the dials exactly, so it is what
  the check reads.

- **Three dials are deliberately not checked, and each exclusion cost a measurement.** *Time
  signature*: a spec pins one because a generator must write in something, but no `SkillTag`
  models metre, and refusing 3/4 at a rung whose spec says 4/4 accounted for 50,712 refusals — a
  difficulty this ladder never claimed to teach. *Exact key*: a spec names one key; a rung reads
  signatures **up to a size**, so the check is on `accidentalCount`. *Bars and tempo*: decisions
  made when practising, not properties of the music.

- **The accidental check must ask the key, not the note.** The first version compared
  `note.pitch.alter` against `spec.allowedAlterations` and refused every exercise the generator
  wrote in G major, because an F♯ there is `Alter.Sharp` on the pitch while being no accidental at
  all on the page. `printedAccidental` asks `KeySignatureAlterations.impliedAlter` — the same
  function `DerivedScoreSkills` uses — so the two can never disagree about what a reader sees. The
  property test `every exercise a spec generates is admitted by that spec` is what caught it.

## core/score/src/main/kotlin/com/dewijones92/primavista/score/Excerpt.kt

- **Why a passage type had to exist before repertoire was worth shipping.** Graded whole, all 1,353
  readable Lieder songs land in one bucket ("harder than the last rung"), because a whole song
  contains every reading skill somewhere in it. That is one rung with a lot of pages, not a ladder.
  Windowed, the same songs place across stages 6 to 10 — and **window length is itself difficulty**:
  735 passages placed at four bars, 140 at eight, 17 at sixteen. That measurement is why
  `Screener.DEFAULT_PASSAGE_BARS` grades at several lengths rather than picking one.

- **A note that starts inside the window is kept whole even if it rings past the last barline.**
  Clipping it would produce a duration nobody could notate, and the layout engine draws what the
  `Duration` says.

- **`hasTieInto` looks for the surviving partner rather than clearing every tie.** A note tied from
  a predecessor that the cut removed is now attacked, and leaving `tiedFromPrevious` set would hide
  it from `Score.attackedNotes` — so the judge would never expect it and never score it. A note
  whose partner is still inside the window keeps its tie, which is why the check matches on pitch,
  staff, voice and end tick rather than simply blanking the flag.

## tools/repertoire

- **The import tool is built on the app's own parser, grader and curriculum on purpose.** A piece it
  accepts is one the phone will read identically; a tool-side copy of the screening would make
  "imports cleanly" a claim about the tool rather than about the app. It is a separate Gradle module
  outside `:app`'s dependency graph, so none of it ships.

- **`DropKind` classifies by "does this leave a hole", not by "was something lost".** The app draws
  from the parsed score, so anything the parser dropped is absent from the page *and* from the
  expectation, and the two still agree. What matters is whether the remainder is coherent music.
  Checked rather than assumed: `readNoteElement` returns the file's own `occupied` duration even for
  notes it refuses, so no drop ever shifts the timeline. Reclassifying grace notes, cue notes,
  `<notehead>` and plain repeats as cosmetic on that basis took the corpus from 41% to 93% usable.
  `<ending>` stays material — playing a first-time bar straight into a second-time bar is a passage
  that never existed.

- **The corpus is a sparse clone, not a vendored copy.** `~/code/primavista-corpus-src/Lieder` at
  commit `6b2dc542ce2e8aa4b78c8ee62103b210efc07015` (2026-04-07), 1,462 `.mxl` files, 18.6 MB. Only
  the 25 selected files are committed here, byte for byte as published, so provenance survives and
  the file the app parses is the file that was screened. Re-run with
  `./gradlew :tools:repertoire:run --args="report --corpus <dir> --commit <sha>"`.

## app/src/main/kotlin/com/dewijones92/primavista/di/ShippedRepertoire.kt

- **What the Repertoire tab's blank wait actually was, measured rather than guessed.** The GC log
  is the instrument: `adb logcat | grep "GC freed"` on the app process, first and last timestamp.

  | | span of back-to-back GC | collections |
  |---|---|---|
  | sequential parse, whole event list filtered per window | **24s** | 65 |
  | after `covers` short-circuits and `Passages` binary-searches | ~20s | — |
  | parallel `async` per piece | **12s** | 27 |

  Each collection freed 30–50MB in a 26MB heap, so it is allocation-bound and the **DOM** is where
  it goes — not the windowing, which is 59ms warm on the JVM. Three things follow. Parallelism
  helps but does not fix it. The remaining cost is inherent to parsing 500KB of compressed MusicXML,
  so the real fix is to not do it at startup (`docs/todos/repertoire-load-cost.md`). And the emulator
  is a memory-capped software VM, so **do not decide the fix from these numbers** — measure the
  phone first, exactly as the Trill frame-time note above says.

- **Streaming the rows is a separate win from making it faster, and worth having either way.** A
  screen that shows twenty-eight skeletons for ten seconds and then everything at once reads as
  broken; one that fills reads as busy. `parsed` is a `StateFlow` each piece appends itself to, and
  `PieceParse` carries its own `passages` so a row is *finished* when its coroutine ends rather than
  when the whole corpus has been read.

- **Two caches doing the same work is a DRY bug that only shows up as latency.** For one commit the
  Repertoire tab (`ParsedCorpus`) and the scheduler (`AppPracticeWiring`) each parsed and windowed
  the corpus independently. Both were correct; together they did everything twice. Unifying them
  made the tab *slower* in wall-clock — one consumer now waits on the other's computation instead of
  racing it — which is the right trade and worth knowing before reading it as a regression.

## core/notation — a false alarm worth not repeating

- **The long verticals below the bass staff are stems, not overshooting barlines.** Eyeballing a
  device screenshot said the grand-staff barlines ran past the bottom staff line. Measuring the
  pixel columns said otherwise: staff lines at y=804/837/870/903/936 (treble) and
  1200/1233/1266/1299/1332 (bass), barlines at x=520/741/962 spanning **804..1335** — exactly top of
  the upper staff to bottom of the lower, which is what `emitBarlines` says. The lines 10px to their
  right, ending at 1388, are 3.5-staff-space down-stems on low bass notes, which is correct
  engraving. The `StaffPngRenderer` output of the same piece confirms it at a glance. Measure the
  image; do not read it.

## core/score/.../Exercise.kt — `key` versus `maxKeyAccidentals`

- **The path capped at one sharp for all ten rungs, and nobody noticed because one field was doing
  two jobs.** `DifficultySpec.key` says what to *write* in — a generator must pick something — and
  admission was reading it as what a level can *read*. Stage six is called "Keys"; it set
  `key = KeySignature(1)` and no later rung touched it, so a reader could finish the entire path
  having never met a B flat, and `docs/journey.md`'s stage nine ("the corpus, at tempo") could not
  happen. Measured on the corpus, that single conflation produced **44,335 refusals** and left
  **236** pieces placeable out of 1,353. Splitting the dial took it to **805 pieces**, from 71
  composers to over 100, and 735 placeable passages to 3,410.

- **The floor is derived, not required, because `copy` does not re-evaluate defaults.** The first
  version had `require(maxKeyAccidentals >= key.accidentalCount)` in `init`. Every existing
  `spec.copy(key = …)` in the codebase and the tests keeps the *old* ceiling, so the precondition
  turned ordinary copies into crashes — four tests failed instantly. `readableKeyAccidentals` is
  `maxOf(maxKeyAccidentals, key.accidentalCount)` instead: the illegal state cannot be represented
  rather than being caught at runtime, which is rung 1 of CLAUDE.md's ladder instead of rung 4.

- **A stage may only CLAIM the keys its own material writes.** `CurriculumTest` asserts that every
  skill a stage claims is one its base spec actually generates, which is why stage six claims only
  `KeyReading(1)` even though it now reads two accidentals. That is the right invariant and it is
  also the tell that the model is one dial short — a stage that teaches keys ought to write in more
  than one. Recorded as `docs/todos/generate-in-more-than-one-key.md` rather than bodged by
  loosening the test.

- **The codec change is additive on purpose.** `maxKey` is written but read with a default of the
  key's own size, so `VERSION` stays at 1 and a session stored before the split is still replayable
  from a report — the default is exactly what those rows meant.

## core/practice/.../ReadingLead.kt

- **A bar of lead blanks a phone.** The classic paper exercise is a card over the bar you are
  playing, which is why `OneBar` exists — and on the api35 emulator it hides everything. The staff
  zooms to fit `gutter + MIN_READ_AHEAD_SPACES`, which is roughly a bar and a half of a generated
  exercise, so a cover four beats ahead of the playhead runs off the right edge and
  `coerceAtMost(size.width)` paints the whole canvas. That is the drill working exactly as
  specified on a screen too small to hold it. `choices` stops at two beats; `OneBar` is kept as a
  tested value because a tablet may yet earn it. Seen at one beat: cover ends mid-staff with the
  next half note clearly readable beyond it, which is the exercise.

- **The cover is not clamped to the start of the piece, on purpose.** During the count-in the
  Conductor's position is negative, so `coversUpTo` returns a position at or before bar one and
  nothing is covered — the count-in is when you read the opening. Clamping to zero would hide bar
  one before Dewi had ever seen it.

- **`PracticeChange` replaced `PracticeToggle` because a dial is not a toggle.** The reading lead is
  a four-state choice, and adding it as a second entry point on the view model would have meant
  every future preference adding another. One sealed type, one `change(…)`, and `remembering(…)`
  lifted out as a free function saying what is persisted — Echo deliberately is not, because the
  session mutes it for the mic and a stored value would fight that.

## core/practice/.../SessionReplay.kt — what makes spec I7 a property

- **The replay records INPUTS, never the outcome.** A report that carried the verdicts alone could
  be read but never checked; one that carries the music, the clock and every note heard can be
  *re-judged*. `claimed` travels alongside precisely so a replay can be compared against what the
  app said — and `SessionReplayTest` includes a test that forces the claim wrong and asserts the
  replay disagrees, because a comparison that cannot fail proves nothing.

- **`ClaimedVerdict` is kind + dt, not the whole `Verdict`.** The rest of a verdict is derivable
  from the note it answers to; the `dt` is not, and the same verdict reached at a materially
  different `dt` is a timing drift worth catching. Derived in one place (`ClaimedVerdict.of`) so the
  encoder and the comparison cannot disagree about what "the same verdict" means. A `Verdict.Extra`
  encodes as index `-1` — the sentinel the *judge* refuses to hold in memory is fine as a wire
  value, because nothing indexes an array with it.

- **`InputLatency` travels whole.** The first cut carried `latencyMillis: Int`, which lost both the
  fraction and the provenance. An assumed 60ms and a measured 60ms apply the same correction and
  justify completely different confidence in the verdict built on them, and the spec asks for
  "which of the two it was".

- **`SpecText` is a port, and that is not ceremony.** `:core:practice` cannot see `:core:database`,
  where `DifficultyCodec` already encodes a difficulty spec and already makes stored sessions
  replayable. Re-encoding it here would be a second encoding of one thing — the duplication this
  repo has been bitten by twice — so the app, which sees both, hands the existing one across.

- **A pause is part of the clock, so the legs travel.** `Conductor.pauseLegs()` exists only for
  this. A replay that lost them would rebuild an unbroken tempo map and re-judge every note after
  the pause as late, which is the exact bug the judge's `retime` was added to fix.

- **Every failure to rebuild is a stated reason.** A piece a later build no longer ships, a report
  truncated mid-block, a spec this build cannot read — all come back as `Lost`/`Unreadable` with
  text. A blank would read as "nothing went wrong", and "no session has been played" and "the run
  was lost" are different situations.
