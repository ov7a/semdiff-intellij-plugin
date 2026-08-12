package dev.ov7a.semdiff.diff

import com.intellij.diff.DiffContext
import com.intellij.diff.FrameDiffTool
import com.intellij.diff.contents.DocumentContent
import com.intellij.diff.requests.ContentDiffRequest
import com.intellij.diff.requests.DiffRequest
import com.intellij.diff.tools.simple.SimpleDiffViewer
import com.intellij.diff.util.DiffUserDataKeysEx
import com.intellij.icons.AllIcons
import dev.ov7a.semdiff.ide.SemanticDiffService
import dev.ov7a.semdiff.ide.SemanticDiffSettings
import javax.swing.Icon

/**
 * Registers semantic diff as a viewer choice.
 *
 * Being a `FrameDiffTool` is also what produces the toolbar toggle: `DiffRequestProcessor` puts a
 * `DiffToolChooser` in its right toolbar listing every fitted tool.
 */
class SemanticDiffTool : FrameDiffTool {

    override fun getName(): String = "Semantic"

    /**
     * Must differ from the two built-in glyphs, or the chooser shows three identical buttons: the
     * default implementation returns `AllIcons.Diff.SideBySide` for any tool whose type is Default,
     * which is what `SimpleDiffTool` also gets.
     */
    override fun getIcon(): Icon = AllIcons.Actions.GroupByClass

    override fun canShow(context: DiffContext, request: DiffRequest): Boolean {
        if (!SemanticDiffSettings.instance.isUsable()) return false
        if (request !is ContentDiffRequest) return false

        val contents = request.contents
        // Three-way has no custom-fragment hook in the platform, so it is left to the built-in
        // viewer regardless of what the configured tool supports.
        if (contents.size != TWO_SIDED) return false
        if (contents.any { it !is DocumentContent }) return false

        return SimpleDiffViewer.canShowRequest(context, request) &&
            SemanticDiffService.instance.activeInvocation() != null
    }

    override fun createComponent(context: DiffContext, request: DiffRequest): FrameDiffTool.DiffViewer {
        val contentRequest = request as ContentDiffRequest
        val invocation = requireNotNull(SemanticDiffService.instance.activeInvocation()) {
            "createComponent called without a configured tool; canShow should have refused"
        }

        val computer = SemanticDiffComputer(context.project, invocation, fileNameFor(contentRequest))
        request.putUserData(DiffUserDataKeysEx.CUSTOM_DIFF_COMPUTER, computer)
        request.putUserData(SemanticDiffComputer.KEY, computer)

        // Always the same viewer. It draws the experimental marks only while that setting is on, and
        // reacts to the setting changing, so the choice does not have to be made here — which is what
        // used to force the user to close and reopen a diff after ticking the box.
        return RichSemanticDiffViewer(context, contentRequest, computer)
    }

    /**
     * A name whose extension the tool can use for language detection. Contents are usually
     * in-memory documents, so the request title is the only reliable source.
     */
    private fun fileNameFor(request: ContentDiffRequest): String {
        val fromContent = request.contents
            .filterIsInstance<DocumentContent>()
            .firstNotNullOfOrNull { it.highlightFile?.name }
        return fromContent ?: request.title?.substringAfterLast('/')?.substringBefore(' ') ?: "content.txt"
    }

    private companion object {
        const val TWO_SIDED = 2
    }
}
