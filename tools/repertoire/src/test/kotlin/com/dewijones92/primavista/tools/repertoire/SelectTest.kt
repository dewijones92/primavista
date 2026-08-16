package com.dewijones92.primavista.tools.repertoire

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val A_STAGE = 6
private const val PER_STAGE = 8
private const val PER_COMPOSER = 2

/**
 * Which pieces actually ship.
 *
 * The tool had no tests at all, and it decides the app's repertoire. The cap it claims to enforce
 * was not enforced: three composers reached the shipped manifest with three pieces each against a
 * stated limit of two.
 */
class SelectTest {

    @Test
    fun `no composer gets more slots than the cap allows`() {
        val prolific = (1..6).map { SelectFixtures.accepted("Grandval", "g$it", stage = A_STAGE, passages = 10 - it) }

        val chosen = select(prolific, perStage = PER_STAGE, perComposer = PER_COMPOSER)

        assertEquals(PER_COMPOSER, chosen.size)
    }

    /** The cap is across the whole selection, not per rung — the shipped violation spanned rungs. */
    @Test
    fun `the cap holds across stages, not just within one`() {
        val spread = listOf(
            SelectFixtures.accepted("Schubert", "a", stage = 8, passages = 9),
            SelectFixtures.accepted("Schubert", "b", stage = 9, passages = 8),
            SelectFixtures.accepted("Schubert", "c", stage = 10, passages = 7),
        )

        val chosen = select(spread, perStage = PER_STAGE, perComposer = PER_COMPOSER)

        assertEquals(PER_COMPOSER, chosen.size)
    }

    @Test
    fun `breadth comes before depth, so a second piece by one composer waits`() {
        val mixed = listOf(
            SelectFixtures.accepted("Grandval", "g1", stage = A_STAGE, passages = 30),
            SelectFixtures.accepted("Grandval", "g2", stage = A_STAGE, passages = 29),
            SelectFixtures.accepted("Thys", "t1", stage = A_STAGE, passages = 5),
        )

        val chosen = select(mixed, perStage = PER_STAGE, perComposer = PER_COMPOSER)

        assertEquals(listOf("g1", "t1", "g2"), chosen.map { it.source.id })
    }

    @Test
    fun `a stage takes no more than its share`() {
        val many = (1..20).map { SelectFixtures.accepted("Composer $it", "p$it", stage = A_STAGE, passages = it) }

        val chosen = select(many, perStage = 3, perComposer = PER_COMPOSER)

        assertEquals(3, chosen.size)
    }

    @Test
    fun `a piece no rung can place is not offered at all`() {
        val unplaceable = listOf(SelectFixtures.accepted("Nobody", "n1", stage = null, passages = 0))

        assertTrue(select(unplaceable).isEmpty())
    }

    @Test
    fun `the more readable passages a piece offers, the sooner it is taken`() {
        val pieces = listOf(
            SelectFixtures.accepted("A", "few", stage = A_STAGE, passages = 1),
            SelectFixtures.accepted("B", "many", stage = A_STAGE, passages = 40),
        )

        val chosen = select(pieces, perStage = 1, perComposer = PER_COMPOSER)

        assertEquals(listOf("many"), chosen.map { it.source.id })
    }
}
