package dev.ov7a.semdiff.model.fragments

import dev.ov7a.semdiff.model.ChangedSpan
import dev.ov7a.semdiff.model.Granularity
import dev.ov7a.semdiff.model.SemanticDiffResult
import dev.ov7a.semdiff.model.Side
import dev.ov7a.semdiff.model.text.LineIndex

/**
 * Turns a tool-agnostic [SemanticDiffResult.Changed] into IntelliJ-shaped fragments.
 *
 * Rejects anything it cannot represent faithfully rather than emitting fragments that would make
 * the viewer throw or paint nonsense; the caller falls back to the built-in diff.
 */
object FragmentPlanner {

    fun plan(
        result: SemanticDiffResult.Changed,
        leftText: String,
        rightText: String,
        includeInnerFragments: Boolean = true,
    ): FragmentPlan {
        val left = LineIndex.of(leftText)
        val right = LineIndex.of(rightText)

        validateAlignment(result, left, right)?.let { return FragmentPlan.Rejected(it) }
        validateSpans(result.spans, left, right)?.let { return FragmentPlan.Rejected(it) }
        validateRegions(result.regions, left, right)?.let { return FragmentPlan.Rejected(it) }

        val useInner = includeInnerFragments && result.granularity == Granularity.INTRA_LINE
        val spansByLine = result.spans.groupBy { it.side to it.line }

        // Regions count as changed as much as spans do. Entity-level tools report only regions, so
        // without this they produce an empty diff whenever the alignment happens to be 1:1 — which
        // is every pair of equal-length files.
        val changedLines = spansByLine.keys + result.regions.flatMap { region ->
            (region.startLine until region.endLine).map { region.side to it }
        }

        // Where a new reported entity begins. A changed run is cut here, so two entities never merge
        // into one fragment: without this, two adjacent changed methods are drawn as a single block
        // and the diff reads as though one enormous thing changed.
        val boundaries = result.regions.mapTo(mutableSetOf()) { it.side to it.startLine }

        val specs = mutableListOf<FragmentSpec>()
        var index = 0
        var leftLine = 0
        var rightLine = 0

        while (index < result.alignment.size) {
            val entry = result.alignment[index]
            if (!isChanged(entry, changedLines)) {
                index++
                leftLine++
                rightLine++
                continue
            }

            val runStartLeft = leftLine
            val runStartRight = rightLine
            var first = true
            while (index < result.alignment.size && isChanged(result.alignment[index], changedLines)) {
                if (!first && startsRegion(result.alignment[index], boundaries)) break
                result.alignment[index].left?.let { leftLine++ }
                result.alignment[index].right?.let { rightLine++ }
                index++
                first = false
            }

            specs += buildSpec(
                startLine1 = runStartLeft,
                endLine1 = leftLine,
                startLine2 = runStartRight,
                endLine2 = rightLine,
                left = left,
                right = right,
                spansByLine = spansByLine,
                includeInner = useInner,
            )
        }

        return FragmentPlan.Fragments(specs)
    }

    private fun startsRegion(
        entry: dev.ov7a.semdiff.model.LinePair,
        boundaries: Set<Pair<Side, Int>>,
    ): Boolean {
        val left = entry.left?.let { (Side.LEFT to it) in boundaries } ?: false
        val right = entry.right?.let { (Side.RIGHT to it) in boundaries } ?: false
        return left || right
    }

    private fun isChanged(entry: dev.ov7a.semdiff.model.LinePair, changedLines: Set<Pair<Side, Int>>): Boolean {
        val leftLine = entry.left
        val rightLine = entry.right
        if (leftLine == null || rightLine == null) return true
        return (Side.LEFT to leftLine) in changedLines || (Side.RIGHT to rightLine) in changedLines
    }

    private fun buildSpec(
        startLine1: Int,
        endLine1: Int,
        startLine2: Int,
        endLine2: Int,
        left: LineIndex,
        right: LineIndex,
        spansByLine: Map<Pair<Side, Int>, List<ChangedSpan>>,
        includeInner: Boolean,
    ): FragmentSpec {
        val startOffset1 = left.startOffset(startLine1)
        val endOffset1 = if (endLine1 > startLine1) left.endOffsetWithSeparator(endLine1 - 1) else startOffset1
        val startOffset2 = right.startOffset(startLine2)
        val endOffset2 = if (endLine2 > startLine2) right.endOffsetWithSeparator(endLine2 - 1) else startOffset2

        val inner = if (includeInner) {
            pairInnerFragments(
                leftSpans = collectSpans(spansByLine, Side.LEFT, startLine1, endLine1, left, startOffset1),
                rightSpans = collectSpans(spansByLine, Side.RIGHT, startLine2, endLine2, right, startOffset2),
                leftLength = endOffset1 - startOffset1,
                rightLength = endOffset2 - startOffset2,
            )
        } else {
            emptyList()
        }

        return FragmentSpec(
            startLine1 = startLine1,
            endLine1 = endLine1,
            startLine2 = startLine2,
            endLine2 = endLine2,
            startOffset1 = startOffset1,
            endOffset1 = endOffset1,
            startOffset2 = startOffset2,
            endOffset2 = endOffset2,
            inner = inner,
        )
    }

    /** Spans of one side of one fragment, as ranges relative to the fragment start. */
    private fun collectSpans(
        spansByLine: Map<Pair<Side, Int>, List<ChangedSpan>>,
        side: Side,
        startLine: Int,
        endLine: Int,
        index: LineIndex,
        fragmentStart: Int,
    ): List<IntRange> =
        (startLine until endLine)
            .flatMap { line -> spansByLine[side to line].orEmpty() }
            .sortedWith(compareBy({ it.line }, { it.startChar }))
            .map { span ->
                val lineStart = index.startOffset(span.line)
                (lineStart + span.startChar - fragmentStart)..(lineStart + span.endChar - fragmentStart)
            }

    /**
     * Pairs per-side novel spans into IntelliJ's two-sided inner fragments.
     *
     * Tools report the sides independently — difftastic emits `lhs.changes` and `rhs.changes`
     * separately with no correspondence between them — while `DiffFragment` is a pair of ranges.
     * Spans are therefore zipped in document order, and a leftover on one side is paired with an
     * empty range placed just after the previous pair on the other side. That keeps the result
     * sorted and non-overlapping on both sides, which is what the viewer requires; it is an
     * approximation of correspondence, not a claim about it.
     */
    private fun pairInnerFragments(
        leftSpans: List<IntRange>,
        rightSpans: List<IntRange>,
        leftLength: Int,
        rightLength: Int,
    ): List<InnerFragmentSpec> {
        val paired = mutableListOf<InnerFragmentSpec>()
        var leftCursor = 0
        var rightCursor = 0

        for (i in 0 until maxOf(leftSpans.size, rightSpans.size)) {
            val leftRange = leftSpans.getOrNull(i)
            val rightRange = rightSpans.getOrNull(i)

            val leftStart = leftRange?.first ?: leftCursor
            val leftEnd = leftRange?.last ?: leftCursor
            val rightStart = rightRange?.first ?: rightCursor
            val rightEnd = rightRange?.last ?: rightCursor

            paired += InnerFragmentSpec(leftStart, leftEnd, rightStart, rightEnd)
            leftCursor = leftEnd
            rightCursor = rightEnd
        }

        return paired.filter { fragment ->
            fragment.startOffset1 in 0..leftLength && fragment.endOffset1 in fragment.startOffset1..leftLength &&
                fragment.startOffset2 in 0..rightLength && fragment.endOffset2 in fragment.startOffset2..rightLength &&
                (fragment.endOffset1 > fragment.startOffset1 || fragment.endOffset2 > fragment.startOffset2)
        }
    }

    /**
     * The alignment must list every line of both documents exactly once and in order. difftastic
     * satisfies this; checking it means a future tool cannot silently produce fragments that point
     * at the wrong lines.
     */
    private fun validateAlignment(
        result: SemanticDiffResult.Changed,
        left: LineIndex,
        right: LineIndex,
    ): String? {
        var expectedLeft = 0
        var expectedRight = 0

        result.alignment.forEachIndexed { i, pair ->
            if (pair.left == null && pair.right == null) {
                return "alignment entry $i has neither side"
            }
            pair.left?.let {
                if (it != expectedLeft) return "alignment is not contiguous on the left at entry $i: expected $expectedLeft, got $it"
                expectedLeft++
            }
            pair.right?.let {
                if (it != expectedRight) return "alignment is not contiguous on the right at entry $i: expected $expectedRight, got $it"
                expectedRight++
            }
        }

        if (expectedLeft != left.lineCount) {
            return "alignment covers $expectedLeft left lines, document has ${left.lineCount}"
        }
        if (expectedRight != right.lineCount) {
            return "alignment covers $expectedRight right lines, document has ${right.lineCount}"
        }
        return null
    }

    private fun validateRegions(
        regions: List<dev.ov7a.semdiff.model.ChangedRegion>,
        left: LineIndex,
        right: LineIndex,
    ): String? {
        regions.forEach { region ->
            val index = if (region.side == Side.LEFT) left else right
            if (region.startLine < 0 || region.endLine > index.lineCount || region.startLine > region.endLine) {
                return "region ${region.startLine}..${region.endLine} on ${region.side} does not fit a " +
                    "document of ${index.lineCount} lines"
            }
        }
        return null
    }

    private fun validateSpans(spans: List<ChangedSpan>, left: LineIndex, right: LineIndex): String? {
        spans.forEach { span ->
            val index = if (span.side == Side.LEFT) left else right
            if (span.line !in 0 until index.lineCount) {
                return "span on ${span.side} refers to line ${span.line}, document has ${index.lineCount} lines"
            }
            val lineLength = index.lineLength(span.line)
            if (span.startChar < 0 || span.endChar > lineLength || span.startChar > span.endChar) {
                return "span ${span.startChar}..${span.endChar} on ${span.side} line ${span.line} " +
                    "does not fit a line of length $lineLength"
            }
        }
        return null
    }
}
