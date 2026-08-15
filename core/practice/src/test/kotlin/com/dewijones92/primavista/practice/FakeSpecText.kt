package com.dewijones92.primavista.practice

import com.dewijones92.primavista.score.DifficultySpec

/**
 * Stands in for `:core:database`'s `DifficultyCodec`, which `:core:practice` deliberately cannot
 * see. It keeps the specs it was handed and returns them by key — enough to prove the replay
 * carries the spec through, without this module growing a second encoding of one (see [SpecText]).
 */
internal class FakeSpecText : SpecText {
    private val kept = mutableMapOf<String, DifficultySpec>()

    override fun encode(spec: DifficultySpec): String {
        val key = "spec${kept.size}"
        kept[key] = spec
        return key
    }

    override fun decode(encoded: String): DifficultySpec? = kept[encoded]
}
