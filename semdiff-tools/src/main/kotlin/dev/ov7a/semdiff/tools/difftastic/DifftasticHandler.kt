package dev.ov7a.semdiff.tools.difftastic

import dev.ov7a.semdiff.model.AlignmentBuilder
import dev.ov7a.semdiff.model.ChangedSpan
import dev.ov7a.semdiff.model.DiffInputs
import dev.ov7a.semdiff.model.Granularity
import dev.ov7a.semdiff.model.LinePair
import dev.ov7a.semdiff.model.ProcessResult
import dev.ov7a.semdiff.model.SemanticDiffResult
import dev.ov7a.semdiff.model.Side
import dev.ov7a.semdiff.model.SpanKind
import dev.ov7a.semdiff.model.ToolVersion
import dev.ov7a.semdiff.model.VersionRange
import dev.ov7a.semdiff.model.text.LineIndex
import dev.ov7a.semdiff.model.text.Utf8Offsets
import dev.ov7a.semdiff.tools.InputMode
import dev.ov7a.semdiff.tools.SemanticDiffToolHandler
import kotlinx.serialization.json.Json

/** [difftastic](https://github.com/Wilfred/difftastic), `difft`. */
class DifftasticHandler : SemanticDiffToolHandler {

    override val id: String = "difftastic"
    override val displayName: String = "Difftastic"
    override val executableNames: List<String> = listOf("difft", "difftastic")
    override val supportedVersions: VersionRange = VersionRange.of("0.60.0", "1.0.0")
    override val granularity: Granularity = Granularity.INTRA_LINE
    override val supportsThreeWay: Boolean = false
    override val inputMode: InputMode = InputMode.FILE_PAIR

    override val defaultArgumentPattern: String = "--display json --color never %1 %2"

    /** JSON output is gated behind this; without it difft prints its usual side-by-side view. */
    override val defaultEnvironment: Map<String, String> = mapOf("DFT_UNSTABLE" to "yes")

    override val versionArguments: List<String> = listOf("--version")

    override fun parseVersion(result: ProcessResult): ToolVersion? {
        val firstLine = (result.stdout.ifBlank { result.stderr }).lineSequence().firstOrNull().orEmpty()
        if (!firstLine.startsWith("Difftastic", ignoreCase = true)) return null
        return ToolVersion.parseFirst(firstLine)
    }

    override fun parseOutput(result: ProcessResult, inputs: DiffInputs): SemanticDiffResult {
        if (result.stdout.isBlank()) {
            return SemanticDiffResult.Unsupported(
                "difft produced no output (exit ${result.exitCode})" + result.stderr.trim().prefixedOrEmpty(": "),
            )
        }

        val file = try {
            JSON.decodeFromString(DifftasticFile.serializer(), result.stdout)
        } catch (e: Exception) {
            return SemanticDiffResult.Unsupported("difft output is not the expected JSON shape: ${e.message}")
        }

        return when (file.status) {
            "unchanged" -> SemanticDiffResult.Unchanged
            "changed", "created", "deleted" -> toChanged(file, inputs)
            else -> SemanticDiffResult.Unsupported("unknown difft status '${file.status}'")
        }
    }

    private fun toChanged(file: DifftasticFile, inputs: DiffInputs): SemanticDiffResult {
        val leftLines = LineIndex.of(inputs.left.text)
        val rightLines = LineIndex.of(inputs.right.text)

        // 'created' and 'deleted' come with no alignment at all — one side is simply absent.
        val alignment = when {
            file.alignedLines.isNotEmpty() -> file.alignedLines
                .map { pair ->
                    // difft counts lines as if every file ended with a newline, so a file that does
                    // not gets one phantom line past the end. Dropping the out-of-range component
                    // is safe: only the final entry can be affected.
                    LinePair(
                        left = pair.getOrNull(0)?.takeIf { it < leftLines.lineCount },
                        right = pair.getOrNull(1)?.takeIf { it < rightLines.lineCount },
                    )
                }
                .filterNot { it.left == null && it.right == null }

            // One whole-document block, so the trailing empty line an empty file still has on our
            // side gets a counterpart and the alignment covers both documents.
            file.status == "created" || file.status == "deleted" -> {
                val whole = AlignmentBuilder.ChangedBlock(0, leftLines.lineCount, 0, rightLines.lineCount)
                when (
                    val built = AlignmentBuilder.fromChangedBlocks(
                        listOf(whole),
                        leftLines.lineCount,
                        rightLines.lineCount,
                    )
                ) {
                    is AlignmentBuilder.Result.Alignment -> built.pairs
                    is AlignmentBuilder.Result.Inconsistent ->
                        return SemanticDiffResult.Unsupported(built.reason)
                }
            }

            else -> return SemanticDiffResult.Unsupported("difft reported '${file.status}' without an alignment")
        }
        val spans = mutableListOf<ChangedSpan>()

        file.chunks.asSequence().flatten().forEach { line ->
            line.lhs?.let { side ->
                collectSpans(side, Side.LEFT, leftLines, spans)?.let { return SemanticDiffResult.Unsupported(it) }
            }
            line.rhs?.let { side ->
                collectSpans(side, Side.RIGHT, rightLines, spans)?.let { return SemanticDiffResult.Unsupported(it) }
            }
        }

        return SemanticDiffResult.Changed(
            granularity = granularity,
            alignment = alignment,
            // Upstream states that changes within block-scoped chunks come out unordered, so the
            // order is normalized here; without this the golden tests would flake.
            spans = spans.sortedWith(compareBy({ it.side }, { it.line }, { it.startChar }, { it.endChar })),
        )
    }

    /** Appends [side]'s spans to [into], or returns a rejection reason. */
    private fun collectSpans(
        side: DifftasticSide,
        which: Side,
        lines: LineIndex,
        into: MutableList<ChangedSpan>,
    ): String? {
        if (side.lineNumber !in 0 until lines.lineCount) {
            return "difft reported ${which.name.lowercase()} line ${side.lineNumber}, " +
                "document has ${lines.lineCount} lines"
        }
        if (side.changes.isEmpty()) return null

        val offsets = Utf8Offsets.forLine(lines.lineText(side.lineNumber))
        side.changes.forEach { change ->
            val start = offsets.charOffsetOrNull(change.start)
            val end = offsets.charOffsetOrNull(change.end)
            if (start == null || end == null || start > end) {
                return "difft reported byte range ${change.start}..${change.end} on " +
                    "${which.name.lowercase()} line ${side.lineNumber}, which does not map to characters"
            }
            into += ChangedSpan(which, side.lineNumber, start, end, change.highlight.toSpanKind())
        }
        return null
    }

    private companion object {
        val JSON = Json { ignoreUnknownKeys = true }

        fun String.toSpanKind(): SpanKind = when (this) {
            "delimiter" -> SpanKind.DELIMITER
            "string" -> SpanKind.STRING
            "type" -> SpanKind.TYPE
            "comment" -> SpanKind.COMMENT
            "keyword" -> SpanKind.KEYWORD
            "tree_sitter_error" -> SpanKind.PARSE_ERROR
            else -> SpanKind.PLAIN
        }

        fun String.prefixedOrEmpty(prefix: String): String = if (isEmpty()) "" else prefix + this
    }
}
