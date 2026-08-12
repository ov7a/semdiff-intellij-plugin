package dev.ov7a.semdiff.diff

import com.intellij.diff.comparison.ComparisonPolicy
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.ov7a.semdiff.ide.SemanticDiffNotifications
import dev.ov7a.semdiff.tools.HandlerRegistry
import dev.ov7a.semdiff.tools.ToolInvocation
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists

/**
 * The one test that runs a real binary through the real in-IDE path.
 *
 * Everything else is split: the golden suite pins what the tools emit, and the fake-handler tests
 * pin how the viewer consumes a model. This joins them, so a break in the seam between the two
 * cannot pass unnoticed.
 */
class RealToolEndToEndTest : BasePlatformTestCase() {

    private val left = """
        package billing

        fun totalFor(order: Order): Money {
            val sum = order.lines.sumOf { it.amount }
            return Money(sum, order.currency)
        }
    """.trimIndent() + "\n"

    private val right = """
        package billing

        fun totalFor(order: Order): Money {
            val subtotal = order.lines.sumOf { it.amount }
            return Money(subtotal, order.currency)
        }
    """.trimIndent() + "\n"

    override fun setUp() {
        super.setUp()
        SemanticDiffNotifications.resetForTests()
    }

    fun `test difftastic drives the in-IDE diff computer`() {
        val executable = difftastic() ?: return

        val handler = HandlerRegistry.byId("difftastic")!!
        val computer = SemanticDiffComputer(
            project,
            ToolInvocation.defaults(handler, executable),
            "Billing.kt",
        )

        val fragments = computer.compute(left, right, ComparisonPolicy.DEFAULT, true, EmptyProgressIndicator())

        // Two lines differ, and only by a renamed local, so difftastic should report exactly those
        // two lines with inner spans rather than the whole function.
        assertEquals(1, fragments.size)
        val fragment = fragments.single()
        assertEquals(3, fragment.startLine1)
        assertEquals(5, fragment.endLine1)
        assertEquals(3, fragment.startLine2)
        assertEquals(5, fragment.endLine2)
        assertNotNull("expected intra-line spans", fragment.innerFragments)
        assertTrue(fragment.innerFragments!!.isNotEmpty())

        // Every inner fragment must sit inside its enclosing fragment on both sides, or the viewer
        // would paint highlights over the wrong text.
        val length1 = fragment.endOffset1 - fragment.startOffset1
        val length2 = fragment.endOffset2 - fragment.startOffset2
        fragment.innerFragments!!.forEach { inner ->
            assertTrue("inner $inner escapes side 1", inner.startOffset1 >= 0 && inner.endOffset1 <= length1)
            assertTrue("inner $inner escapes side 2", inner.startOffset2 >= 0 && inner.endOffset2 <= length2)
        }

        assertNotNull(computer.lastChanged())
    }

    fun `test identical content produces no fragments`() {
        val executable = difftastic() ?: return

        val handler = HandlerRegistry.byId("difftastic")!!
        val computer = SemanticDiffComputer(project, ToolInvocation.defaults(handler, executable), "Billing.kt")

        assertEmpty(computer.compute(left, left, ComparisonPolicy.DEFAULT, true, EmptyProgressIndicator()))
    }

    /**
     * Null when the binary is not provisioned, unless `-Psemdiff.requireTools=true` — the same
     * rule the tool suite uses, so CI can never be quietly green.
     */
    private fun difftastic(): Path? {
        val configured = System.getProperty("semdiff.tool.difftastic")
        val path = configured?.let(Path::of)
        if (path != null && path.exists() && Files.isExecutable(path)) return path

        check(!System.getProperty("semdiff.requireTools").toBoolean()) {
            "difftastic is not available at '$configured' and -Psemdiff.requireTools=true"
        }
        return null
    }
}
