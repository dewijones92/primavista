package com.dewijones92.primavista.notation

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

private const val CODEPOINT_PREFIX = "U+"
private const val HEX_RADIX = 16
private const val AXIS_X = 0
private const val AXIS_Y = 1
private const val COORDINATE_COUNT = 2

@Serializable
private data class BravuraDocument(
    val engravingDefaults: Map<String, JsonElement> = emptyMap(),
    val glyphAdvanceWidths: Map<String, Double> = emptyMap(),
    val glyphBBoxes: Map<String, BravuraBox> = emptyMap(),
    val glyphsWithAnchors: Map<String, Map<String, List<Double>>> = emptyMap(),
)

@Serializable
private data class BravuraBox(
    val bBoxNE: List<Double> = emptyList(),
    val bBoxSW: List<Double> = emptyList(),
)

@Serializable
private data class GlyphNameEntry(val codepoint: String = "")

/** [GlyphMetrics] from Bravura's own metadata, in staff spaces with SMuFL's upward y. */
public class BravuraGlyphMetrics private constructor(
    override val engraving: EngravingDefaults,
    private val codepoints: Map<SmuflGlyph, Int>,
    private val advances: Map<SmuflGlyph, StaffSpaces>,
    private val boxes: Map<SmuflGlyph, GlyphBox>,
    private val anchors: Map<SmuflGlyph, Map<String, Pair<StaffSpaces, StaffSpaces>>>,
) : GlyphMetrics {
    override fun codepoint(glyph: SmuflGlyph): Int = codepoints.getValue(glyph)

    override fun advanceWidth(glyph: SmuflGlyph): StaffSpaces = advances.getValue(glyph)

    override fun boundingBox(glyph: SmuflGlyph): GlyphBox = boxes.getValue(glyph)

    override fun anchor(glyph: SmuflGlyph, name: String): Pair<StaffSpaces, StaffSpaces>? =
        anchors[glyph]?.get(name)

    public companion object {
        private val json = Json { ignoreUnknownKeys = true }

        public fun from(source: GlyphMetricsSource): BravuraGlyphMetrics {
            val document = json.decodeFromString<BravuraDocument>(source.bravuraMetadataJson())
            val names = json.decodeFromString<Map<String, GlyphNameEntry>>(source.glyphNamesJson())
            val wanted = SmuflGlyph.entries.associateBy { it.glyphName }

            val codepoints = wanted.entries.mapNotNull { (name, glyph) ->
                names[name]?.let { glyph to parseCodepoint(name, it.codepoint) }
            }.toMap()
            val advances = wanted.entries.mapNotNull { (name, glyph) ->
                document.glyphAdvanceWidths[name]?.let { glyph to StaffSpaces(it) }
            }.toMap()
            val boxes = wanted.entries.mapNotNull { (name, glyph) ->
                document.glyphBBoxes[name]?.let { glyph to box(name, it) }
            }.toMap()
            val anchors = wanted.entries.mapNotNull { (name, glyph) ->
                document.glyphsWithAnchors[name]?.let { glyph to anchorsOf(name, it) }
            }.toMap()

            requireComplete("codepoint", codepoints.keys)
            requireComplete("advance width", advances.keys)
            requireComplete("bounding box", boxes.keys)
            return BravuraGlyphMetrics(
                engraving = engravingDefaults(document.engravingDefaults),
                codepoints = codepoints,
                advances = advances,
                boxes = boxes,
                anchors = anchors,
            )
        }

        private fun requireComplete(what: String, present: Set<SmuflGlyph>) {
            val missing = SmuflGlyph.entries.filterNot { it in present }
            check(missing.isEmpty()) {
                "Bravura metadata has no $what for ${missing.joinToString { it.glyphName }}"
            }
        }

        private fun parseCodepoint(name: String, raw: String): Int {
            require(raw.startsWith(CODEPOINT_PREFIX)) { "codepoint '$raw' for $name is not U+XXXX" }
            return requireNotNull(raw.removePrefix(CODEPOINT_PREFIX).toIntOrNull(HEX_RADIX)) {
                "codepoint '$raw' for $name is not hexadecimal"
            }
        }

        private fun box(name: String, raw: BravuraBox): GlyphBox {
            require(raw.bBoxNE.size == COORDINATE_COUNT && raw.bBoxSW.size == COORDINATE_COUNT) {
                "bounding box for $name is not two corners of two coordinates"
            }
            return GlyphBox(
                southWestX = StaffSpaces(raw.bBoxSW[AXIS_X]),
                southWestY = StaffSpaces(raw.bBoxSW[AXIS_Y]),
                northEastX = StaffSpaces(raw.bBoxNE[AXIS_X]),
                northEastY = StaffSpaces(raw.bBoxNE[AXIS_Y]),
            )
        }

        private fun anchorsOf(
            name: String,
            raw: Map<String, List<Double>>,
        ): Map<String, Pair<StaffSpaces, StaffSpaces>> = raw.mapValues { (anchor, coordinates) ->
            require(coordinates.size == COORDINATE_COUNT) {
                "anchor $anchor of $name is not two coordinates"
            }
            StaffSpaces(coordinates[AXIS_X]) to StaffSpaces(coordinates[AXIS_Y])
        }

        private fun engravingDefaults(values: Map<String, JsonElement>): EngravingDefaults =
            EngravingDefaults(
                staffLineThickness = values.spaces("staffLineThickness"),
                stemThickness = values.spaces("stemThickness"),
                beamThickness = values.spaces("beamThickness"),
                beamSpacing = values.spaces("beamSpacing"),
                legerLineThickness = values.spaces("legerLineThickness"),
                legerLineExtension = values.spaces("legerLineExtension"),
                thinBarlineThickness = values.spaces("thinBarlineThickness"),
                thickBarlineThickness = values.spaces("thickBarlineThickness"),
                barlineSeparation = values.spaces("barlineSeparation"),
                tieMidpointThickness = values.spaces("tieMidpointThickness"),
                slurMidpointThickness = values.spaces("slurMidpointThickness"),
            )

        private fun Map<String, JsonElement>.spaces(key: String): StaffSpaces {
            val number = (this[key] as? JsonPrimitive)?.doubleOrNull
            checkNotNull(number) { "Bravura engravingDefaults.$key is missing or not a number" }
            return StaffSpaces(number)
        }
    }
}
