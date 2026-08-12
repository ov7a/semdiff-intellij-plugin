package dev.ov7a.semdiff.diff

import com.intellij.diff.comparison.ComparisonPolicy
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.ov7a.semdiff.ide.SemanticDiffNotifications
import dev.ov7a.semdiff.model.ChangedSpan
import dev.ov7a.semdiff.model.Granularity
import dev.ov7a.semdiff.model.LinePair
import dev.ov7a.semdiff.model.SemanticDiffResult
import dev.ov7a.semdiff.model.Side

/** Exercises the whole in-IDE path: temp files, process execution, parsing, fragment building. */
class SemanticDiffComputerTest : BasePlatformTestCase() {

    private val left = "fun total(): Int {\n    val sum = 1\n    return sum\n}\n"
    private val right = "fun total(): Int {\n    val subtotal = 1\n    return subtotal\n}\n"

    override fun setUp() {
        super.setUp()
        SemanticDiffNotifications.resetForTests()
    }

    fun `test tool fragments reach the viewer`() {
        val handler = FakeHandler(
            SemanticDiffResult.Changed(
                granularity = Granularity.INTRA_LINE,
                alignment = (0..4).map { LinePair(it, it) },
                spans = listOf(
                    ChangedSpan(Side.LEFT, 1, 8, 11),
                    ChangedSpan(Side.RIGHT, 1, 8, 16),
                ),
            ),
        )

        val fragments = compute(handler)

        assertEquals(1, fragments.size)
        val fragment = fragments.single()
        assertEquals(1, fragment.startLine1)
        assertEquals(2, fragment.endLine1)
        assertEquals(1, fragment.startLine2)
        assertEquals(2, fragment.endLine2)

        val inner = fragment.innerFragments!!.single()
        assertEquals(8, inner.startOffset1)
        assertEquals(11, inner.endOffset1)
        assertEquals(8, inner.startOffset2)
        assertEquals(16, inner.endOffset2)
    }

    fun `test unchanged result produces no fragments`() {
        assertEmpty(compute(FakeHandler(SemanticDiffResult.Unchanged)))
    }

    fun `test unsupported result falls back to the built-in diff`() {
        val fragments = compute(FakeHandler(SemanticDiffResult.Unsupported("tool exploded")))

        // The texts really do differ, so a fallback must still describe the change.
        assertTrue("expected built-in fragments, got none", fragments.isNotEmpty())
    }

    fun `test a result the planner rejects falls back to the built-in diff`() {
        val handler = FakeHandler(
            SemanticDiffResult.Changed(
                granularity = Granularity.INTRA_LINE,
                // Only one line aligned for a five-line document.
                alignment = listOf(LinePair(0, 0)),
            ),
        )

        assertTrue(compute(handler).isNotEmpty())
    }

    /**
     * Regression: an earlier version fell back to the built-in computer for anything but
     * ComparisonPolicy.DEFAULT, which silently disabled the plugin for everyone who has
     * "Ignore whitespaces" turned on.
     */
    fun `test the tool runs under every ignore policy`() {
        ComparisonPolicy.entries.forEach { policy ->
            val handler = FakeHandler(SemanticDiffResult.Unchanged)

            val fragments = compute(handler, policy = policy)

            assertEquals("policy $policy did not reach the tool", 1, handler.invocations)
            assertEmpty(fragments)
        }
    }

    fun `test inner fragments are dropped when the viewer does not want them`() {
        val handler = FakeHandler(
            SemanticDiffResult.Changed(
                granularity = Granularity.INTRA_LINE,
                alignment = (0..4).map { LinePair(it, it) },
                spans = listOf(ChangedSpan(Side.LEFT, 1, 8, 11), ChangedSpan(Side.RIGHT, 1, 8, 16)),
            ),
        )

        val fragments = compute(handler, innerChanges = false)

        assertNull(fragments.single().innerFragments)
    }

    fun `test the last successful result is kept for the rich viewer`() {
        val changed = SemanticDiffResult.Changed(
            granularity = Granularity.INTRA_LINE,
            alignment = (0..4).map { LinePair(it, it) },
            spans = listOf(ChangedSpan(Side.LEFT, 1, 8, 11), ChangedSpan(Side.RIGHT, 1, 8, 16)),
        )
        val computer = computerFor(FakeHandler(changed))

        computer.compute(left, right, ComparisonPolicy.DEFAULT, true, EmptyProgressIndicator())

        assertEquals(changed, computer.lastChanged())
    }

    fun `test a fallback clears the result the rich viewer would colour`() {
        val computer = computerFor(FakeHandler(SemanticDiffResult.Unsupported("nope")))

        computer.compute(left, right, ComparisonPolicy.DEFAULT, true, EmptyProgressIndicator())

        assertNull(computer.lastChanged())
    }

    private fun computerFor(handler: FakeHandler) =
        SemanticDiffComputer(project, handler.invocation(), "Sample.kt")

    private fun compute(
        handler: FakeHandler,
        policy: ComparisonPolicy = ComparisonPolicy.DEFAULT,
        innerChanges: Boolean = true,
    ) = computerFor(handler).compute(left, right, policy, innerChanges, EmptyProgressIndicator())
}
