package dev.ov7a.semdiff.diff

import com.intellij.diff.comparison.ComparisonPolicy
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.diff.util.DiffUserDataKeysEx
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.util.Disposer
import dev.ov7a.semdiff.ide.SemanticDiffNotifications
import dev.ov7a.semdiff.model.RegionChange
import dev.ov7a.semdiff.model.Side
import dev.ov7a.semdiff.tools.HandlerRegistry
import dev.ov7a.semdiff.tools.ToolInvocation
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText

/**
 * The showcase's moved, added and deleted methods.
 *
 * Every tool used to fall back on a file containing a move: crossing entity ranges are not a
 * monotonic line alignment. Moving code is an everyday change, so falling back made the plugin
 * useless for a large share of real diffs.
 */
class MovedCodeTest : BasePlatformTestCase() {

    private val corpus: Path = Path.of(
        System.getProperty("semdiff.testData") ?: error("semdiff.testData not set"),
    ).resolve("cases/showcase")

    private val left by lazy { corpus.resolve("left.java").readText() }
    private val right by lazy { corpus.resolve("right.java").readText() }

    override fun setUp() {
        super.setUp()
        SemanticDiffNotifications.resetForTests()
        // The viewer only draws while this is on; it is the setting under test here.
        dev.ov7a.semdiff.ide.SemanticDiffSettings.instance.useExperimentalViewer = true
    }

    override fun tearDown() {
        try {
            dev.ov7a.semdiff.ide.SemanticDiffSettings.instance.useExperimentalViewer = false
        } finally {
            super.tearDown()
        }
    }

    fun `test difftastic renders a file with moved methods`() = assertRenders("difftastic")

    fun `test sem renders a file with moved methods`() = assertRenders("sem")

    fun `test diffsitter renders a file with moved methods`() = assertRenders("diffsitter")

    private fun assertRenders(handlerId: String) {
        val executable = tool(handlerId) ?: return

        val fragments = SemanticDiffComputer(
            project,
            ToolInvocation.defaults(HandlerRegistry.byId(handlerId)!!, executable),
            "Pricing.java",
        ).compute(left, right, ComparisonPolicy.DEFAULT, true, EmptyProgressIndicator())

        assertTrue("$handlerId produced no fragments", fragments.isNotEmpty())

        // Fragments must stay sorted and non-overlapping, or the viewer paints nonsense. A move is
        // the case most likely to break that, since it is expressed as a delete plus an insert.
        fragments.zipWithNext { previous, next ->
            assertTrue(
                "$handlerId: fragments overlap on the left",
                previous.endLine1 <= next.startLine1,
            )
            assertTrue(
                "$handlerId: fragments overlap on the right",
                previous.endLine2 <= next.startLine2,
            )
        }
    }

    /**
     * Two changed entities next to each other must not be drawn as one block. They used to be: the
     * planner merged every adjacent changed line into a single fragment, so neighbouring methods
     * read as one enormous change spanning both.
     */
    fun `test adjacent entities are separate fragments`() {
        val executable = tool("sem") ?: return
        val regions = regions(executable).filter { it.side == Side.LEFT }
        val fragments = SemanticDiffComputer(
            project,
            ToolInvocation.defaults(HandlerRegistry.byId("sem")!!, executable),
            "Pricing.java",
        ).compute(left, right, ComparisonPolicy.DEFAULT, true, EmptyProgressIndicator())

        // No fragment may swallow two whole reported entities.
        fragments.forEach { fragment ->
            val swallowed = regions.count { it.startLine >= fragment.startLine1 && it.endLine <= fragment.endLine1 }
            assertTrue("one fragment covers $swallowed entities", swallowed <= 1)
        }
    }

    /** sem's own classification, taken as given: nothing infers a move it did not report. */
    fun `test only the reorder sem reported is a move`() {
        val executable = tool("sem") ?: return

        val moved = regions(executable).filter { it.change == RegionChange.MOVED }

        assertEquals("expected exactly the swapped pair, both sides", 2, moved.size)
        assertTrue("a move has both sides", moved.any { it.side == Side.LEFT } && moved.any { it.side == Side.RIGHT })
        moved.forEach { assertNotNull("moved ${it.entityName} has no counterpart", it.counterpartStartLine) }
    }

    /** An added method exists only on the right, a deleted one only on the left. */
    fun `test added and deleted entities are reported on one side each`() {
        val executable = tool("sem") ?: return
        val regions = regions(executable)

        val added = regions.filter { it.change == RegionChange.ADDED }
        val deleted = regions.filter { it.change == RegionChange.DELETED }

        assertEquals("added should be right-side only: $added", listOf(Side.RIGHT), added.map { it.side })
        assertEquals("deleted should be left-side only: $deleted", listOf(Side.LEFT), deleted.map { it.side })
    }

    private fun regions(executable: Path): List<dev.ov7a.semdiff.model.ChangedRegion> {
        val computer = SemanticDiffComputer(
            project,
            ToolInvocation.defaults(HandlerRegistry.byId("sem")!!, executable),
            "Pricing.java",
        )
        computer.compute(left, right, ComparisonPolicy.DEFAULT, true, EmptyProgressIndicator())
        return computer.lastChanged()!!.regions
    }

    fun `test the experimental viewer boxes every reported entity`() {
        val executable = tool("sem") ?: return

        val request = SimpleDiffRequest(
            "test",
            listOf(content(left), content(right)),
            listOf("Left", "Right"),
        )
        val computer = SemanticDiffComputer(
            project,
            ToolInvocation.defaults(HandlerRegistry.byId("sem")!!, executable),
            "Pricing.java",
        )
        request.putUserData(DiffUserDataKeysEx.CUSTOM_DIFF_COMPUTER, computer)

        val viewer = RichSemanticDiffViewer(context(), request, computer)
        try {
            viewer.init()
            viewer.rediff(true)

            // Must be ours: the editor has boxed highlighters of its own, so "something is boxed"
            // would pass without the plugin drawing anything. Only ours carry an explanation.
            val ours = listOf(com.intellij.diff.util.Side.LEFT, com.intellij.diff.util.Side.RIGHT)
                .flatMap { viewer.getEditor(it).markupModel.allHighlighters.toList() }
                .filter { it.getTextAttributes(null)?.effectType == EffectType.BOXED }
                .filter { (it.errorStripeTooltip as? String)?.isNotBlank() == true }

            assertTrue("no entity is boxed and named", ours.isNotEmpty())
        } finally {
            Disposer.dispose(viewer)
        }
    }

    private fun content(text: String) =
        com.intellij.diff.DiffContentFactory.getInstance()
            .create(project, text, com.intellij.openapi.fileTypes.PlainTextFileType.INSTANCE)

    private fun context(): com.intellij.diff.DiffContext = object : com.intellij.diff.DiffContext() {
        private val data = com.intellij.openapi.util.UserDataHolderBase()

        override fun getProject() = this@MovedCodeTest.project
        override fun isWindowFocused() = true
        override fun isFocusedInWindow() = true
        override fun requestFocusInWindow() = Unit
        override fun <T : Any?> getUserData(key: com.intellij.openapi.util.Key<T>): T? = data.getUserData(key)
        override fun <T : Any?> putUserData(key: com.intellij.openapi.util.Key<T>, value: T?) =
            data.putUserData(key, value)
    }.also {
        it.putUserData(
            com.intellij.diff.impl.DiffSettingsHolder.DiffSettings.KEY,
            com.intellij.diff.impl.DiffSettingsHolder.DiffSettings.getSettings(),
        )
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
