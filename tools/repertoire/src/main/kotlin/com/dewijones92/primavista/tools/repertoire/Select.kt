package com.dewijones92.primavista.tools.repertoire

/**
 * Which of the accepted pieces actually ship.
 *
 * The goal is a **spread**, not the best few: the ladder needs something real to read at each rung
 * it can reach, and a bundle of forty songs all of the same difficulty would be one rung with a lot
 * of pages. A piece is placed by its **easiest passage**, because that is the rung at which it
 * first becomes readable at all, and within a rung the ones offering more readable passages come
 * first — one file that yields eight usable reads is worth more than three that yield one each.
 *
 * [perComposer] caps how deep any one name goes, so a stage is never one composer wearing a hat.
 */
public fun select(
    accepted: List<Screening.Accepted>,
    perStage: Int = DEFAULT_PER_STAGE,
    perComposer: Int = DEFAULT_PER_COMPOSER,
): List<Screening.Accepted> {
    val takenPerComposer = mutableMapOf<String, Int>()
    return accepted.filter { it.easiestPassage?.stage != null }
        .groupBy { it.easiestPassage?.stageNumber }
        .toSortedMap(compareBy(nullsLast()) { it })
        .flatMap { (_, pieces) -> fillStage(pieces, perStage, perComposer, takenPerComposer) }
}

private fun fillStage(
    pieces: List<Screening.Accepted>,
    perStage: Int,
    perComposer: Int,
    takenPerComposer: MutableMap<String, Int>,
): List<Screening.Accepted> {
    val ordered = pieces.sortedWith(
        compareByDescending<Screening.Accepted> { it.placeablePassages }.thenBy { it.source.id },
    )
    val chosen = mutableListOf<Screening.Accepted>()
    // One pass per composer slot, so breadth comes before depth.
    //
    // The sequence is lazy on purpose, and that laziness is the whole correctness argument: each
    // element is tested against `takenPerComposer` as it is pulled, AFTER the previous element
    // updated it. Materialising the candidates for a pass up front — which an earlier version did,
    // to satisfy a lint about loop jumps — tested every piece against the counts as they were
    // BEFORE the pass, so one composer with six pieces took all six against a cap of two. Three
    // composers reached the shipped manifest that way.
    for (pass in 0 until perComposer) {
        val room = perStage - chosen.size
        ordered.asSequence()
            .filter { it !in chosen && takenPerComposer.getOrDefault(it.source.composer, 0) <= pass }
            .take(room)
            .forEach { piece ->
                chosen += piece
                takenPerComposer[piece.source.composer] = pass + 1
            }
        if (chosen.size >= perStage) break
    }
    return chosen
}

private val Screening.Accepted.placeablePassages: Int get() = passages.count { it.stage != null }

private fun nullsLast(): Comparator<Int?> =
    Comparator { a, b ->
        when {
            a == null && b == null -> 0
            a == null -> 1
            b == null -> -1
            else -> a.compareTo(b)
        }
    }

public const val DEFAULT_PER_STAGE: Int = 8
public const val DEFAULT_PER_COMPOSER: Int = 2
