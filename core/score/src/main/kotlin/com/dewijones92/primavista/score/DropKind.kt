package com.dewijones92.primavista.score

/**
 * Whether a dropped element leaves a **hole** in the music, or only takes away decoration.
 *
 * The criterion is not "was something lost". The app draws from the parsed score, so anything the
 * parser dropped is absent from the page as well as from the expectation, and the two still agree.
 * The criterion is whether what remains is still coherent music: a missing slur changes nothing you
 * read, a missing triplet leaves a silence where three notes should be, and reading that silence is
 * practising a lie.
 *
 * Measured rather than assumed. The parser advances its cursor by the file's own duration even for
 * notes it refuses, so no drop ever shifts the timeline — which is what makes "does it leave a
 * hole" the only question left. Applying this to the 1,462-file OpenScore Lieder corpus took the
 * share of files safe to practise against from 41% to 93%, with the losses all named.
 */
public enum class DropKind { Material, Cosmetic }

/**
 * Elements whose loss leaves the music coherent. Everything not listed is [DropKind.Material], so a
 * parser change that starts dropping something new fails closed rather than quietly widening what
 * counts as safe.
 */
private val cosmeticElements = setOf(
    // Decoration and expression: nothing structural depends on them.
    "slur", "articulations", "ornaments", "technical", "dynamics", "fermata", "arpeggiate",
    "glissando", "slide", "non-arpeggiate", "accidental-mark", "other-notation", "tremolo",
    "lyric", "direction", "harmony", "figured-bass", "bookmark", "link", "listening",
    // How a notehead is drawn, not which note it is.
    "notehead",
    // Ornamental by definition, and carrying no duration of their own, so removing them leaves no gap.
    "grace", "cue",
    // Read straight through: the page shows a plain barline and the expectation matches it, so the
    // piece is simply shorter than it was written. `ending` is deliberately NOT here — playing a
    // first-time bar straight into a second-time bar is a passage that never existed.
    "repeat",
)

private const val PART_CHOSEN = "read "

public val Dropped.kind: DropKind
    get() = when {
        element == "part" && detail.startsWith(PART_CHOSEN) -> DropKind.Cosmetic
        element in cosmeticElements -> DropKind.Cosmetic
        else -> DropKind.Material
    }

/** What was lost that a reader would notice. Empty means the page is honest, whatever else it dropped. */
public val MusicXmlResult.Parsed.material: List<Dropped> get() = dropped.filter { it.kind == DropKind.Material }
