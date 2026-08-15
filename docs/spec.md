---
title: What PrimaVista is, and what must never break
kind: spec
status: living
updated: 2026-08-07
---

# What PrimaVista is, and what must never break

Written at scaffolding time rather than retrofitted, because this app's failure modes are
predictable: it is a stopwatch with a staff drawn on it, and every hard bug it will have
is a timing bug or a lying verdict. Better to name them before the code exists.

This is deliberately not a feature list — that lives in [`features/`](features/_index.md)
and [`todos/`](todos/_index.md). This is the shorter, harder document: the handful of
things that must be true every time, why each one is here, and whether we can actually
prove it today.

## What it is

PrimaVista is **one person's sight-reading trainer**. Dewi's. Installed on his phone
through Obtainium from this repo's releases. There is no second user, no store listing, no
support burden.

That fact cuts both ways, exactly as it does in Totum:

- **It lowers the bar on breadth.** No score editor, no composing, no sharing, no social.
  An unbuilt feature nobody asked for is not a gap.
- **It raises the bar on honesty.** With one user there is no aggregate to hide behind. If
  the app tells him he played a wrong note when he did not, he will believe it, practise
  the wrong thing, and the only evidence that ever existed was one person's suspicion. A
  training app that scores wrongly is worse than no app, because it teaches.

The goal, in his words (2026-08-07): *"i want to become fluent deeling with music note
notation"*. Not to collect features — to actually be able to read.

## The core loop

Everything below is one behaviour: **a piece scrolls past at tempo, you play along, and
the app tells you the truth about how you did.**

Each invariant states what must be true, why it earns a place here, and what proves it.
"Proven" means something fails when the behaviour breaks — not that it was seen working.

### I1 — The staff you see, the time the music is at, and the note being judged all agree

One musical position, one source. If the playhead is drawn at bar 3 beat 2, then the note
being judged is the note at bar 3 beat 2, and the metronome's click is on that beat.

**Why it is here:** three things independently derive from time — the scroll offset, the
judging window, and the audible click — and any two of them drifting produces a bug that
feels like "the app is wrong about my playing" while being a rendering fault. Totum's
whole class of playback bugs came from time being read in more than one place.

**How it is held:** `Conductor` is the only converter between musical and wall time, over a
`NanoClock` port. The scroll offset, the judge's window and the metronome all ask it. No
component keeps its own idea of "now", and nothing derives time from recomposition.

**Proven by:** JVM tests in fake time asserting all three readings agree across tempo
changes, a count-in, and a pause/resume. *Status: held by `TempoConductorTest` — "the scroll
offset and the judging window read the same now" samples `position()` against `ticksAt(clock)`
either side of a pause; "the count-in counts down and the position crosses zero" and "a count-in
starting mid-piece still lands on the requested position" cover the count-in; "changing tempo
hands the position over without a jump" covers a tempo change; and "nanosFor and ticksAt are
exact inverses" pins the mapping over five tempi and three beat units.*

### I2 — A verdict is about the note you actually played

If the app says `WrongPitch`, the pitch was wrong. If it says `Late`, the note was late by
the amount stated, measured from a timestamp that came from the input device rather than
from when the app noticed.

**Why it is here:** it is the entire product. A wrong verdict trains the wrong thing.

**How it is held:** timestamps are taken at the source (`MotionEvent.eventTime` for taps,
`AudioRecord` frame position for the mic) and converted into the Conductor's timebase once,
at the boundary. `PerformanceJudge` is pure, so every verdict is reproducible from its
inputs alone.

**Proven by:** `PerformanceJudge` unit tests over hand-built note sequences including the
nasty cases — grace-note-early, note held across the boundary, two notes inside one window,
a trill of extras. Plus a round-trip test: generate a *perfect* performance from a `Score`
and assert the judge finds zero faults, which catches whole-model drift that per-case tests
miss. *Status: held by `WindowedJudgeTest` — "a perfect performance has no faults at all" is the
round trip, with "two notes inside one window", "a trill of unexpected notes…", "a tied
continuation is never judged" and "an exact pitch is preferred to a nearer wrong pitch" as the
nasty cases, and "the live fold and judgeAll reach identical verdicts" holding the live and batch
paths to one answer. Reproducibility is held by `TempoConductorTest` — "a perfect performance
across a pause re-judges identically from its own snapshot", which re-judges the recorded notes
after the transport has moved on and gets the same verdicts. "A wrong note played on time stays a
wrong note at any tempo" holds the verdict itself against the tempo, since a fixed matching window
turned a wrong note at 200bpm into the next note played early.*

### I3 — The app never scores you on something it cannot hear

Mic mode judges monophonic lines. On polyphonic material it **refuses, and says why** —
never guesses, never silently marks the notes it happened to detect.

**Why it is here:** this is the one place where the honest answer and the impressive answer
differ. Polyphonic transcription from a phone mic is a research problem; a plausible-looking
score derived from a partial detection is the worst possible outcome for a training app.
The pattern — return a `Refused` carrying its reason rather than a wrong answer — is lifted
directly from Totum's `routeNow`, where the same shape of question had been silently
answered two different ways.

**How it is held:** an `AnswerSource` declares its `Polyphony`. Starting a session with a
mono source against a polyphonic score yields `Refused(reason)`, surfaced in the UI as an
offer to practise hands-separately, and written to the diagnostics log.

**Proven by:** a test that a mono source + polyphonic score refuses, and that the refusal
reason names the bar. *Status: held by `WindowedJudgeTest` — "a mono source on a polyphonic score
is refused, naming the first polyphonic bar", "a held left hand under a moving right hand is
polyphonic, though no onset is shared" (the texture an onset-based gate waved through), "a mono
source on a monophonic score is accepted" and "judgeAll refuses rather than scoring what the input
cannot hear", which closes the bypass of judging without asking. `SpacedPracticeSchedulerTest`'s
"a mono input is never handed material its own judge would refuse" carries the same gate back into
what the app recommends, generated exercises included. The overlap test itself lives once, in
`:core:score`, because `Score.polyphony` is the only definition.*

### I4 — What you practised is not lost

A finished session's per-note verdicts and the skill state they update survive the process
dying, the phone rebooting, and the app being updated.

**Why it is here:** the scheduler is only as good as its history. Losing a session loses the
evidence of what is weak, which silently degrades every future session choice.

**How it is held:** Room, written at session end (and at pause), with skill state derived
from stored verdicts rather than held only in memory.

**Proven by:** instrumented tests round-tripping a session and re-deriving skill state.
*Status: held, on a device, by 39 instrumented tests in `:core:database`. `SessionStoreTest` covers
the round trip — "every verdict kind survives the round trip through SQLite", "several extras in one
session are all kept", "a generated session keeps its seed and spec so it can be replayed" (which is
what makes a stored session reproducible at all), and "saving twice replaces the session rather than
duplicating it", since a session is written at pause as well as at finish. `SkillStoreTest` holds the
derivation — "the rung climbs across sessions because it is stored rather than rederived" and "a
lapse takes the stored rung back down rather than leaving it stale". `SkillRepetitionMigrationTest`
covers "the app being updated" specifically: "a version one skill row keeps its strength and starts
on the bottom rung" runs the real migration against the exported v1 schema.*

### I5 — What you get next reflects how you actually did

The next piece or generated exercise is weighted towards the reading skills that are weak
and due. Getting better at bass-clef ledger lines must visibly change what it gives you.

**Why it is here:** without it the app is a metronome with a staff. This is the difference
between practice and a trainer, and it is the whole justification for storing per-note
verdicts rather than a session score.

**How it is held:** every note carries `SkillTag`s; verdicts debit them; the scheduler picks
weighted by weakness and dueness; the generator can synthesise material that targets a
specific weak skill when the corpus has nothing suitable.

**Proven by:** deterministic scheduler tests — feed a history where one skill is failing and
assert the next selection targets it; feed a mastered history and assert it moves on.
*Status: held by `SpacedPracticeSchedulerTest` — "a skill that keeps failing is what next targets"
and "mastered and nothing due moves on to a piece rather than drilling" are the two named here;
"a lapse puts the skill back on the bottom rung, not one rung down" holds the spacing itself,
since a ladder that only stepped back hid a just-failed skill for roughly ten days after one good
session; "nothing suitable falls back to a generated exercise targeting the weakest skill" and
"the spec a generated choice carries is the generator's own, not a second opinion" hold the
generator route; and "the same inputs and seed give the same choice" keeps a choice reproducible
from a report.*

### I6 — Nothing leaves the device unless you shared it

**Why it is here:** it is cheap to guarantee and expensive to retrofit, and it makes the
verbose diagnostics rule safe: a report can be as detailed as we like because it only ever
goes where Dewi sends it.

**How it is held:** the app has **no `INTERNET` permission**. Not a policy — an absence.
Reports leave only through a share sheet.

**Proven by:** the manifest, and a check asserting `INTERNET` is absent from the merged
manifest, so adding it later is a deliberate, visible act rather than a transitive
dependency's side effect. *Status: held by a CI step ("Assert no INTERNET permission") rather than
by a test, and the distinction is worth stating: it runs on every push but not on a local
`./gradlew` run, so a locally-introduced dependency that pulls the permission in is caught at push
time, not at build time. The step counts the merged manifests it found and fails when that count is
zero, because a check that finds nothing to check and reports success is the failure mode here.*

### I7 — A report from his phone can settle whether I1–I6 held

Every invariant above must be checkable after the fact from a diagnostics report alone,
because that is the only evidence that will ever exist.

**Why it is here:** Dewi's standing instruction, and Totum's most expensive lesson twice
over. A feature that works but cannot be shown to have worked is not finished.

**How it is held:** the `Diag` ring buffer records decisions **with their inputs** (see
`CLAUDE.md`), high-frequency events are counted rather than logged per-event, and every
report carries the build's git SHA so a finding can be dated against the code. On top of that the
report carries a **replay block** — the inputs to the judgement rather than a summary of its
output: how to rebuild the music, the tempo map including every pause that happened, the input and
its latency *with the provenance of that latency*, every note heard with its time and confidence,
and every verdict the app claimed with its `dt`.

**Proven by:** `SessionReplayTest` (10 JVM tests), which plays a session, writes the block, reads it
back out of a report's worth of surrounding text, rebuilds the music **from the seed alone**, and
re-judges — then asserts the verdicts match what the report claimed. A companion test forces the
claim wrong and asserts the replay disagrees, so the comparison has teeth rather than comparing a
value with itself. `LastSessionTest` proves the same block survives the real difficulty codec the
app ships. A run that cannot be rebuilt — a piece a later build no longer ships, a truncated report,
a spec this build cannot read — comes back as a **stated reason**, never as a blank.

Seen end to end on a device, 2026-08-15. A real report from the emulator carried
`score=generated 1786801765805 <full spec>`, `legs=-40320@1095021410707`, seven `played=` lines with
pitch, nanos and confidence, and verdicts including `claimed=2:WrongPitch:-222.420635` and
`claimed=-1:Extra:` — the sentinel-free index the judge insists on, travelling intact.

*Still outside the guard: it proves a report can settle **I2** (the judge's verdicts) exactly, and
carries what I1, I3 and I5 need. It does not yet replay the layout, so a claim about I1's scroll
geometry is still read rather than recomputed.*

That said, the report has already twice done in practice what this invariant asks of it: it found
the origin-zero timing bug on 2026-08-07 (a tap at uptime 29,542s landing at tick 555,868,801 in a
120,960-tick score, from one line that logged the tap's timestamp beside the clock's), and before
that it distinguished "the touch never arrived" from "the flow never delivered it". Neither was
visible to any test. That is evidence the design works; it is not a substitute for the guard.

## The weakness

Stated plainly so nobody mistakes the above for a description of working software:

- **I7 is the one still proven by nothing.** I1, I2, I3 and I5 name the JVM tests that hold them,
  I4 is held by 39 instrumented tests on a device, and I6 by a CI step. I7 — that a report can
  settle the other six — has good foundations and no guard, which matters more than the count
  suggests: it is the invariant the other six are *checked through* once the app is on a phone and
  nobody is watching.
- **Latency is unmeasured.** I2's promise is about numbers, and the audio path's real
  round-trip latency on Dewi's phone is currently unknown. Until it is measured and
  compensated, mic verdicts carry a systematic timing bias of unknown size. This is the
  single most likely way the app lies to him.
- **The corpus is tiny.** The generator covers for it, but "read real pieces" is the point
  and a handful of hand-authored public-domain excerpts is not a repertoire.
- **The MusicXML subset is a subset.** Real scores in the wild use far more of the spec than
  we parse. A file that parses to *nearly* the right thing is more dangerous than one that
  fails outright, so the parser must be loud about what it dropped.
