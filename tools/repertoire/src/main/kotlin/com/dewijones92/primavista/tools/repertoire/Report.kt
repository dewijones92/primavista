package com.dewijones92.primavista.tools.repertoire

import com.dewijones92.primavista.score.SkillTag

private const val TOP_REASONS = 14
private const val TOP_ROWS = 25
private const val EXAMPLES_PER_REASON = 2
private const val EXAMPLES_PER_STAGE = 4
private const val TRUNCATE = 150
private const val PERCENT = 100.0
private const val MEDIAN = 2

/**
 * What the screening found, in the shape that decides policy: not "how many passed" but *which
 * losses* did the rejecting, because a reason appearing 900 times is either a real limit of the
 * parser or a rule that is too strict, and only the histogram tells you which.
 */
public fun printReport(screened: List<Screening>, taught: Set<SkillTag>? = null) {
    val accepted = screened.filterIsInstance<Screening.Accepted>()
    val rejected = screened.filterIsInstance<Screening.Rejected>()
    val share = if (screened.isEmpty()) 0.0 else accepted.size * PERCENT / screened.size
    println("accepted ${accepted.size} of ${screened.size} (%.1f%%)".format(share))

    printRejections(rejected)
    printWholePieces(accepted)
    printPassages(accepted)
    printBlockingDials(accepted)
    printUntaughtSkills(accepted, taught.orEmpty())
    printByElement("cosmetic losses on accepted pieces", accepted.flatMap { it.cosmetic }.map { it.element })
}

private fun printRejections(rejected: List<Screening.Rejected>) {
    println("\n--- why the rest were rejected ---")
    rejected.groupingBy { headline(it.reason) }.eachCount().entries
        .sortedByDescending { it.value }
        .take(TOP_REASONS)
        .forEach { (reason, count) ->
            println("%5d  %s".format(count, reason))
            rejected.filter { headline(it.reason) == reason }
                .take(EXAMPLES_PER_REASON)
                .forEach { println("         e.g. ${it.source.id}: ${it.reason.take(TRUNCATE)}") }
        }
    printByElement("material losses", rejected.flatMap { it.material }.map { it.element })
}

private fun printWholePieces(accepted: List<Screening.Accepted>) {
    println("\n--- accepted whole pieces, by stage ---")
    accepted.groupBy { it.stageNumber }.toSortedMap(nullsLast()).forEach { (stage, pieces) ->
        val bars = pieces.map { it.score.measures.size }.sorted()
        println(
            "stage %-8s %4d pieces, %3d composers, bars %d..%d (median %d)".format(
                stageName(stage),
                pieces.size,
                pieces.map { it.source.composer }.distinct().size,
                bars.first(),
                bars.last(),
                bars[bars.size / MEDIAN],
            ),
        )
    }
}

private fun printPassages(accepted: List<Screening.Accepted>) {
    println("\n--- passages, by stage (the rung a piece can actually be read at) ---")
    accepted.flatMap { piece -> piece.passages.map { piece to it } }
        .groupBy { it.second.stageNumber }
        .toSortedMap(nullsLast())
        .forEach { (stage, found) ->
            println(
                "stage %-8s %5d passages from %3d pieces, %3d composers".format(
                    stageName(stage),
                    found.size,
                    found.map { it.first.source.id }.distinct().size,
                    found.map { it.first.source.composer }.distinct().size,
                ),
            )
        }

    println("\n--- pieces by their EASIEST passage ---")
    accepted.groupBy { it.easiestPassage?.stageNumber }.toSortedMap(nullsLast()).forEach { (stage, pieces) ->
        println(
            "stage %-8s %4d pieces, %3d composers".format(
                stageName(stage),
                pieces.size,
                pieces.map { it.source.composer }.distinct().size,
            ),
        )
        pieces.take(EXAMPLES_PER_STAGE).forEach {
            val bars = it.easiestPassage?.score?.measures?.size
            println("         ${it.source.composer} — ${it.source.title} [$bars bars]")
        }
    }
}

private fun printBlockingDials(accepted: List<Screening.Accepted>) {
    println("\n--- what the LAST rung objects to, by dial ---")
    val unplaced = accepted.flatMap { it.passages }.filter { it.stage == null }
    unplaced.flatMap { it.topRungRefusals }
        .map { it.substringBefore(" at bar ").replace(Regex("\\d+"), "N") }
        .groupingBy { it }
        .eachCount()
        .entries
        .sortedByDescending { it.value }
        .take(TOP_ROWS)
        .forEach { (reason, count) -> println("%6d  %s".format(count, reason)) }
    println("passages the last rung refuses for exactly one reason: " + unplaced.count { it.topRungRefusals.size == 1 })
}

private fun printUntaughtSkills(accepted: List<Screening.Accepted>, taught: Set<SkillTag>) {
    println("\n--- skills no stage teaches ---")
    accepted.flatMap { it.passages }
        .filter { it.stage == null }
        .flatMap { it.skills }
        .filterNot { it in taught }
        .groupingBy { it.toString() }
        .eachCount()
        .entries
        .sortedByDescending { it.value }
        .take(TOP_ROWS)
        .forEach { (skill, count) -> println("%6d  %s".format(count, skill)) }
}

private fun printByElement(heading: String, elements: List<String>) {
    println("\n--- $heading, by element ---")
    elements.groupingBy { it }.eachCount().entries
        .sortedByDescending { it.value }
        .take(TOP_ROWS)
        .forEach { (element, count) -> println("%6d  <%s>".format(count, element)) }
}

private fun stageName(stage: Int?): String = stage?.toString() ?: "beyond"

/** Rejections carry bar numbers and counts; grouping needs the reason without them. */
private fun headline(reason: String): String = reason.substringBefore(" (").substringBefore("; first at bar")

private fun <T : Comparable<T>> nullsLast(): Comparator<T?> =
    Comparator { a, b ->
        when {
            a == null && b == null -> 0
            a == null -> 1
            b == null -> -1
            else -> a.compareTo(b)
        }
    }
