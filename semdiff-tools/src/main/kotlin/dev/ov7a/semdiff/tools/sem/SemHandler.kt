package dev.ov7a.semdiff.tools.sem

import dev.ov7a.semdiff.model.AlignmentBuilder
import dev.ov7a.semdiff.model.ChangedRegion
import dev.ov7a.semdiff.model.DiffInputs
import dev.ov7a.semdiff.model.Granularity
import dev.ov7a.semdiff.model.ProcessResult
import dev.ov7a.semdiff.model.RegionChange
import dev.ov7a.semdiff.model.SemanticDiffResult
import dev.ov7a.semdiff.model.Side
import dev.ov7a.semdiff.model.ToolVersion
import dev.ov7a.semdiff.model.VersionRange
import dev.ov7a.semdiff.model.text.LineIndex
import dev.ov7a.semdiff.tools.InputMode
import dev.ov7a.semdiff.tools.SemanticDiffToolHandler
import kotlinx.serialization.json.Json

/**
 * [sem](https://github.com/Ataraxy-Labs/sem).
 *
 * The coarsest handler: sem reports whole changed entities (a function, a class) with no
 * intra-line detail, so a diff shown through it marks entire declarations rather than the tokens
 * that actually differ. It also detects moves, which a monotonic line alignment cannot express —
 * those results are refused here and fall back to the built-in diff rather than being approximated.
 */
class SemHandler : SemanticDiffToolHandler {

    override val id: String = "sem"
    override val displayName: String = "sem"
    override val executableNames: List<String> = listOf("sem")
    override val supportedVersions: VersionRange = VersionRange.of("0.20.0", "1.0.0")
    override val granularity: Granularity = Granularity.LINE_RANGE
    override val supportsThreeWay: Boolean = false
    override val inputMode: InputMode = InputMode.FILE_PAIR

    override val defaultArgumentPattern: String = "diff %1 %2 --format json"
    override val defaultEnvironment: Map<String, String> = emptyMap()
    override val versionArguments: List<String> = listOf("--version")

    override fun parseVersion(result: ProcessResult): ToolVersion? {
        val firstLine = (result.stdout.ifBlank { result.stderr }).lineSequence().firstOrNull().orEmpty()
        if (!firstLine.startsWith("sem", ignoreCase = true)) return null
        return ToolVersion.parseFirst(firstLine)
    }

    override fun parseOutput(result: ProcessResult, inputs: DiffInputs): SemanticDiffResult {
        if (result.stdout.isBlank()) {
            return SemanticDiffResult.Unsupported(
                "sem produced no output (exit ${result.exitCode})" +
                    result.stderr.trim().let { if (it.isEmpty()) "" else ": $it" },
            )
        }

        val output = try {
            JSON.decodeFromString(SemOutput.serializer(), result.stdout)
        } catch (e: Exception) {
            return SemanticDiffResult.Unsupported("sem output is not the expected JSON shape: ${e.message}")
        }

        if (output.changes.isEmpty()) return SemanticDiffResult.Unchanged

        val leftLines = LineIndex.of(inputs.left.text)
        val rightLines = LineIndex.of(inputs.right.text)

        val kept = innermost(output.changes)
        val regions = kept.flatMap(::toRegions)

        // Not fromChangedBlocks: sem pairs old and new entities, and a moved method makes those
        // pairings cross, which no line alignment can express. Only *which* lines it touched is
        // used, and the correspondence is worked out from the untouched lines.
        val alignment = AlignmentBuilder.fromChangedLinesUsingContent(
            leftChanged = regions.changedLines(Side.LEFT),
            rightChanged = regions.changedLines(Side.RIGHT),
            leftLines = leftLines.allLines(),
            rightLines = rightLines.allLines(),
        )

        return when (alignment) {
            is AlignmentBuilder.Result.Inconsistent -> SemanticDiffResult.Unsupported(
                "sem output cannot be aligned: ${alignment.reason}",
            )

            is AlignmentBuilder.Result.Alignment -> SemanticDiffResult.Changed(
                granularity = granularity,
                alignment = alignment.pairs,
                spans = emptyList(),
                regions = regions.sortedWith(compareBy({ it.side }, { it.startLine }, { it.endLine })),
            )
        }
    }

    private fun List<ChangedRegion>.changedLines(side: Side): Set<Int> =
        filter { it.side == side }.flatMapTo(mutableSetOf()) { it.startLine until it.endLine }

    /**
     * Keeps only the most specific entity of each nest.
     *
     * sem reports containers as well as their members — a modified method also shows up as a
     * modified class and as a modified `module-level` — so the ranges nest, and nested ranges are
     * not expressible as a line alignment. The container carries no information its members do not,
     * so the innermost one wins.
     */
    private fun innermost(changes: List<SemChange>): List<SemChange> =
        changes.filterNot { outer -> changes.any { inner -> inner !== outer && inner.isNestedIn(outer) } }

    /**
     * Containment on **both** sides, and only between entities that exist on both sides.
     *
     * Comparing the left side alone made an added entity — whose left range is empty — look like it
     * was nested inside a deleted entity at the same place, so the deletion was dropped and one of
     * the two changes vanished from the diff.
     */
    private fun SemChange.isNestedIn(other: SemChange): Boolean {
        if (!spansBothSides() || !other.spansBothSides()) return false

        val inner = toBlock()
        val outer = other.toBlock()
        val contained = inner.leftStart >= outer.leftStart && inner.leftEnd <= outer.leftEnd &&
            inner.rightStart >= outer.rightStart && inner.rightEnd <= outer.rightEnd
        val smaller = (inner.leftEnd - inner.leftStart) < (outer.leftEnd - outer.leftStart) ||
            (inner.rightEnd - inner.rightStart) < (outer.rightEnd - outer.rightStart)
        return contained && smaller
    }

    private fun SemChange.spansBothSides(): Boolean =
        oldStartLine != null && oldEndLine != null && startLine != null && endLine != null

    /** sem's ranges are 1-based inclusive; ours are 0-based half-open. */
    private fun SemChange.toBlock(): AlignmentBuilder.ChangedBlock {
        val leftStart = oldStartLine?.let { it - 1 } ?: (startLine?.let { it - 1 } ?: 0)
        val leftEnd = oldEndLine ?: leftStart
        val rightStart = startLine?.let { it - 1 } ?: (oldStartLine?.let { it - 1 } ?: 0)
        val rightEnd = endLine ?: rightStart
        return AlignmentBuilder.ChangedBlock(leftStart, leftEnd, rightStart, rightEnd)
    }

    /**
     * sem's own classification, taken at face value.
     *
     * An earlier version also inferred moves, by looking for entities whose position could not be
     * explained without one. That invented information the tool had not reported: sem calls a
     * swapped pair "modified", and relabelling it "moved" told the reader something sem never said.
     * If sem says modified, it is modified.
     */
    private fun toRegions(change: SemChange): List<ChangedRegion> {
        val kind = when (change.changeType) {
            "added" -> RegionChange.ADDED
            "deleted" -> RegionChange.DELETED
            "moved", "reordered" -> RegionChange.MOVED
            "renamed" -> RegionChange.RENAMED
            else -> RegionChange.MODIFIED
        }

        // A deleted entity exists only on the left and an added one only on the right. sem echoes a
        // range for both sides regardless, and drawing a "deleted legacyRate" box over the added
        // method that took its place says something sem did not.
        val sides = when (kind) {
            RegionChange.DELETED -> setOf(Side.LEFT)
            RegionChange.ADDED -> setOf(Side.RIGHT)
            else -> setOf(Side.LEFT, Side.RIGHT)
        }

        val regions = mutableListOf<ChangedRegion>()
        if (Side.LEFT in sides && change.oldStartLine != null && change.oldEndLine != null) {
            regions += ChangedRegion(
                side = Side.LEFT,
                startLine = change.oldStartLine - 1,
                endLine = change.oldEndLine,
                change = kind,
                entityKind = change.entityType,
                entityName = change.entityName,
                counterpartStartLine = change.startLine?.let { it - 1 },
            )
        }
        if (Side.RIGHT in sides && change.startLine != null && change.endLine != null) {
            regions += ChangedRegion(
                side = Side.RIGHT,
                startLine = change.startLine - 1,
                endLine = change.endLine,
                change = kind,
                entityKind = change.entityType,
                entityName = change.entityName,
                counterpartStartLine = change.oldStartLine?.let { it - 1 },
            )
        }
        return regions
    }

    private companion object {
        val JSON = Json { ignoreUnknownKeys = true }
    }
}
