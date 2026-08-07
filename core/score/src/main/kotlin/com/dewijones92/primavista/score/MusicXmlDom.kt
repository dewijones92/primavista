package com.dewijones92.primavista.score

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory

private const val LOAD_EXTERNAL_DTD = "http://apache.org/xml/features/nonvalidating/load-external-dtd"
private const val BYTE_ORDER_MARK = "\uFEFF"
private const val DETAIL_LENGTH = 40

internal fun parseXmlDocument(xml: String): Document {
    val factory = DocumentBuilderFactory.newInstance()
    runCatching { factory.isValidating = false }
    runCatching { factory.setFeature(LOAD_EXTERNAL_DTD, false) }
    val builder = factory.newDocumentBuilder()
    builder.setEntityResolver { _, _ -> InputSource(StringReader("")) }
    return builder.parse(InputSource(StringReader(xml.removePrefix(BYTE_ORDER_MARK))))
}

internal fun Element.elements(): List<Element> {
    val out = ArrayList<Element>(childNodes.length)
    for (index in 0 until childNodes.length) {
        val node = childNodes.item(index)
        if (node.nodeType == Node.ELEMENT_NODE) out += node as Element
    }
    return out
}

internal fun Element.elements(name: String): List<Element> = elements().filter { it.tagName == name }

internal fun Element.first(name: String): Element? = elements().firstOrNull { it.tagName == name }

internal fun Element.textOf(name: String): String? = first(name)?.textContent?.trim()?.ifEmpty { null }

internal fun Element.intOf(name: String): Int? = textOf(name)?.toIntOrNull()

internal fun Element.attr(name: String): String? = getAttribute(name).trim().ifEmpty { null }

/** A short human-readable "what was this" for a [Dropped] entry, without dumping the subtree. */
internal fun Element.summary(): String {
    val children = elements().joinToString(",") { it.tagName }
    if (children.isNotEmpty()) return children
    val text = textContent.trim().replace(Regex("\\s+"), " ")
    return text.take(DETAIL_LENGTH).ifEmpty { "(no content)" }
}
