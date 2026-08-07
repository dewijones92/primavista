package com.dewijones92.primavista.data

import android.content.Context
import com.dewijones92.primavista.notation.GlyphMetricsSource

/**
 * Reads the two SMuFL metadata files out of the APK's assets, which is all `:core:notation` needs
 * from Android — the port exists so the layout engine can stay pure JVM and golden-testable.
 */
public class AssetGlyphMetricsSource(private val context: Context) : GlyphMetricsSource {

    override fun bravuraMetadataJson(): String = read("smufl/bravura_metadata.json")

    override fun glyphNamesJson(): String = read("smufl/glyphnames.json")

    private fun read(path: String): String =
        context.assets.open(path).bufferedReader().use { it.readText() }
}
