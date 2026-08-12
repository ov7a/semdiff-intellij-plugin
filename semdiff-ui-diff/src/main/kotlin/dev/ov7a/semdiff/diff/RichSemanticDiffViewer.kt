package dev.ov7a.semdiff.diff

import com.intellij.diff.DiffContext
import com.intellij.diff.requests.ContentDiffRequest
import com.intellij.diff.tools.simple.SimpleDiffChange
import com.intellij.diff.tools.simple.SimpleDiffViewer
import com.intellij.diff.util.Side
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.application.ApplicationManager
import dev.ov7a.semdiff.ide.SemanticDiffSettings
import dev.ov7a.semdiff.ide.SemanticDiffSettingsListener
import dev.ov7a.semdiff.model.ChangedRegion
import dev.ov7a.semdiff.model.ChangedSpan
import dev.ov7a.semdiff.model.RegionChange
import dev.ov7a.semdiff.model.Side as ModelSide

/**
 * The stock viewer, plus the experimental marks when that setting is on.
 *
 * Deliberately a subclass rather than a new viewer: gutter, folding, navigation, aligned mode and
 * every diff action are inherited untouched, and the added behaviour is a set of extra highlighters
 * that can be added and removed without affecting anything else.
 *
 * This is the only viewer the tool creates, whether the experimental setting is on or off. With it
 * off nothing is drawn and the viewer behaves exactly like `SimpleDiffViewer`; toggling the setting
 * re-marks an already-open diff, instead of the user having to close and reopen it.
 */
class RichSemanticDiffViewer(
    context: DiffContext,
    request: ContentDiffRequest,
    private val computer: SemanticDiffComputer,
) : SimpleDiffViewer(context, request) {

    private val kindHighlighters = mutableListOf<RangeHighlighter>()

    private companion object {
        val LOG = com.intellij.openapi.diagnostic.Logger.getInstance(RichSemanticDiffViewer::class.java)
    }

    init {
        ApplicationManager.getApplication().messageBus.connect(this)
            .subscribe(
                SemanticDiffSettingsListener.TOPIC,
                object : SemanticDiffSettingsListener {
                    override fun settingsChanged() {
                        ApplicationManager.getApplication().invokeLater { installKindHighlighters() }
                    }
                },
            )
    }

    override fun apply(changes: MutableList<out SimpleDiffChange>?, isContentsEqual: Boolean): Runnable {
        val applyBase = super.apply(changes, isContentsEqual)
        return Runnable {
            applyBase.run()
            installKindHighlighters()
        }
    }

    override fun clearDiffPresentation() {
        removeKindHighlighters()
        super.clearDiffPresentation()
    }

    override fun onDispose() {
        removeKindHighlighters()
        // Same reason as StockSemanticDiffViewer: the request outlives this viewer.
        clearSemanticComputer(request)
        super.onDispose()
    }

    private fun installKindHighlighters() {
        removeKindHighlighters()
        val on = SemanticDiffSettings.instance.useExperimentalViewer
        val result = computer.lastChanged()

        if (!on || result == null) {
            LOG.info(
                "semdiff: no marks drawn (experimental=$on, tool=${computer.toolId}, " +
                    "result=${if (result == null) "none" else "present"})",
            )
            return
        }

        result.spans.forEach { span ->
            highlight(editorFor(span.side), span)
        }
        // Every reported entity, not only moved ones: sem reports no character spans, so boxes are
        // the only thing the experimental viewer can add for it.
        result.regions.forEach { region -> highlightRegion(editorFor(region.side), region) }

        LOG.info(
            "semdiff: drew ${kindHighlighters.size} marks for ${computer.toolId} " +
                "(${result.spans.size} spans, ${result.regions.size} regions)",
        )
    }

    private fun editorFor(side: ModelSide): EditorEx =
        if (side == ModelSide.LEFT) getEditor(Side.LEFT) else getEditor(Side.RIGHT)

    /**
     * Boxes a reported entity and names it.
     *
     * For a move the box matters most: the fragments can only show a move as a deletion on one side
     * and an insertion on the other, so the box and its tooltip are what tell the reader those two
     * halves are the same code.
     */
    private fun highlightRegion(editor: EditorEx, region: ChangedRegion) {
        val document = editor.document
        if (region.startLine >= document.lineCount) return

        val lastLine = (region.endLine - 1).coerceAtMost(document.lineCount - 1)
        if (lastLine < region.startLine) return

        val attributes = SpanColors.regionAttributes(region.change, editor.colorsScheme) ?: return

        val highlighter = editor.markupModel.addRangeHighlighter(
            document.getLineStartOffset(region.startLine),
            document.getLineEndOffset(lastLine),
            HighlighterLayer.SELECTION - 2,
            attributes,
            HighlighterTargetArea.EXACT_RANGE,
        )
        highlighter.errorStripeTooltip = regionTooltip(region)
        kindHighlighters += highlighter
    }

    private fun regionTooltip(region: ChangedRegion): String {
        val what = listOfNotNull(region.entityKind, region.entityName)
            .joinToString(" ")
            .ifBlank { "This block" }

        if (region.change != RegionChange.MOVED) {
            return "$what ${region.change.name.lowercase()}"
        }

        val counterpart = region.counterpartStartLine?.let { it + 1 } ?: return "$what moved"
        return when (region.side) {
            ModelSide.LEFT -> "$what moved to line $counterpart"
            ModelSide.RIGHT -> "$what moved from line $counterpart"
        }
    }

    private fun highlight(editor: EditorEx, span: ChangedSpan) {
        val document = editor.document
        if (span.line >= document.lineCount) return

        val lineStart = document.getLineStartOffset(span.line)
        val start = lineStart + span.startChar
        val end = lineStart + span.endChar
        if (end > document.getLineEndOffset(span.line) || start >= end) return

        val attributes = SpanColors.attributesFor(span.kind, editor.colorsScheme) ?: return

        kindHighlighters += editor.markupModel.addRangeHighlighter(
            start,
            end,
            // Above the diff's own inner-fragment layer so the foreground wins, while the diff
            // background underneath still shows what changed.
            HighlighterLayer.SELECTION - 1,
            attributes,
            HighlighterTargetArea.EXACT_RANGE,
        )
    }

    private fun removeKindHighlighters() {
        kindHighlighters.forEach { it.dispose() }
        kindHighlighters.clear()
    }
}

/**
 * Detaches the semantic computer from a request.
 *
 * Safe on dispose: `doApplyRequest` destroys the old viewer before creating the new one, so a tool
 * switch always sees a clean request. Without this, switching to Side-by-side showed a semantic diff
 * anyway, because the request is reused and `DiffUtil.createTextDiffProvider` would still find the
 * computer on it.
 */
internal fun clearSemanticComputer(request: com.intellij.diff.requests.DiffRequest) {
    request.putUserData(com.intellij.diff.util.DiffUserDataKeysEx.CUSTOM_DIFF_COMPUTER, null)
    request.putUserData(SemanticDiffComputer.KEY, null)
}
