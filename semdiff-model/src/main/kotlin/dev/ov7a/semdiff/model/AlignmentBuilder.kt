package dev.ov7a.semdiff.model

/**
 * Builds a line alignment for tools that do not report one.
 *
 * difftastic emits `aligned_lines` directly. diffsitter reports only which lines changed on each
 * side, and sem only which entities changed, so for those the correspondence has to be worked out
 * here — see [fromChangedLinesUsingContent], which is what both use.
 */
object AlignmentBuilder {

    /** Guard on the O(n*m) matching below; beyond this the file is not worth aligning by content. */
    private const val MAX_ANCHOR_PAIRS = 25_000_000L

    /** A changed region, half-open and 0-based. An empty range means a pure insertion or deletion. */
    data class ChangedBlock(
        val leftStart: Int,
        val leftEnd: Int,
        val rightStart: Int,
        val rightEnd: Int,
    )

    sealed interface Result {
        data class Alignment(val pairs: List<LinePair>) : Result
        data class Inconsistent(val reason: String) : Result
    }

    /**
     * From explicit changed blocks, as sem reports them.
     *
     * The blocks must be sorted and non-overlapping on both sides, and the unchanged runs between
     * them must be the same length on both sides — otherwise the tool has left a change unreported
     * and any alignment we invent would be wrong. Reordered entities fail here by design: a move
     * cannot be expressed as a monotonic line alignment.
     */
    fun fromChangedBlocks(
        blocks: List<ChangedBlock>,
        leftLineCount: Int,
        rightLineCount: Int,
    ): Result {
        val pairs = mutableListOf<LinePair>()
        var left = 0
        var right = 0

        blocks.forEachIndexed { index, block ->
            if (block.leftStart < left || block.rightStart < right) {
                return Result.Inconsistent("block $index overlaps or precedes the previous one")
            }
            if (block.leftEnd < block.leftStart || block.rightEnd < block.rightStart) {
                return Result.Inconsistent("block $index has an inverted range")
            }

            val leftGap = block.leftStart - left
            val rightGap = block.rightStart - right
            if (leftGap != rightGap) {
                return Result.Inconsistent(
                    "unchanged run before block $index is $leftGap lines on the left and $rightGap on the right",
                )
            }

            repeat(leftGap) { pairs += LinePair(left++, right++) }
            appendBlock(pairs, block.leftStart, block.leftEnd, block.rightStart, block.rightEnd)
            left = block.leftEnd
            right = block.rightEnd
        }

        val leftTail = leftLineCount - left
        val rightTail = rightLineCount - right
        if (leftTail != rightTail) {
            return Result.Inconsistent(
                "unchanged run after the last block is $leftTail lines on the left and $rightTail on the right",
            )
        }
        if (leftTail < 0) return Result.Inconsistent("blocks extend past the end of the document")

        repeat(leftTail) { pairs += LinePair(left++, right++) }
        return Result.Alignment(pairs)
    }

    /**
     * From per-side changed lines, matching the *unreported* lines by content.
     *
     * The strict readings above refuse anything they cannot represent exactly, which turned out to
     * be too strict for real edits: a tool that reports a moved method emits crossing ranges, and
     * crossing ranges are not a monotonic line alignment, so a commit that moves code fell back to
     * the built-in diff entirely.
     *
     * This reading keeps what the tool knows — which regions changed — and works out the line
     * correspondence itself, by finding the longest common subsequence of the lines the tool did
     * *not* mark. Whatever lies between two matched lines becomes one changed block, so a move comes
     * out as a deletion in one place and an insertion in the other. That is how a line diff shows a
     * move too; it loses the fact that it *was* a move, which `LineFragment` cannot express anyway.
     */
    fun fromChangedLinesUsingContent(
        leftChanged: Set<Int>,
        rightChanged: Set<Int>,
        leftLines: List<String>,
        rightLines: List<String>,
    ): Result {
        val leftAnchors = leftLines.indices.filterNot { it in leftChanged }
        val rightAnchors = rightLines.indices.filterNot { it in rightChanged }

        if (leftAnchors.size.toLong() * rightAnchors.size > MAX_ANCHOR_PAIRS) {
            return Result.Inconsistent(
                "too many unchanged lines to match up (${leftAnchors.size} x ${rightAnchors.size})",
            )
        }

        val matched = longestCommonSubsequence(
            leftAnchors.map { leftLines[it] },
            rightAnchors.map { rightLines[it] },
        ).map { (l, r) -> leftAnchors[l] to rightAnchors[r] }

        val blocks = mutableListOf<ChangedBlock>()
        var left = 0
        var right = 0
        matched.forEach { (leftAnchor, rightAnchor) ->
            if (leftAnchor > left || rightAnchor > right) {
                blocks += ChangedBlock(left, leftAnchor, right, rightAnchor)
            }
            left = leftAnchor + 1
            right = rightAnchor + 1
        }
        if (left < leftLines.size || right < rightLines.size) {
            blocks += ChangedBlock(left, leftLines.size, right, rightLines.size)
        }

        return fromChangedBlocks(blocks, leftLines.size, rightLines.size)
    }

    /** Index pairs of a longest common subsequence, in order. */
    private fun longestCommonSubsequence(left: List<String>, right: List<String>): List<Pair<Int, Int>> {
        val lengths = Array(left.size + 1) { IntArray(right.size + 1) }
        for (i in left.indices.reversed()) {
            for (j in right.indices.reversed()) {
                lengths[i][j] = if (left[i] == right[j]) {
                    lengths[i + 1][j + 1] + 1
                } else {
                    maxOf(lengths[i + 1][j], lengths[i][j + 1])
                }
            }
        }

        val pairs = mutableListOf<Pair<Int, Int>>()
        var i = 0
        var j = 0
        while (i < left.size && j < right.size) {
            when {
                left[i] == right[j] -> {
                    pairs += i to j
                    i++
                    j++
                }

                lengths[i + 1][j] >= lengths[i][j + 1] -> i++
                else -> j++
            }
        }
        return pairs
    }

    private fun appendBlock(
        pairs: MutableList<LinePair>,
        leftStart: Int,
        leftEnd: Int,
        rightStart: Int,
        rightEnd: Int,
    ) {
        val shared = minOf(leftEnd - leftStart, rightEnd - rightStart)
        repeat(shared) { offset -> pairs += LinePair(leftStart + offset, rightStart + offset) }
        (leftStart + shared until leftEnd).forEach { pairs += LinePair(left = it) }
        (rightStart + shared until rightEnd).forEach { pairs += LinePair(right = it) }
    }
}
