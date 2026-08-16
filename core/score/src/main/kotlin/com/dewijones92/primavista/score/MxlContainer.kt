package com.dewijones92.primavista.score

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

/** How a `.mxl` is laid out. One statement of it, read by the container and by the parser alike. */
internal const val CONTAINER_PATH: String = "META-INF/container.xml"
internal const val METADATA_PREFIX: String = "META-INF"
internal val musicXmlSuffixes: List<String> = listOf(".musicxml", ".xml")

/**
 * Reading a `.mxl` container without trusting it.
 *
 * The shipped corpus was written by this repo's own import tool, so the old reader called
 * `readBytes()` on every entry and that was fine. A file picked off the phone is not ours: a
 * forty-kilobyte archive can inflate to gigabytes, and on a phone that is an out-of-memory kill
 * rather than an error message. So every byte inflated here is counted against one budget and the
 * refusal, when it comes, **says what the limit was** — a crash tells Dewi nothing.
 *
 * The limits are set from what real files actually are, not from a guess. Across the 41 shipped
 * songs: largest 1.5 MB uncompressed, at most 2 entries, worst compression ratio 46:1.
 */
internal object MxlContainer {
    /** Twenty times the largest real one, so a much longer piece than anything shipped still opens. */
    const val MAX_INFLATED_BYTES: Long = 32L * 1024 * 1024

    /** The shipped files hold two; an export carrying images or fonts may hold more. */
    const val MAX_ENTRIES: Int = 64

    private const val CHUNK = 16 * 1024
    private const val BYTES_PER_MEGABYTE = 1024 * 1024

    fun read(bytes: ByteArray): MxlReading =
        runCatching { readOrThrow(bytes) }.getOrElse { failure ->
            MxlReading.Refused("unreadable .mxl container: ${failure.message ?: failure::class.simpleName}")
        }

    private fun readOrThrow(bytes: ByteArray): MxlReading {
        val entries = LinkedHashMap<String, ByteArray>()
        val budget = Budget()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            generateSequence { zip.nextEntry }
                .filterNot { it.isDirectory }
                .forEach { entry ->
                    budget.take(zip, entry.name, entries)?.let { return it }
                }
        }
        return MxlReading.Read(entries)
    }

    /**
     * One budget for the whole archive, spent as the stream inflates.
     *
     * Entries that cannot be music are inflated and thrown away rather than skipped, because a zip
     * stream cannot seek past one — so they cost the budget too, which is the point: a bomb hidden
     * in `Pictures/cover.png` must be refused just as one in `score.xml` is.
     */
    private class Budget {
        private var inflated = 0L
        private var seen = 0

        fun take(zip: ZipInputStream, name: String, into: MutableMap<String, ByteArray>): MxlReading.Refused? {
            seen++
            if (seen > MAX_ENTRIES) {
                return MxlReading.Refused("this .mxl holds more than $MAX_ENTRIES files, so it is not a score")
            }
            val sink = if (couldHoldMusic(name)) ByteArrayOutputStream() else null
            val buffer = ByteArray(CHUNK)
            var read = zip.read(buffer)
            while (read > 0) {
                inflated += read
                if (inflated > MAX_INFLATED_BYTES) return tooBig()
                sink?.write(buffer, 0, read)
                read = zip.read(buffer)
            }
            sink?.let { into[name] = it.toByteArray() }
            return null
        }

        private fun tooBig() = MxlReading.Refused(
            "this .mxl unpacks to more than ${MAX_INFLATED_BYTES / BYTES_PER_MEGABYTE}MB, " +
                "which is not a piece of music",
        )
    }

    /**
     * Whether an entry is worth keeping in memory at all: the container's own manifest, or
     * something outside `META-INF` that a root file could be. A MuseScore export routinely carries
     * images and fonts, and none of them can be a score.
     */
    private fun couldHoldMusic(name: String): Boolean =
        name == CONTAINER_PATH || (!name.startsWith(METADATA_PREFIX) && musicXmlSuffixes.any { name.endsWith(it) })
}

/** What reading a container turned out to be. A refusal carries its reason, never a bare null. */
internal sealed interface MxlReading {
    data class Read(val entries: Map<String, ByteArray>) : MxlReading

    data class Refused(val reason: String) : MxlReading
}
