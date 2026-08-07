package com.dewijones92.primavista.score

import com.dewijones92.primavista.common.Diag
import com.dewijones92.primavista.common.NoOpDiag
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

private const val ROOT_ELEMENT = "score-partwise"
private const val CONTAINER_PATH = "META-INF/container.xml"
private const val METADATA_PREFIX = "META-INF"
private const val DIAG_TAG = "musicxml"
private const val DROPPED_IN_SUMMARY = 5

private val musicXmlSuffixes = listOf(".musicxml", ".xml")

/**
 * The hardened DOM reader for this app's MusicXML subset.
 */
public class DomMusicXmlParser(private val diag: Diag = NoOpDiag) : MusicXmlParser {

    override fun parse(xml: String, sourceName: String, licence: String): MusicXmlResult {
        val document = runCatching { parseXmlDocument(xml) }.getOrElse { failure ->
            return failed(sourceName, "malformed XML: ${failure.message ?: failure::class.simpleName}")
        }
        val root = document.documentElement
            ?: return failed(sourceName, "the document has no root element")
        if (root.tagName != ROOT_ELEMENT) {
            return failed(sourceName, "expected <$ROOT_ELEMENT>, found <${root.tagName}>")
        }
        return reported(sourceName, PartwiseReader(sourceName, licence).read(root))
    }

    override fun parseCompressed(bytes: ByteArray, sourceName: String, licence: String): MusicXmlResult {
        val entries = runCatching { zipEntriesOf(bytes) }.getOrElse { failure ->
            return failed(sourceName, "unreadable .mxl container: ${failure.message ?: failure::class.simpleName}")
        }
        val root = rootFileOf(entries)
            ?: return failed(sourceName, "no MusicXML root file inside the .mxl container")
        return parse(root.toString(Charsets.UTF_8), sourceName, licence)
    }

    private fun failed(sourceName: String, reason: String): MusicXmlResult.Failed {
        diag.event(DIAG_TAG, "parse failed source=$sourceName reason=$reason")
        return MusicXmlResult.Failed(reason)
    }

    private fun reported(sourceName: String, result: MusicXmlResult): MusicXmlResult {
        val parsed = result as? MusicXmlResult.Parsed ?: return result
        val score = parsed.score
        parsed.dropped.forEach { diag.counted(DIAG_TAG, "dropped:${it.element}") }
        diag.event(
            DIAG_TAG,
            "parsed source=$sourceName bars=${score.measures.size} notes=${score.notes.size} " +
                "staves=${score.staves.size} tempo=${score.defaultTempoBpm}bpm endsAt=${score.endsAt.value}ticks " +
                "poly=${score.polyphony} polyFromBar=${score.firstPolyphonicMeasure() ?: "none"} " +
                "dropped=${parsed.dropped.size} " +
                parsed.dropped.take(DROPPED_IN_SUMMARY).joinToString("; ", prefix = "[", postfix = "]"),
        )
        return parsed
    }

    private fun zipEntriesOf(bytes: ByteArray): Map<String, ByteArray> {
        val entries = LinkedHashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory) entries[entry.name] = zip.readBytes()
            }
        }
        return entries
    }

    private fun rootFileOf(entries: Map<String, ByteArray>): ByteArray? {
        val declared = entries[CONTAINER_PATH]?.let { declaredRootPath(it) }
        return declared?.let { entries[it] } ?: entries.entries
            .firstOrNull { candidate ->
                !candidate.key.startsWith(METADATA_PREFIX) && musicXmlSuffixes.any { candidate.key.endsWith(it) }
            }
            ?.value
    }

    private fun declaredRootPath(containerXml: ByteArray): String? =
        runCatching {
            parseXmlDocument(containerXml.toString(Charsets.UTF_8))
                .getElementsByTagName("rootfile")
                .let { nodes -> (0 until nodes.length).map { nodes.item(it) } }
                .filterIsInstance<Element>()
                .firstNotNullOfOrNull { it.attr("full-path") }
        }.getOrNull()
}
