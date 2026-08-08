package com.dewijones92.primavista.database

import com.dewijones92.primavista.common.Diag
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Keeps every line so a test can assert what was said, not only what happened. */
private class Lines : Diag {
    val said: MutableList<String> = mutableListOf()

    override fun event(tag: String, message: String) {
        said += message
    }

    override fun counted(tag: String, key: String, increment: Int): Unit = Unit

    override fun state(tag: String, snapshot: () -> String): Unit = Unit

    override fun report(header: Map<String, String>): String = said.joinToString("\n")
}

class ReadOrRefuseTest {
    private val diag = Lines()

    @Test
    fun aFailedReadRefusesWithTheReason() = runBlocking {
        val read = diag.readOrRefuse(TAG, "the sessions") {
            error("No enum constant Provenance.Unmeasured")
        }

        assertEquals(
            StoredReading.Unreadable("the sessions", "No enum constant Provenance.Unmeasured"),
            read,
        )
        assertTrue(diag.said.single(), diag.said.single().contains("Unmeasured"))
    }

    /**
     * The whole of fix 2: an empty history and a refused read are opposite statements about
     * Dewi's practice, so they must not be the same value.
     */
    @Test
    fun aRefusalIsATellableThingRatherThanAnEmptyList() = runBlocking {
        val nothingPractised = diag.readOrRefuse(TAG, "the sessions") { emptyList<String>() }
        val refused = diag.readOrRefuse(TAG, "the sessions") { error("corrupt row") }

        assertEquals(StoredReading.Readable(emptyList<String>()), nothingPractised)
        assertTrue("a refusal read back as an empty list", refused is StoredReading.Unreadable)
        assertNull(refused.valueOrNull())
    }

    @Test
    fun mapCarriesARefusalThroughWithItsReasonIntact() = runBlocking {
        val refused: StoredReading<List<String>> = diag.readOrRefuse(TAG, "the repertoire") {
            error("unrecognised polyphony 'Duet'")
        }

        val mapped = refused.map { titles -> titles.map(String::uppercase) }

        assertEquals(refused, mapped)
        assertEquals("the repertoire", (mapped as StoredReading.Unreadable).what)
    }

    @Test
    fun aReadableValueSurvivesMapping() = runBlocking {
        val read = diag.readOrRefuse(TAG, "the repertoire") { listOf("minuet") }

        assertEquals(StoredReading.Readable(listOf("MINUET")), read.map { it.map(String::uppercase) })
    }

    /**
     * `RoomDatabase.close()` cancels Room's own scope, so a read on a closed database fails with
     * a `CancellationException` while the caller is still very much alive. Rethrowing it there
     * cancels the caller's coroutine and the screen sits on "Reading…" for ever, saying nothing.
     */
    @Test
    fun aCancellationThatIsNotTheCallersIsReportedRatherThanRethrown() = runBlocking {
        val read = diag.readOrRefuse(TAG, "the skill states") {
            throw CancellationException("Job was cancelled; job=SupervisorJobImpl{Cancelled}")
        }

        assertTrue("expected a refusal, got $read", read is StoredReading.Unreadable)
        assertTrue(diag.said.single(), diag.said.single().contains("closed underneath the read"))
    }

    /**
     * Leaving the Progress tab cancels the read. Reporting that as an unreadable database is a
     * false line in the one evidence a report will ever have — see `.claude/CODE-NOTES.md`.
     */
    @Test
    fun aCancelledReadIsNotReportedAsAnUnreadableDatabase() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val reading = launch {
            diag.readOrRefuse(TAG, "the sessions") {
                started.complete(Unit)
                CompletableDeferred<List<String>>().await()
            }
        }
        started.await()

        reading.cancel()
        yield()

        assertTrue(reading.isCancelled)
        assertEquals(emptyList<String>(), diag.said)
    }

    private companion object {
        const val TAG = "db.test"
    }
}
