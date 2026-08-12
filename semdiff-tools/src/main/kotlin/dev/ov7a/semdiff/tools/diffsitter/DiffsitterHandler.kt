package dev.ov7a.semdiff.tools.diffsitter

import dev.ov7a.semdiff.model.AlignmentBuilder
import dev.ov7a.semdiff.model.ChangedSpan
import dev.ov7a.semdiff.model.DiffInputs
import dev.ov7a.semdiff.model.Granularity
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

/** [diffsitter](https://github.com/afnanenayet/diffsitter). */
class DiffsitterHandler : SemanticDiffToolHandler {

    override val id: String = "diffsitter"
    override val displayName: String = "Diffsitter"
    override val executableNames: List<String> = listOf("diffsitter")
    override val supportedVersions: VersionRange = VersionRange.of("0.8.0", "1.0.0")
    override val granularity: Granularity = Granularity.INTRA_LINE
    override val supportsThreeWay: Boolean = false
    override val inputMode: InputMode = InputMode.FILE_PAIR

    override val defaultArgumentPattern: String = "-r json --color off %1 %2"
    override val defaultEnvironment: Map<String, String> = emptyMap()
    override val versionArguments: List<String> = listOf("--version")

    override fun parseVersion(result: ProcessResult): ToolVersion? {
        val firstLine = (result.stdout.ifBlank { result.stderr }).lineSequence().firstOrNull().orEmpty()
        if (!firstLine.startsWith("diffsitter", ignoreCase = true)) return null
        return ToolVersion.parseFirst(firstLine)
    }

    override fun parseOutput(result: ProcessResult, inputs: DiffInputs): SemanticDiffResult {
        if (result.stdout.isBlank()) {
            // Exit 1 with an empty stdout is how diffsitter reports a file type it has no grammar for.
            return SemanticDiffResult.Unsupported(
                "diffsitter produced no output (exit ${result.exitCode})" +
                    result.stderr.trim().let { if (it.isEmpty()) "" else ": $it" },
            )
        }

        val output = try {
            JSON.decodeFromString(DiffsitterOutput.serializer(), result.stdout)
        } catch (e: Exception) {
            return SemanticDiffResult.Unsupported("diffsitter output is not the expected JSON shape: ${e.message}")
        }

        if (output.hunks.isEmpty()) return SemanticDiffResult.Unchanged

        val leftLines = LineIndex.of(inputs.left.text)
        val rightLines = LineIndex.of(inputs.right.text)
        val spans = mutableListOf<ChangedSpan>()

        output.hunks.forEach { hunk ->
            hunk.old?.let { lines ->
                collect(lines, Side.LEFT, leftLines, spans)?.let { return SemanticDiffResult.Unsupported(it) }
            }
            hunk.new?.let { lines ->
                collect(lines, Side.RIGHT, rightLines, spans)?.let { return SemanticDiffResult.Unsupported(it) }
            }
        }

        // diffsitter reports the two sides independently, with no correspondence between them, so
        // the line correspondence is worked out from the lines it did not mark.
        val alignment = AlignmentBuilder.fromChangedLinesUsingContent(
            leftChanged = spans.filter { it.side == Side.LEFT }.mapTo(mutableSetOf()) { it.line },
            rightChanged = spans.filter { it.side == Side.RIGHT }.mapTo(mutableSetOf()) { it.line },
            leftLines = leftLines.allLines(),
            rightLines = rightLines.allLines(),
        )

        return when (alignment) {
            is AlignmentBuilder.Result.Inconsistent ->
                SemanticDiffResult.Unsupported("diffsitter output cannot be aligned: ${alignment.reason}")

            is AlignmentBuilder.Result.Alignment -> SemanticDiffResult.Changed(
                granularity = granularity,
                alignment = alignment.pairs,
                spans = spans.sortedWith(compareBy({ it.side }, { it.line }, { it.startChar })),
            )
        }
    }

    private fun collect(
        lines: List<DiffsitterLine>,
        side: Side,
        index: LineIndex,
        into: MutableList<ChangedSpan>,
    ): String? {
        lines.forEach { line ->
            if (line.lineIndex !in 0 until index.lineCount) {
                return "diffsitter reported ${side.name.lowercase()} line ${line.lineIndex}, " +
                    "document has ${index.lineCount} lines"
            }
            if (line.entries.isEmpty()) return@forEach

            val offsets = Utf8Offsets.forLine(index.lineText(line.lineIndex))
            val converted = mutableListOf<IntRange>()
            line.entries.forEach { entry ->
                if (entry.startPosition.row != line.lineIndex) {
                    return "diffsitter entry row ${entry.startPosition.row} does not match line ${line.lineIndex}"
                }
                val start = offsets.charOffsetOrNull(entry.startPosition.column)
                val end = offsets.charOffsetOrNull(entry.endPosition.column)
                if (start == null || end == null || start > end) {
                    return "diffsitter reported byte range ${entry.startPosition.column}..${entry.endPosition.column} " +
                        "on ${side.name.lowercase()} line ${line.lineIndex}, which does not map to characters"
                }
                converted += start..end
            }

            // Entries are one per code point; merging keeps the model (and the goldens) readable.
            merge(converted).forEach { range ->
                into += ChangedSpan(side, line.lineIndex, range.first, range.last, SpanKind.PLAIN)
            }
        }
        return null
    }

    private fun merge(ranges: List<IntRange>): List<IntRange> {
        val sorted = ranges.sortedBy { it.first }
        val merged = mutableListOf<IntRange>()
        sorted.forEach { range ->
            val last = merged.lastOrNull()
            if (last != null && range.first <= last.last) {
                merged[merged.lastIndex] = last.first..maxOf(last.last, range.last)
            } else {
                merged += range
            }
        }
        return merged
    }

    private companion object {
        val JSON = Json { ignoreUnknownKeys = true }
    }
}
