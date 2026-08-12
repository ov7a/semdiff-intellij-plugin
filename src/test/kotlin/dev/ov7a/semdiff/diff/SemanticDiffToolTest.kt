package dev.ov7a.semdiff.diff

import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffContext
import com.intellij.diff.FrameDiffTool
import com.intellij.diff.contents.DiffContent
import com.intellij.diff.impl.DiffSettingsHolder.DiffSettings
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.diff.tools.fragmented.UnifiedDiffTool
import com.intellij.diff.tools.simple.SimpleDiffTool
import com.intellij.diff.util.DiffUserDataKeysEx
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.ov7a.semdiff.ide.SemanticDiffSettings
import dev.ov7a.semdiff.ide.ToolEntry

class SemanticDiffToolTest : BasePlatformTestCase() {

    private val tool = SemanticDiffTool()
    private lateinit var settings: SemanticDiffSettings

    override fun setUp() {
        super.setUp()
        settings = SemanticDiffSettings.instance
        settings.useExperimentalViewer = false
        settings.tools = mutableListOf(
            ToolEntry().apply {
                name = "difft"
                // A real handler id and a real executable; canShow never runs the tool.
                handlerId = "difftastic"
                executablePath = "/bin/echo"
            },
        )
        settings.activeToolName = "difft"
    }

    override fun tearDown() {
        try {
            settings.tools = mutableListOf()
            settings.activeToolName = ""
            settings.useExperimentalViewer = false
        } finally {
            super.tearDown()
        }
    }

    fun `test shows a two-side text diff`() {
        assertTrue(tool.canShow(context(), twoSideRequest()))
    }

    /**
     * An empty tool table is the only "off" switch, so it has to be a complete one: with no tool the
     * plugin must not appear in the chooser at all.
     */
    fun `test hidden when no tool is configured`() {
        settings.tools = mutableListOf()

        assertFalse(tool.canShow(context(), twoSideRequest()))
    }

    fun `test hidden when the configured tool has no parser`() {
        settings.tools.single().handlerId = "no-such-handler"

        assertFalse(tool.canShow(context(), twoSideRequest()))
    }

    /** Three-way has no custom-fragment hook in the platform, so it stays with the built-in viewer. */
    fun `test hidden for three-way requests`() {
        val request = SimpleDiffRequest(
            "test",
            listOf(content("a"), content("base"), content("b")),
            listOf("Left", "Base", "Right"),
        )

        assertFalse(tool.canShow(context(), request))
    }

    fun `test hidden for one-side requests`() {
        val request = SimpleDiffRequest("test", listOf(content("a")), listOf("Only"))

        assertFalse(tool.canShow(context(), request))
    }

    fun `test creating the viewer installs the custom diff computer`() {
        val request = twoSideRequest()

        withViewer(request) {
            assertNotNull(request.getUserData(DiffUserDataKeysEx.CUSTOM_DIFF_COMPUTER))
            assertNotNull(request.getUserData(SemanticDiffComputer.KEY))
        }
    }

    /**
     * One viewer either way. It draws the experimental marks only while that setting is on, which is
     * what lets toggling the setting affect a diff that is already open.
     */
    fun `test the same viewer is used whether the experimental setting is on or off`() {
        withViewer { viewer -> assertTrue(viewer is RichSemanticDiffViewer) }

        settings.useExperimentalViewer = true

        withViewer { viewer -> assertTrue(viewer is RichSemanticDiffViewer) }
    }

    /**
     * A viewer must be initialized before it can be disposed: `init()` is what installs the editor
     * listeners that `onDispose` removes.
     */
    private fun withViewer(
        request: SimpleDiffRequest = twoSideRequest(),
        assertions: (FrameDiffTool.DiffViewer) -> Unit,
    ) {
        val viewer = tool.createComponent(context(), request)
        try {
            viewer.init()
            assertions(viewer)
        } finally {
            Disposer.dispose(viewer)
        }
    }

    /**
     * Regression: `DiffRequestProcessor.switchToDiffTool` re-applies the *same* `DiffRequest`, so a
     * computer left on it after our viewer dies would be picked up by the built-in viewer too —
     * making "switch to Side-by-side" show a semantic diff and look like the toggle did nothing.
     */
    fun `test disposing the viewer takes the computer off the request`() {
        val request = twoSideRequest()

        withViewer(request) {
            assertNotNull(request.getUserData(DiffUserDataKeysEx.CUSTOM_DIFF_COMPUTER))
        }

        assertNull(request.getUserData(DiffUserDataKeysEx.CUSTOM_DIFF_COMPUTER))
        assertNull(request.getUserData(SemanticDiffComputer.KEY))
    }

    fun `test the experimental viewer also detaches on dispose`() {
        settings.useExperimentalViewer = true
        val request = twoSideRequest()

        withViewer(request) { assertTrue(it is RichSemanticDiffViewer) }

        assertNull(request.getUserData(DiffUserDataKeysEx.CUSTOM_DIFF_COMPUTER))
    }

    /**
     * The chooser draws tools by icon, so ours has to differ from the built-in ones — the default
     * `FrameDiffTool.getIcon` hands every Default-type tool the same side-by-side glyph.
     *
     * Compares the icons' resource identity rather than rendered pixels: icons do not paint in a
     * headless test, so every glyph would compare equal and the assertion would prove nothing.
     */
    fun `test the icon differs from the built-in viewers`() {
        val ours = tool.icon.toString()

        assertFalse("blank icon identity", ours.isBlank())
        assertFalse("same icon as Side-by-side", ours == SimpleDiffTool.INSTANCE.icon.toString())
        assertFalse("same icon as Unified", ours == UnifiedDiffTool.INSTANCE.icon.toString())
    }

    private fun twoSideRequest() = SimpleDiffRequest(
        "test",
        listOf(content("val x = 1\n"), content("val y = 1\n")),
        listOf("Left", "Right"),
    )

    private fun content(text: String): DiffContent =
        DiffContentFactory.getInstance().create(project, text, PlainTextFileType.INSTANCE)

    private fun context(): DiffContext = object : DiffContext() {
        private val data = UserDataHolderBase()

        override fun getProject(): Project = this@SemanticDiffToolTest.project
        override fun isWindowFocused(): Boolean = true
        override fun isFocusedInWindow(): Boolean = true
        override fun requestFocusInWindow() = Unit
        override fun <T : Any?> getUserData(key: com.intellij.openapi.util.Key<T>): T? = data.getUserData(key)
        override fun <T : Any?> putUserData(key: com.intellij.openapi.util.Key<T>, value: T?) =
            data.putUserData(key, value)
    }.also { it.putUserData(DiffSettings.KEY, DiffSettings.getSettings()) }
}
