package dev.ov7a.semdiff.diff

import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffContext
import com.intellij.diff.impl.DiffSettingsHolder.DiffSettings
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.diff.util.DiffUserDataKeysEx
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.markup.TextAttributes
import dev.ov7a.semdiff.model.RegionChange
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.ov7a.semdiff.ide.SemanticDiffNotifications
import dev.ov7a.semdiff.ide.SemanticDiffSettings
import dev.ov7a.semdiff.ide.ToolEntry
import dev.ov7a.semdiff.model.SpanKind
import dev.ov7a.semdiff.tools.HandlerRegistry
import dev.ov7a.semdiff.tools.ToolInvocation
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists

/**
 * The experimental viewer.
 *
 * Reported as "does nothing", which turned out to be the ignore-policy bail-out starving it of a
 * result. These tests cover the two ways it can still silently do nothing: no colours resolved from
 * the scheme, and no highlighters installed.
 */
class RichSemanticDiffViewerTest : BasePlatformTestCase() {

    private val left = "class A {\n    String s = \"one\";\n    int n = 1;\n}\n"
    private val right = "class A {\n    String s = \"two\";\n    long n = 1;\n}\n"

    override fun setUp() {
        super.setUp()
        SemanticDiffNotifications.resetForTests()
        SemanticDiffSettings.instance.apply {
            useExperimentalViewer = true
            tools = mutableListOf(
                ToolEntry().apply {
                    name = "difft"
                    handlerId = "difftastic"
                    executablePath = difftastic()?.toString().orEmpty()
                },
            )
            activeToolName = "difft"
        }
    }

    override fun tearDown() {
        try {
            SemanticDiffSettings.instance.apply {
                useExperimentalViewer = false
                tools = mutableListOf()
                activeToolName = ""
            }
        } finally {
            super.tearDown()
        }
    }

    /**
     * If the scheme resolves no foreground for a kind, `attributesFor` returns null and the viewer
     * installs nothing — indistinguishable from the feature being broken.
     */
    fun `test each reported kind resolves to a colour`() {
        // The diagnostic override deliberately paints every kind the same; skip while it is on.
        if (SpanColors.isDiagnostic) return
        val scheme = EditorColorsManager.getInstance().globalScheme

        val resolved = SpanColors.colouredKinds().associateWith { SpanColors.attributesFor(it, scheme) }

        resolved.forEach { (kind, attributes) ->
            assertNotNull("the shipped scheme defines no colour for $kind", attributes)
        }
        // Two kinds rendered identically defeat the point of the viewer. Colour alone is hard to
        // tell apart at underline width, so the effect has to vary too.
        val distinct = resolved.values.mapNotNull { it?.effectColor }.toSet()
        assertEquals("kinds share colours: $distinct", resolved.size, distinct.size)
        assertTrue(
            "every kind uses the same effect, so they look alike",
            resolved.values.mapNotNull { it?.effectType }.toSet().size > 1,
        )

        // Foreground must stay untouched: the editor's syntax highlighting already owns it, and
        // matching it is exactly what made the previous version invisible.
        resolved.values.forEach { attributes ->
            assertNull("a kind sets a foreground colour", attributes?.foregroundColor)
            assertNotNull("a kind draws no effect, so nothing would be visible", attributes?.effectType)
        }
    }

    /**
     * PLAIN has to be marked as well.
     *
     * diffsitter reports nothing but PLAIN, so skipping it left the experimental viewer identical to
     * the normal one for that tool — reported as "no difference for diffsitter".
     */
    fun `test plain spans are marked too`() {
        val scheme = EditorColorsManager.getInstance().globalScheme

        assertNotNull(SpanColors.attributesFor(SpanKind.PLAIN, scheme))
    }

    /** The tool that reports only PLAIN must still get something drawn. */
    fun `test a tool reporting only plain spans still gets highlighters`() {
        val executable = tool("diffsitter") ?: return

        val request = SimpleDiffRequest(
            "test",
            listOf(content(left), content(right)),
            listOf("Left", "Right"),
        )
        val computer = SemanticDiffComputer(
            project,
            ToolInvocation.defaults(HandlerRegistry.byId("diffsitter")!!, executable),
            "A.java",
        )
        request.putUserData(DiffUserDataKeysEx.CUSTOM_DIFF_COMPUTER, computer)

        val viewer = RichSemanticDiffViewer(context(), request, computer)
        try {
            viewer.init()
            viewer.rediff(true)

            val kinds = computer.lastChanged()!!.spans.map { it.kind }.toSet()
            assertEquals("diffsitter should report only PLAIN", setOf(SpanKind.PLAIN), kinds)

            val installed = listOf(com.intellij.diff.util.Side.LEFT, com.intellij.diff.util.Side.RIGHT)
                .flatMap { viewer.getEditor(it).markupModel.allHighlighters.toList() }
                .count { it.getTextAttributes(null)?.effectColor != null }
            assertTrue("nothing drawn for a PLAIN-only tool", installed > 0)
        } finally {
            Disposer.dispose(viewer)
        }
    }

    fun `test the viewer installs highlighters for the tool's spans`() {
        val executable = difftastic() ?: return

        val request = SimpleDiffRequest(
            "test",
            listOf(content(left), content(right)),
            listOf("Left", "Right"),
        )
        val computer = SemanticDiffComputer(
            project,
            ToolInvocation.defaults(HandlerRegistry.byId("difftastic")!!, executable),
            "A.java",
        )
        request.putUserData(DiffUserDataKeysEx.CUSTOM_DIFF_COMPUTER, computer)

        val viewer = RichSemanticDiffViewer(context(), request, computer)
        try {
            viewer.init()
            viewer.rediff(true)

            assertNotNull("the tool produced no result to colour", computer.lastChanged())
            val kinds = computer.lastChanged()!!.spans.map { it.kind }.toSet()
            assertTrue("expected a colourable kind, got $kinds", kinds.any { it != SpanKind.PLAIN })

            val highlighters = viewer.getEditor(com.intellij.diff.util.Side.RIGHT)
                .markupModel.allHighlighters
                .count { it.getTextAttributes(null)?.effectColor != null }
            assertTrue("no foreground highlighters installed", highlighters > 0)
        } finally {
            Disposer.dispose(viewer)
        }
    }

    /**
     * A highlighter that exists but is painted over shows nothing. The diff viewer puts its own
     * highlighters on the same ranges, so ours has to be the topmost one carrying an effect.
     */
    fun `test the mark is the topmost effect on a changed token`() {
        val executable = difftastic() ?: return

        val request = SimpleDiffRequest("test", listOf(content(left), content(right)), listOf("L", "R"))
        val computer = SemanticDiffComputer(
            project,
            ToolInvocation.defaults(HandlerRegistry.byId("difftastic")!!, executable),
            "A.java",
        )
        request.putUserData(DiffUserDataKeysEx.CUSTOM_DIFF_COMPUTER, computer)

        val viewer = RichSemanticDiffViewer(context(), request, computer)
        try {
            viewer.init()
            viewer.rediff(true)

            val editor = viewer.getEditor(com.intellij.diff.util.Side.RIGHT)
            val offset = editor.document.text.indexOf("two") + 1
            val topEffect = editor.markupModel.allHighlighters
                .filter { it.startOffset <= offset && it.endOffset > offset }
                .filter { it.getTextAttributes(editor.colorsScheme)?.effectColor != null }
                .maxByOrNull { it.layer }

            assertNotNull("nothing with an effect covers the changed token", topEffect)
            assertNotNull(
                "the topmost effect has no colour, so nothing is drawn",
                topEffect!!.getTextAttributes(editor.colorsScheme)!!.effectColor,
            )
        } finally {
            Disposer.dispose(viewer)
        }
    }

    /** Toggling the setting has to re-mark a diff that is already open. */
    fun `test turning the setting off and on again re-marks an open diff`() {
        val executable = difftastic() ?: return

        val request = SimpleDiffRequest("test", listOf(content(left), content(right)), listOf("L", "R"))
        val computer = SemanticDiffComputer(
            project,
            ToolInvocation.defaults(HandlerRegistry.byId("difftastic")!!, executable),
            "A.java",
        )
        request.putUserData(DiffUserDataKeysEx.CUSTOM_DIFF_COMPUTER, computer)

        val viewer = RichSemanticDiffViewer(context(), request, computer)
        try {
            viewer.init()
            viewer.rediff(true)
            assertTrue("nothing marked while the setting is on", marks(viewer) > 0)

            SemanticDiffSettings.instance.useExperimentalViewer = false
            publishSettingsChanged()
            assertEquals("marks survived turning the setting off", 0, marks(viewer))

            SemanticDiffSettings.instance.useExperimentalViewer = true
            publishSettingsChanged()
            assertTrue("marks did not come back", marks(viewer) > 0)
        } finally {
            Disposer.dispose(viewer)
        }
    }

    private fun marks(viewer: RichSemanticDiffViewer): Int =
        listOf(com.intellij.diff.util.Side.LEFT, com.intellij.diff.util.Side.RIGHT)
            .flatMap { viewer.getEditor(it).markupModel.allHighlighters.toList() }
            .count { it.getTextAttributes(null)?.effectColor != null }

    private fun publishSettingsChanged() {
        com.intellij.openapi.application.ApplicationManager.getApplication().messageBus
            .syncPublisher(dev.ov7a.semdiff.ide.SemanticDiffSettingsListener.TOPIC)
            .settingsChanged()
        com.intellij.util.ui.UIUtil.dispatchAllInvocationEvents()
    }

    private fun content(text: String) =
        DiffContentFactory.getInstance().create(project, text, PlainTextFileType.INSTANCE)

    private fun context(): DiffContext = object : DiffContext() {
        private val data = UserDataHolderBase()

        override fun getProject(): Project = this@RichSemanticDiffViewerTest.project
        override fun isWindowFocused(): Boolean = true
        override fun isFocusedInWindow(): Boolean = true
        override fun requestFocusInWindow() = Unit
        override fun <T : Any?> getUserData(key: Key<T>): T? = data.getUserData(key)
        override fun <T : Any?> putUserData(key: Key<T>, value: T?) = data.putUserData(key, value)
    }.also { it.putUserData(DiffSettings.KEY, DiffSettings.getSettings()) }

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
