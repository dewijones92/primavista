package com.dewijones92.primavista.ui.repertoire

import com.dewijones92.primavista.score.MusicXmlParser
import com.dewijones92.primavista.score.MusicXmlResult
import com.dewijones92.primavista.score.PartChoice
import com.dewijones92.primavista.score.Score
import com.dewijones92.primavista.score.ScoreId
import com.dewijones92.primavista.score.ScoreOrigin
import com.dewijones92.primavista.score.material
import com.dewijones92.primavista.score.parseAny

private const val PICKED_LICENCE = "Opened from this device. Its licence is whatever Dewi's copy is."

/** What came of a file Dewi picked. Never a bare null: a file that will not read has a reason. */
internal sealed interface Picked {
    data class Readable(val score: Score, val lost: List<String>) : Picked

    data class Refused(val reason: String) : Picked
}

/**
 * Reads a MusicXML file Dewi already has, by exactly the road a shipped piece takes.
 *
 * The parser, the keyboard-part choice, the material/decoration split, the grading and the
 * windowing are all the same code — so a file he brings is not a second kind of music. What differs
 * is only what is *known* about it: nothing, which is why the licence is stated as unknown and the
 * losses are surfaced before he reads rather than after.
 *
 * A keyboard part is preferred and the first part is the fallback, because a picked file is as
 * likely to be a single line his teacher wrote out as it is to be a piano score.
 */
internal fun readPicked(bytes: ByteArray, name: String, parser: MusicXmlParser): Picked {
    if (bytes.isEmpty()) return Picked.Refused("'$name' is empty")
    val id = ScoreId("picked-$name")
    val keyboard = parser.parseAny(bytes, id.value, PICKED_LICENCE, PartChoice.Keyboard)
    val result = keyboard as? MusicXmlResult.Parsed
        ?: parser.parseAny(bytes, id.value, PICKED_LICENCE, PartChoice.First)
    return when (result) {
        is MusicXmlResult.Parsed -> Picked.Readable(
            score = result.score.copy(
                title = titleOf(result.score, name),
                origin = ScoreOrigin.Parsed(id.value, PICKED_LICENCE)
            ),
            lost = result.material.map { it.toString() },
        )
        is MusicXmlResult.Failed -> Picked.Refused("'$name' is not MusicXML this app can read: ${result.reason}")
    }
}

/** The file's own name wins only when the engraving has nothing better; many have neither. */
private fun titleOf(score: Score, name: String): String =
    score.title.takeIf { it.isNotBlank() && it != score.id.value } ?: name.substringBeforeLast('.')
