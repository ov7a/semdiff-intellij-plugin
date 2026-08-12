package dev.ov7a.semdiff.model.fragments

import dev.ov7a.semdiff.model.ChangedSpan
import dev.ov7a.semdiff.model.Granularity
import dev.ov7a.semdiff.model.LinePair
import dev.ov7a.semdiff.model.SemanticDiffResult
import dev.ov7a.semdiff.model.Side
import dev.ov7a.semdiff.model.SpanKind
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class FragmentPlannerTest {

    @Test
    fun `unchanged lines produce no fragments`() {
        val plan = plan(
            leftText = "a\nb\n",
            rightText = "a\nb\n",
            alignment = listOf(LinePair(0, 0), LinePair(1, 1), LinePair(2, 2)),
        )

        assertThat(plan.specs).isEmpty()
    }

    @Test
    fun `a span marks its line changed on both sides`() {
        val plan = plan(
            leftText = "val x = 1\nsame\n",
            rightText = "val y = 1\nsame\n",
            alignment = listOf(LinePair(0, 0), LinePair(1, 1), LinePair(2, 2)),
            spans = listOf(
                ChangedSpan(Side.LEFT, 0, 4, 5),
                ChangedSpan(Side.RIGHT, 0, 4, 5),
            ),
        )

        assertThat(plan.specs).hasSize(1)
        with(plan.specs.single()) {
            assertThat(startLine1 to endLine1).isEqualTo(0 to 1)
            assertThat(startLine2 to endLine2).isEqualTo(0 to 1)
            // Offsets cover the whole line including its terminator.
            assertThat(startOffset1 to endOffset1).isEqualTo(0 to 10)
            assertThat(inner).containsExactly(InnerFragmentSpec(4, 5, 4, 5))
        }
    }

    @Test
    fun `an inserted line becomes an empty range on the left`() {
        val plan = plan(
            leftText = "a\nb\n",
            rightText = "a\nnew\nb\n",
            alignment = listOf(LinePair(0, 0), LinePair(null, 1), LinePair(1, 2), LinePair(2, 3)),
        )

        assertThat(plan.specs).hasSize(1)
        with(plan.specs.single()) {
            assertThat(startLine1).isEqualTo(1)
            assertThat(endLine1).isEqualTo(1)
            assertThat(startOffset1).isEqualTo(endOffset1)
            assertThat(startLine2 to endLine2).isEqualTo(1 to 2)
            assertThat(startOffset2 to endOffset2).isEqualTo(2 to 6)
        }
    }

    @Test
    fun `a deleted line becomes an empty range on the right`() {
        val plan = plan(
            leftText = "a\ngone\nb\n",
            rightText = "a\nb\n",
            alignment = listOf(LinePair(0, 0), LinePair(1, null), LinePair(2, 1), LinePair(3, 2)),
        )

        with(plan.specs.single()) {
            assertThat(startLine1 to endLine1).isEqualTo(1 to 2)
            assertThat(startLine2).isEqualTo(endLine2)
        }
    }

    @Test
    fun `adjacent changed lines collapse into one fragment`() {
        val plan = plan(
            leftText = "a\nb\nc\n",
            rightText = "a\nB\nC\n",
            alignment = listOf(LinePair(0, 0), LinePair(1, 1), LinePair(2, 2), LinePair(3, 3)),
            spans = listOf(
                ChangedSpan(Side.LEFT, 1, 0, 1),
                ChangedSpan(Side.LEFT, 2, 0, 1),
                ChangedSpan(Side.RIGHT, 1, 0, 1),
                ChangedSpan(Side.RIGHT, 2, 0, 1),
            ),
        )

        assertThat(plan.specs).hasSize(1)
        assertThat(plan.specs.single().startLine1 to plan.specs.single().endLine1).isEqualTo(1 to 3)
    }

    @Test
    fun `inner offsets are relative to the fragment, not the document`() {
        val plan = plan(
            leftText = "keep\nval x = 1\n",
            rightText = "keep\nval y = 1\n",
            alignment = listOf(LinePair(0, 0), LinePair(1, 1), LinePair(2, 2)),
            spans = listOf(
                ChangedSpan(Side.LEFT, 1, 4, 5),
                ChangedSpan(Side.RIGHT, 1, 4, 5),
            ),
        )

        assertThat(plan.specs.single().inner).containsExactly(InnerFragmentSpec(4, 5, 4, 5))
    }

    @Test
    fun `uneven span counts pair leftovers with an empty range`() {
        val plan = plan(
            leftText = "f(a)\n",
            rightText = "f(a, b)\n",
            alignment = listOf(LinePair(0, 0), LinePair(1, 1)),
            spans = listOf(
                ChangedSpan(Side.RIGHT, 0, 4, 6),
            ),
        )

        val inner = plan.specs.single().inner
        assertThat(inner).hasSize(1)
        assertThat(inner.single().startOffset1).isEqualTo(inner.single().endOffset1)
        assertThat(inner.single().startOffset2 to inner.single().endOffset2).isEqualTo(4 to 6)
    }

    @Test
    fun `line-range granularity drops inner fragments`() {
        val result = SemanticDiffResult.Changed(
            granularity = Granularity.LINE_RANGE,
            alignment = listOf(LinePair(0, 0), LinePair(1, 1)),
            spans = listOf(ChangedSpan(Side.LEFT, 0, 0, 1, SpanKind.PLAIN)),
        )

        val plan = FragmentPlanner.plan(result, "abc\n", "xbc\n") as FragmentPlan.Fragments

        assertThat(plan.specs.single().inner).isEmpty()
    }

    @Test
    fun `inner fragments can be suppressed by the caller`() {
        val result = SemanticDiffResult.Changed(
            granularity = Granularity.INTRA_LINE,
            alignment = listOf(LinePair(0, 0), LinePair(1, 1)),
            spans = listOf(ChangedSpan(Side.LEFT, 0, 0, 1)),
        )

        val plan = FragmentPlanner.plan(result, "abc\n", "xbc\n", includeInnerFragments = false)

        assertThat((plan as FragmentPlan.Fragments).specs.single().inner).isEmpty()
    }

    @Test
    fun `a non-contiguous alignment is rejected`() {
        val plan = FragmentPlanner.plan(
            SemanticDiffResult.Changed(
                granularity = Granularity.INTRA_LINE,
                alignment = listOf(LinePair(0, 0), LinePair(2, 1)),
            ),
            leftText = "a\nb\n",
            rightText = "a\nb\n",
        )

        assertThat(plan).isInstanceOf(FragmentPlan.Rejected::class.java)
        assertThat((plan as FragmentPlan.Rejected).reason).contains("not contiguous on the left")
    }

    @Test
    fun `an alignment that does not cover the document is rejected`() {
        val plan = FragmentPlanner.plan(
            SemanticDiffResult.Changed(
                granularity = Granularity.INTRA_LINE,
                alignment = listOf(LinePair(0, 0)),
            ),
            leftText = "a\nb\n",
            rightText = "a\nb\n",
        )

        assertThat((plan as FragmentPlan.Rejected).reason).contains("covers 1 left lines")
    }

    @Test
    fun `an empty alignment entry is rejected`() {
        val plan = FragmentPlanner.plan(
            SemanticDiffResult.Changed(
                granularity = Granularity.INTRA_LINE,
                alignment = listOf(LinePair(null, null)),
            ),
            leftText = "",
            rightText = "",
        )

        assertThat((plan as FragmentPlan.Rejected).reason).contains("neither side")
    }

    @Test
    fun `a span pointing past the end of its line is rejected`() {
        val plan = FragmentPlanner.plan(
            SemanticDiffResult.Changed(
                granularity = Granularity.INTRA_LINE,
                alignment = listOf(LinePair(0, 0), LinePair(1, 1)),
                spans = listOf(ChangedSpan(Side.LEFT, 0, 0, 99)),
            ),
            leftText = "abc\n",
            rightText = "abc\n",
        )

        assertThat((plan as FragmentPlan.Rejected).reason).contains("does not fit a line")
    }

    @Test
    fun `a span on a line the document does not have is rejected`() {
        val plan = FragmentPlanner.plan(
            SemanticDiffResult.Changed(
                granularity = Granularity.INTRA_LINE,
                alignment = listOf(LinePair(0, 0), LinePair(1, 1)),
                spans = listOf(ChangedSpan(Side.LEFT, 7, 0, 1)),
            ),
            leftText = "abc\n",
            rightText = "abc\n",
        )

        assertThat((plan as FragmentPlan.Rejected).reason).contains("refers to line 7")
    }

    @Test
    fun `fragments never violate the LineFragment non-empty requirement`() {
        val plan = plan(
            leftText = "a\nb\nc\n",
            rightText = "a\nx\ny\nc\n",
            alignment = listOf(LinePair(0, 0), LinePair(1, 1), LinePair(null, 2), LinePair(2, 3), LinePair(3, 4)),
            spans = listOf(ChangedSpan(Side.LEFT, 1, 0, 1), ChangedSpan(Side.RIGHT, 1, 0, 1)),
        )

        assertThat(plan.specs).allSatisfy { spec ->
            assertThat(spec.startLine1 != spec.endLine1 || spec.startLine2 != spec.endLine2).isTrue()
        }
    }

    @Test
    fun `fragments are sorted and non-overlapping on both sides`() {
        val plan = plan(
            leftText = "a\nb\nc\nd\ne\n",
            rightText = "A\nb\nC\nd\nE\n",
            alignment = (0..5).map { LinePair(it, it) },
            spans = listOf(0, 2, 4).flatMap {
                listOf(ChangedSpan(Side.LEFT, it, 0, 1), ChangedSpan(Side.RIGHT, it, 0, 1))
            },
        )

        assertThat(plan.specs).hasSize(3)
        plan.specs.zipWithNext { previous, next ->
            assertThat(previous.endOffset1).isLessThanOrEqualTo(next.startOffset1)
            assertThat(previous.endOffset2).isLessThanOrEqualTo(next.startOffset2)
        }
    }

    private fun plan(
        leftText: String,
        rightText: String,
        alignment: List<LinePair>,
        spans: List<ChangedSpan> = emptyList(),
    ): FragmentPlan.Fragments {
        val result = SemanticDiffResult.Changed(Granularity.INTRA_LINE, alignment, spans)
        val plan = FragmentPlanner.plan(result, leftText, rightText)
        assertThat(plan).isInstanceOf(FragmentPlan.Fragments::class.java)
        return plan as FragmentPlan.Fragments
    }
}
