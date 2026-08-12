package dev.ov7a.semdiff.model

import dev.ov7a.semdiff.model.AlignmentBuilder.ChangedBlock
import dev.ov7a.semdiff.model.AlignmentBuilder.Result
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AlignmentBuilderTest {

    @Test
    fun `blocks with matching gaps produce a full alignment`() {
        val result = AlignmentBuilder.fromChangedBlocks(
            listOf(ChangedBlock(1, 2, 1, 2)),
            leftLineCount = 3,
            rightLineCount = 3,
        )

        assertThat(pairs(result)).containsExactly(LinePair(0, 0), LinePair(1, 1), LinePair(2, 2))
    }

    @Test
    fun `an insertion block leaves the left side unpaired`() {
        val result = AlignmentBuilder.fromChangedBlocks(
            listOf(ChangedBlock(1, 1, 1, 3)),
            leftLineCount = 2,
            rightLineCount = 4,
        )

        assertThat(pairs(result)).containsExactly(
            LinePair(0, 0),
            LinePair(right = 1),
            LinePair(right = 2),
            LinePair(1, 3),
        )
    }

    @Test
    fun `a replacement pairs what it can and leaves the rest unpaired`() {
        val result = AlignmentBuilder.fromChangedBlocks(
            listOf(ChangedBlock(0, 1, 0, 3)),
            leftLineCount = 1,
            rightLineCount = 3,
        )

        assertThat(pairs(result)).containsExactly(LinePair(0, 0), LinePair(right = 1), LinePair(right = 2))
    }

    @Test
    fun `mismatched gaps are refused rather than guessed`() {
        val result = AlignmentBuilder.fromChangedBlocks(
            listOf(ChangedBlock(0, 1, 0, 1), ChangedBlock(2, 3, 4, 5)),
            leftLineCount = 4,
            rightLineCount = 6,
        )

        assertThat(reason(result)).contains("unchanged run before block 1")
    }

    @Test
    fun `a mismatched tail is refused`() {
        val result = AlignmentBuilder.fromChangedBlocks(
            listOf(ChangedBlock(0, 1, 0, 1)),
            leftLineCount = 3,
            rightLineCount = 5,
        )

        assertThat(reason(result)).contains("after the last block")
    }

    /**
     * A move is exactly what a monotonic alignment cannot express, so it must be refused. Which
     * check catches it first is an implementation detail — only the refusal is the contract.
     */
    @Test
    fun `crossing blocks are refused`() {
        val result = AlignmentBuilder.fromChangedBlocks(
            listOf(ChangedBlock(0, 1, 4, 5), ChangedBlock(4, 5, 0, 1)),
            leftLineCount = 6,
            rightLineCount = 6,
        )

        assertThat(result).isInstanceOf(Result.Inconsistent::class.java)
    }

    @Test
    fun `a block that starts before the previous one ended is refused`() {
        val result = AlignmentBuilder.fromChangedBlocks(
            listOf(ChangedBlock(0, 3, 0, 3), ChangedBlock(2, 4, 2, 4)),
            leftLineCount = 5,
            rightLineCount = 5,
        )

        assertThat(reason(result)).contains("overlaps or precedes")
    }

    @Test
    fun `unreported lines are matched by content`() {
        val result = AlignmentBuilder.fromChangedLinesUsingContent(
            leftChanged = setOf(1),
            rightChanged = setOf(1, 2),
            leftLines = listOf("a", "OLD", "z"),
            rightLines = listOf("a", "NEW", "MORE", "z"),
        )

        assertThat(pairs(result)).containsExactly(
            LinePair(0, 0),
            LinePair(1, 1),
            LinePair(right = 2),
            LinePair(2, 3),
        )
    }

    @Test
    fun `an asymmetric report still aligns the lines that correspond`() {
        // Only the right side is marked, but every line still has a counterpart.
        val result = AlignmentBuilder.fromChangedLinesUsingContent(
            leftChanged = emptySet(),
            rightChanged = setOf(1),
            leftLines = listOf("a", "b", "c"),
            rightLines = listOf("a", "B", "c"),
        )

        assertThat(pairs(result)).containsExactly(LinePair(0, 0), LinePair(1, 1), LinePair(2, 2))
    }

    /**
     * The case that used to fall back entirely: a tool reporting a moved block emits crossing
     * ranges. A move cannot be a monotonic alignment, so it comes out as a delete plus an insert —
     * the same way a line diff shows one.
     */
    @Test
    fun `a moved block becomes a deletion and an insertion`() {
        val left = listOf("keep", "moved1", "moved2", "middle", "tail")
        val right = listOf("keep", "middle", "moved1", "moved2", "tail")

        val result = AlignmentBuilder.fromChangedLinesUsingContent(
            leftChanged = setOf(1, 2),
            rightChanged = setOf(2, 3),
            leftLines = left,
            rightLines = right,
        )

        val alignment = pairs(result)
        // Every line of both documents is accounted for exactly once, in order.
        assertThat(alignment.mapNotNull { it.left }).isEqualTo(left.indices.toList())
        assertThat(alignment.mapNotNull { it.right }).isEqualTo(right.indices.toList())
        assertThat(alignment.count { it.right == null }).isEqualTo(2)
        assertThat(alignment.count { it.left == null }).isEqualTo(2)
    }

    @Test
    fun `documents with nothing in common still produce a full alignment`() {
        val result = AlignmentBuilder.fromChangedLinesUsingContent(
            leftChanged = setOf(0, 1),
            rightChanged = setOf(0),
            leftLines = listOf("x", "y"),
            rightLines = listOf("z"),
        )

        val alignment = pairs(result)
        assertThat(alignment.mapNotNull { it.left }).containsExactly(0, 1)
        assertThat(alignment.mapNotNull { it.right }).containsExactly(0)
    }

    @Test
    fun `no blocks means everything aligns`() {
        val result = AlignmentBuilder.fromChangedBlocks(emptyList(), 2, 2)

        assertThat(pairs(result)).containsExactly(LinePair(0, 0), LinePair(1, 1))
    }

    private fun pairs(result: Result): List<LinePair> {
        assertThat(result).isInstanceOf(Result.Alignment::class.java)
        return (result as Result.Alignment).pairs
    }

    private fun reason(result: Result): String {
        assertThat(result).isInstanceOf(Result.Inconsistent::class.java)
        return (result as Result.Inconsistent).reason
    }
}
