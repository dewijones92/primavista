package com.dewijones92.primavista.ui.repertoire

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.dewijones92.primavista.di.AppContainer
import com.dewijones92.primavista.di.PieceParse
import com.dewijones92.primavista.di.ShippedRepertoire
import com.dewijones92.primavista.score.Polyphony

private const val UNREACHABLE = Int.MAX_VALUE

/**
 * What there is to read, with the facts that decide whether you can read it here: which rung it
 * becomes readable at, how much of it opens, how polyphonic it is, and whether anything was lost.
 *
 * Rows appear **as each piece finishes reading** rather than when the whole corpus has. Parsing a
 * real song costs real time (see [ShippedRepertoire]), and a screen that shows twenty-eight bones
 * for ten seconds and then everything at once reads as broken, where one that fills reads as busy.
 *
 * Ordered easiest first, so what Dewi can read today is at the top rather than buried under a
 * Schubert song he cannot.
 *
 * Tapping a piece opens it; "Practise this" hands it to the Practise tab through [PracticeRequest].
 */
@Composable
public fun RepertoireRoute(container: AppContainer, modifier: Modifier = Modifier) {
    val shipped = container.shippedRepertoire
    val arrived by shipped.parsed.collectAsState()
    val context = LocalContext.current
    var picked by remember { mutableStateOf<Picked?>(null) }
    LaunchedEffect(shipped) { shipped.load() }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        picked = uri?.let { readPickedFile(context, it, container) }
    }
    RepertoireScreen(
        rows = arrived.map { rowFor(container, shipped, it) }
            .sortedWith(compareBy({ it.rung?.number ?: UNREACHABLE }, { it.piece.title })),
        stillReading = shipped.expected - arrived.size,
        onPractise = { score ->
            container.diag.event(
                "repertoire",
                "practise requested id=${score.id.value} title='${score.title}'",
            )
            PracticeRequest.request(score)
        },
        picked = picked,
        onOpenFile = { open.launch(MUSICXML_MIME_TYPES) },
        onDismissPicked = { picked = null },
        modifier = modifier,
    )
}

/**
 * MIME types a MusicXML file arrives under, with the wildcard last and deliberately: `.mxl` is
 * commonly typed `application/octet-stream` or nothing at all, and a picker that refuses to show
 * the file Dewi is looking at is worse than one that lets him choose wrongly and hear why.
 */
private val MUSICXML_MIME_TYPES = arrayOf(
    "application/vnd.recordare.musicxml",
    "application/vnd.recordare.musicxml+xml",
    "application/xml",
    "text/xml",
    "application/zip",
    "*/*",
)

private fun readPickedFile(context: Context, uri: Uri, container: AppContainer): Picked {
    val name = displayNameOf(context, uri)
    val bytes = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }
        .getOrElse { return Picked.Refused("'$name' could not be opened: ${it.message ?: it::class.simpleName}") }
        ?: return Picked.Refused("'$name' could not be opened")
    val read = readPicked(bytes, name, container.musicXmlParser)
    container.diag.event(
        "repertoire",
        when (read) {
            is Picked.Readable ->
                "opened '$name' [bytes=${bytes.size} bars=${read.score.measures.size} " +
                    "notes=${read.score.attackedNotes.size} lost=${read.lost.size}]"
            is Picked.Refused -> "refused a picked file: ${read.reason}"
        },
    )
    return read
}

/** The picker's own display name, falling back to the last path segment when it will not say. */
private fun displayNameOf(context: Context, uri: Uri): String =
    runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }
    }.getOrNull() ?: uri.lastPathSegment ?: "the file you chose"

private fun rowFor(container: AppContainer, shipped: ShippedRepertoire, parse: PieceParse): RepertoireRow {
    val score = parse.score ?: return unreadable(parse)
    val easiest = parse.passages.firstOrNull()
    return RepertoireRow(
        piece = parse.piece,
        score = score,
        bars = score.measures.size,
        notes = score.attackedNotes.size,
        tempoBpm = score.defaultTempoBpm,
        polyphony = score.polyphony,
        firstPolyphonicBar = score.firstPolyphonicMeasure(),
        skills = container.scoreSkills.skillsOf(score).toList(),
        dropped = parse.dropped,
        material = parse.material,
        rung = easiest?.let { shipped.rungFor(it) },
        opensAs = easiest?.measures?.size,
        failure = null,
    )
}

private fun unreadable(parse: PieceParse) = RepertoireRow(
    piece = parse.piece,
    score = null,
    bars = 0,
    notes = 0,
    tempoBpm = 0,
    polyphony = Polyphony.Mono,
    firstPolyphonicBar = null,
    skills = emptyList(),
    dropped = emptyList(),
    material = emptyList(),
    rung = null,
    opensAs = null,
    failure = parse.failure ?: "the piece could not be read",
)
