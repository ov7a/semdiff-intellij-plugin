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
 * Proves the showcase case actually shows something, against IntelliJ's own comparison engine.
 *
 * Written because two earlier claims about it turned out to be wrong: the semantic diff never ran
 * at all under a non-default ignore policy, and the "layout only" section was invisible to anyone
 * with "Ignore whitespaces" turned on. Both are now assertions rather than prose.
 */
class ShowcaseContrastTest : BasePlatformTestCase() {

    private val corpus: Path = Path.of(
        System.getProperty("semdiff.testData") ?: error("semdiff.testData not set"),
    ).resolve("cases/showcase")

    private val left by lazy { corpus.resolve("left.java").readText() }
    private val right by lazy { corpus.resolve("right.java").readText() }

    override fun setUp() {
        super.setUp()
        SemanticDiffNotifications.resetForTests()
    }

    /** The regression that made the plugin look broken: any ignore policy disabled it. */
    fun `test the tool runs under every ignore policy`() {
        val executable = difftastic() ?: return

        ComparisonPolicy.entries.forEach { policy ->
            val semantic = semantic(executable, policy)
            val builtIn = builtIn(policy)

            assertFalse(
                "policy $policy: semantic output equals the built-in output, so the tool did not run",
                sameFragments(semantic, builtIn),
            )
        }
    }

    /** The showcase has to look different from the built-in diff, whatever the ignore setting is. */
    fun `test the semantic diff is tidier than the built-in one`() {
        val executable = difftastic() ?: return

        listOf(ComparisonPolicy.DEFAULT, ComparisonPolicy.IGNORE_WHITESPACES).forEach { policy ->
            val semantic = semantic(executable, policy)
            val builtIn = builtIn(policy)

            assertTrue(
                "policy $policy: semantic marked ${leftLines(semantic)} lines in ${semantic.size} " +
                    "fragments, built-in ${leftLines(builtIn)} in ${builtIn.size}",
                leftLines(semantic) < leftLines(builtIn) && semantic.size < builtIn.size,
            )
        }
    }

    /**
     * The semantic answer must not depend on the ignore setting — it is the tool's answer, and the
     * tool is whitespace-insensitive by construction. This is what the removed policy bail-out used
     * to break.
     */
    fun `test the semantic result is the same under every ignore policy`() {
        val executable = difftastic() ?: return

        val results = ComparisonPolicy.entries.map { semantic(executable, it) }

        results.zipWithNext { a, b -> assertTrue("policies disagree", sameFragments(a, b)) }
    }

    /** Sections 3-7 exist so the experimental viewer has kinds to colour. */
    fun `test difftastic reports several distinct syntax kinds`() {
        val executable = difftastic() ?: return
        val computer = computer(executable)
        computer.compute(left, right, ComparisonPolicy.DEFAULT, true, EmptyProgressIndicator())

        val kinds = computer.lastChanged()!!.spans.map { it.kind }.toSet()

        assertTrue("expected at least 4 syntax kinds, got $kinds", kinds.size >= 4)
    }

    /** Switching tools has to change what is drawn, not just which name is selected. */
    fun `test the tools disagree about the same file`() {
        val difftastic = difftastic() ?: return
        val sem = tool("sem") ?: return

        val byDifftastic = semantic(difftastic, ComparisonPolicy.DEFAULT, "difftastic")
        val bySem = semantic(sem, ComparisonPolicy.DEFAULT, "sem")

        assertFalse("difftastic and sem produced identical fragments", sameFragments(byDifftastic, bySem))
        assertTrue("sem marked no lines, so it fell back", leftLines(bySem) > 0)
        // sem is entity-level, so it necessarily covers more lines than difftastic's token spans.
        assertTrue(
            "sem marked ${leftLines(bySem)} lines, difftastic ${leftLines(byDifftastic)}",
            leftLines(bySem) > leftLines(byDifftastic),
        )
    }

    private fun computer(executable: Path, handlerId: String = "difftastic") =
        SemanticDiffComputer(
            project,
            ToolInvocation.defaults(HandlerRegistry.byId(handlerId)!!, executable),
            "Pricing.java",
        )

    private fun semantic(
        executable: Path,
        policy: ComparisonPolicy,
        handlerId: String = "difftastic",
    ): List<LineFragment> =
        computer(executable, handlerId).compute(left, right, policy, true, EmptyProgressIndicator())

    private fun builtIn(policy: ComparisonPolicy): List<LineFragment> =
        ComparisonManager.getInstance().compareLinesInner(left, right, policy, EmptyProgressIndicator())

    private fun leftLines(fragments: List<LineFragment>): Int =
        fragments.sumOf { it.endLine1 - it.startLine1 }

    private fun sameFragments(a: List<LineFragment>, b: List<LineFragment>): Boolean =
        a.size == b.size &&
            a.zip(b).all { (x, y) ->
                x.startLine1 == y.startLine1 && x.endLine1 == y.endLine1 &&
                    x.startLine2 == y.startLine2 && x.endLine2 == y.endLine2
            }

    private fun difftastic(): Path? = tool("difftastic")

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
