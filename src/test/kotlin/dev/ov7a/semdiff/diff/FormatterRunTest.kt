package dev.ov7a.semdiff.diff

import com.intellij.diff.comparison.ComparisonManager
import com.intellij.diff.comparison.ComparisonPolicy
import com.intellij.diff.fragments.LineFragment
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.ov7a.semdiff.ide.SemanticDiffNotifications
import dev.ov7a.semdiff.tools.HandlerRegistry
import dev.ov7a.semdiff.tools.ToolInvocation
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText

/**
 * The case where a semantic diff beats a line diff on something that is not merely whitespace.
 *
 * `test-data/cases/formatter-run` re-wraps code across line boundaries without changing a single
 * token. "Ignore whitespaces" cannot undo that — the text that was on one line is now on four — so
 * the built-in comparison marks it either way, while a semantic tool reports nothing at all.
 *
 * Pinned because the claim was wrong twice before it was measured.
 */
class FormatterRunTest : BasePlatformTestCase() {

    private val corpus: Path = Path.of(
        System.getProperty("semdiff.testData") ?: error("semdiff.testData not set"),
    ).resolve("cases/formatter-run")

    private val left by lazy { corpus.resolve("left.java").readText() }
    private val right by lazy { corpus.resolve("right.java").readText() }

    override fun setUp() {
        super.setUp()
        SemanticDiffNotifications.resetForTests()
    }

    fun `test the built-in diff marks it whatever the ignore setting is`() {
        listOf(ComparisonPolicy.DEFAULT, ComparisonPolicy.IGNORE_WHITESPACES).forEach { policy ->
            val builtIn = ComparisonManager.getInstance()
                .compareLinesInner(left, right, policy, EmptyProgressIndicator())

            assertTrue("policy $policy produced no built-in fragments", builtIn.isNotEmpty())
        }
    }

    fun `test difftastic reports no differences at all`() {
        assertSeesNothing("difftastic")
    }

    fun `test diffsitter reports no differences at all`() {
        assertSeesNothing("diffsitter")
    }

    private fun assertSeesNothing(handlerId: String) {
        val executable = tool(handlerId) ?: return

        listOf(ComparisonPolicy.DEFAULT, ComparisonPolicy.IGNORE_WHITESPACES).forEach { policy ->
            val semantic: List<LineFragment> = SemanticDiffComputer(
                project,
                ToolInvocation.defaults(HandlerRegistry.byId(handlerId)!!, executable),
                "Shipping.java",
            ).compute(left, right, policy, true, EmptyProgressIndicator())

            assertEmpty("$handlerId marked something under $policy: $semantic", semantic)
        }
    }

    private fun tool(id: String): Path? {
        val configured = System.getProperty("semdiff.tool.$id")
        val path = configured?.let(Path::of)
        if (path != null && path.exists() && Files.isExecutable(path)) return path

        check(!System.getProperty("semdiff.requireTools").toBoolean()) {
            "$id is not available at '$configured' and -Psemdiff.requireTools=true"
        }
        return null
    }
}
