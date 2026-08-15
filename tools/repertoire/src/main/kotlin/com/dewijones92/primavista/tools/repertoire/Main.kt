package com.dewijones92.primavista.tools.repertoire

import com.dewijones92.primavista.practice.Curriculum
import com.dewijones92.primavista.score.DomMusicXmlParser
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.walk

private const val USAGE = """
usage: repertoire <report|import> --corpus <dir> --commit <sha> [--out <dir>] [--limit <n>]

  report   read every .mxl under <dir>, say what would be imported and why the rest would not
  import   the same screening, then write the chosen pieces and a manifest under <out>
"""

public fun main(args: Array<String>) {
    val options = Options.parse(args) ?: run {
        println(USAGE.trim())
        return
    }
    val files = filesUnder(options.corpus, options.limit)
    println("read ${files.size} .mxl files under ${options.corpus}")
    val corpus = LiederCorpus(options.corpus, options.commit)
    val screener = Screener(DomMusicXmlParser(), passageBars = options.bars)
    val screened = files.map { screener.screen(corpus.read(it)) }
    val curriculum = Curriculum.Standard
    printReport(screened, curriculum.skillsThrough(curriculum.stages.last().id))
    if (options.command == Command.Import) {
        val out = requireNotNull(options.out) { "import needs --out" }
        val written = ImportWriter(out).write(select(screened.filterIsInstance<Screening.Accepted>()))
        println("\nwrote ${written.size} pieces to $out")
        written.forEach {
            println(
                "  stage %-3s %3d readable passages  %s — %s".format(
                    it.easiestPassage?.stageNumber?.toString() ?: "?",
                    it.passages.count { passage -> passage.stage != null },
                    it.source.composer,
                    it.source.title,
                ),
            )
        }
    }
}

private fun filesUnder(root: Path, limit: Int?): List<Path> {
    require(root.isDirectory()) { "$root is not a directory" }
    @OptIn(kotlin.io.path.ExperimentalPathApi::class)
    val all = root.walk().filter { it.toString().endsWith(LiederCorpus.SUFFIX) }.sorted().toList()
    return if (limit == null) all else all.take(limit)
}

private enum class Command { Report, Import }

private data class Options(
    val command: Command,
    val corpus: Path,
    val commit: String,
    val out: Path?,
    val limit: Int?,
    val bars: List<Int>,
) {
    companion object {
        fun parse(args: Array<String>): Options? {
            val command = when (args.firstOrNull()) {
                "report" -> Command.Report
                "import" -> Command.Import
                else -> return null
            }
            val flags = args.drop(1).chunked(2).filter { it.size == 2 }.associate { it[0] to it[1] }
            val corpus = flags["--corpus"] ?: return null
            return Options(
                command = command,
                corpus = Path(corpus),
                commit = flags["--commit"] ?: "unknown",
                out = flags["--out"]?.let { Path(it) },
                limit = flags["--limit"]?.toIntOrNull(),
                bars = flags["--bars"]?.split(",")?.mapNotNull { it.trim().toIntOrNull() }?.ifEmpty { null }
                    ?: Screener.DEFAULT_PASSAGE_BARS,
            )
        }
    }
}
