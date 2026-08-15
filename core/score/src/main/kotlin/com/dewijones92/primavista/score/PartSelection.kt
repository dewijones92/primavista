package com.dewijones92.primavista.score

import org.w3c.dom.Element

private const val KEYBOARD_STAVES = 2

/**
 * Choosing which `<part>` to read, kept apart from reading it: one type answers *which performer*
 * and the other answers *what did they play*, and the reader had grown a second job.
 */
internal object PartSelection {

    fun choose(parts: List<Element>, choice: PartChoice): Element? = when (choice) {
        PartChoice.First -> parts.first()
        is PartChoice.ById -> parts.firstOrNull { it.attr("id") == choice.id }
        PartChoice.Keyboard -> parts.firstOrNull { staffCountOf(it) >= KEYBOARD_STAVES }
    }

    fun describeMissing(parts: List<Element>, choice: PartChoice): String = when (choice) {
        PartChoice.First -> "no <part> in <score-partwise>"
        is PartChoice.ById -> "no part with id '${choice.id}'; found ${parts.joinToString(transform = ::nameOf)}"
        PartChoice.Keyboard ->
            "no part is written on $KEYBOARD_STAVES or more staves, so none of it is keyboard " +
                "writing; found ${parts.joinToString { "${nameOf(it)} on ${staffCountOf(it)}" }}"
    }

    fun nameOf(part: Element): String = part.attr("id") ?: "(unnamed part)"

    /**
     * `<staves>` lives in the part's own attributes rather than in `<part-list>`, so the staff count
     * is a fact only the part itself carries. Absent means one, which is what the spec says.
     */
    private fun staffCountOf(part: Element): Int =
        part.elements("measure")
            .firstNotNullOfOrNull { measure ->
                measure.elements("attributes").firstNotNullOfOrNull { it.textOf("staves") }
            }
            ?.trim()
            ?.toIntOrNull()
            ?: 1
}
