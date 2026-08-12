package dev.ov7a.semdiff.diff

import com.intellij.diff.DiffTool
import com.intellij.diff.impl.DiffRequestProcessor
import com.intellij.diff.impl.DiffSettingsHolder.DiffSettings
import com.intellij.diff.tools.fragmented.UnifiedDiffTool
import com.intellij.diff.tools.simple.SimpleDiffTool
import com.intellij.diff.util.DiffPlaces
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.ov7a.semdiff.ide.SemanticDiffSettings
import dev.ov7a.semdiff.ide.ToolEntry

/**
 * How semantic diff becomes the default, and — just as important — how the user gets back.
 *
 * The first implementation used a `DiffToolSubstitutor`. That made the built-in viewer unreachable:
 * `switchToDiffTool` starts with `if (isSameToolOrSubstitutor(chosen, active)) return`, and a
 * substitutor mapping `SimpleDiffTool -> SemanticDiffTool` makes that true whenever the user picks
 * Side-by-side while Semantic is showing, so the click did nothing at all.
 */
class SemanticDiffToolOrderTest : BasePlatformTestCase() {

    private val toolName: String = SemanticDiffTool::class.java.canonicalName
    private val places = listOf(DiffPlaces.DEFAULT, DiffPlaces.CHANGES_VIEW, DiffPlaces.COMMIT_DIALOG)

    override fun setUp() {
        super.setUp()
        places.forEach { DiffSettings.getSettings(it).diffToolsOrder = emptyList() }
        SemanticDiffSettings.instance.apply {
            tools = mutableListOf(
                ToolEntry().apply {
                    name = "difft"
                    handlerId = "difftastic"
                    executablePath = "/bin/echo"
                },
            )
            activeToolName = "difft"
        }
    }

    override fun tearDown() {
        try {
            places.forEach { DiffSettings.getSettings(it).diffToolsOrder = emptyList() }
            SemanticDiffSettings.instance.apply {
                tools = mutableListOf()
                activeToolName = ""
            }
        } finally {
            super.tearDown()
        }
    }

    fun `test the tool becomes first for every diff place`() {
        SemanticDiffToolOrder.makeDefault()

        places.forEach { place ->
            assertTrue("not default for $place", SemanticDiffToolOrder.isDefaultFor(place))
        }
    }

    /**
     * The platform persists the order when the user switches viewers, so a user who moved semantic
     * diff down must not have it pushed back on the next start.
     */
    fun `test a place that already mentions the tool is left alone`() {
        val existing = listOf(SimpleDiffTool::class.java.canonicalName, toolName)
        DiffSettings.getSettings(DiffPlaces.DEFAULT).diffToolsOrder = existing

        SemanticDiffToolOrder.makeDefault()

        assertEquals(existing, DiffSettings.getSettings(DiffPlaces.DEFAULT).diffToolsOrder)
        assertFalse(SemanticDiffToolOrder.isDefaultFor(DiffPlaces.DEFAULT))
    }

    fun `test making it default is idempotent`() {
        SemanticDiffToolOrder.makeDefault()
        val afterFirst = DiffSettings.getSettings(DiffPlaces.DEFAULT).diffToolsOrder

        SemanticDiffToolOrder.makeDefault()

        assertEquals(afterFirst, DiffSettings.getSettings(DiffPlaces.DEFAULT).diffToolsOrder)
    }

    /**
     * The core regression. Ordering only decides who goes first; the platform must still be able to
     * resolve the built-in viewer as a distinct tool, which is what a substitutor broke.
     */
    fun `test the built-in tools remain reachable and distinct`() {
        SemanticDiffToolOrder.makeDefault()

        val order = DiffRequestProcessor.getToolOrderFromSettings(
            DiffSettings.getSettings(DiffPlaces.DEFAULT),
            listOf(SemanticDiffTool(), SimpleDiffTool.INSTANCE, UnifiedDiffTool.INSTANCE),
        )

        assertTrue("semantic diff is not first: $order", order.first() is SemanticDiffTool)
        assertTrue("side-by-side is missing", order.any { it is SimpleDiffTool })
        assertTrue("unified is missing", order.any { it is UnifiedDiffTool })
    }

    /** No substitutor may be registered any more, or the chooser silently stops working. */
    fun `test no diff tool substitutor is registered by this plugin`() {
        val substitutors = com.intellij.diff.impl.DiffToolSubstitutor.EP_NAME.extensionList
            .filter { it.javaClass.name.startsWith("dev.ov7a.semdiff") }

        assertEmpty(substitutors)
    }

    fun `test the semantic tool is registered exactly once`() {
        val ours = DiffTool.EP_NAME.extensionList.filterIsInstance<SemanticDiffTool>()

        assertEquals(1, ours.size)
    }
}
