package dev.ov7a.semdiff.diff

import com.intellij.diff.impl.DiffSettingsHolder.DiffSettings
import com.intellij.diff.util.DiffPlaces
import com.intellij.openapi.diagnostic.Logger

/**
 * Makes semantic diff the default two-side viewer by putting it first in the platform's own tool
 * order.
 *
 * This replaces a `DiffToolSubstitutor`, which could not work: `switchToDiffTool` begins with
 * `if (isSameToolOrSubstitutor(chosen, active)) return`, and a substitutor mapping
 * `SimpleDiffTool -> SemanticDiffTool` makes that condition true whenever the user picks
 * Side-by-side while Semantic is showing. The click was silently a no-op and the built-in viewer was
 * unreachable.
 *
 * The tool order is the mechanism the chooser itself uses: `switchToDiffTool` calls `moveToolToTop`
 * and persists the result, so a user who switches away stays switched away, per diff place.
 */
object SemanticDiffToolOrder {

    private val LOG = Logger.getInstance(SemanticDiffToolOrder::class.java)

    /** Every place a two-side text diff can appear; the order is stored per place. */
    private val PLACES = listOf(
        DiffPlaces.DEFAULT,
        DiffPlaces.CHANGES_VIEW,
        DiffPlaces.VCS_LOG_VIEW,
        DiffPlaces.VCS_FILE_HISTORY_VIEW,
        DiffPlaces.SHELVE_VIEW,
        DiffPlaces.COMMIT_DIALOG,
        DiffPlaces.BLANK,
    )

    private val TOOL_CLASS_NAME: String = SemanticDiffTool::class.java.canonicalName

    /**
     * Puts the semantic tool first wherever it is not mentioned yet.
     *
     * A place that already lists it is left alone, so someone who deliberately moved it down keeps
     * that choice.
     */
    fun makeDefault() {
        PLACES.forEach { place ->
            val settings = DiffSettings.getSettings(place)
            val order = settings.diffToolsOrder
            if (order.contains(TOOL_CLASS_NAME)) return@forEach

            settings.diffToolsOrder = listOf(TOOL_CLASS_NAME) + order
            LOG.info("Semantic diff made the default viewer for diff place '$place'")
        }
    }

    /** Test support: whether the tool is currently first for [place]. */
    fun isDefaultFor(place: String): Boolean =
        DiffSettings.getSettings(place).diffToolsOrder.firstOrNull() == TOOL_CLASS_NAME
}
