package dev.ov7a.semdiff.ide

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.util.xmlb.annotations.OptionTag

/**
 * One configured CLI tool.
 *
 * [handlerId] is resolved by auto-detection, or pinned by the user when detection cannot identify
 * the binary (a wrapper script, an unreleased build).
 */
class ToolEntry : BaseState() {
    var name: String? by string("")
    var handlerId: String? by string("")
    var executablePath: String? by string("")

    /** `%1` = left, `%2` = right, `%3` = base. Seeded from the handler, then owned by the user. */
    var arguments: String? by string("")
    var environment: MutableMap<String, String> by map()
    var detectedVersion: String? by string("")

    /** True once the user overrides detection; detection then stops changing [handlerId]. */
    var handlerPinnedByUser: Boolean by property(false)
}

@State(
    name = "SemanticDiffSettings",
    storages = [Storage("semdiff.xml")],
    category = SettingsCategory.TOOLS,
)
class SemanticDiffSettings : BaseState(), PersistentStateComponent<SemanticDiffSettings> {

    override fun getState(): SemanticDiffSettings = this

    override fun loadState(state: SemanticDiffSettings) = copyFrom(state)

    @get:OptionTag("TOOLS")
    var tools: MutableList<ToolEntry> by list()

    @get:OptionTag("ACTIVE_TOOL")
    var activeToolName: String? by string("")

    @get:OptionTag("TIMEOUT_SECONDS")
    var timeoutSeconds: Int by property(10)

    /** Adds syntax-kind colouring of changed spans on top of the stock viewer. Experimental. */
    @get:OptionTag("EXPERIMENTAL_VIEWER")
    var useExperimentalViewer: Boolean by property(false)

    /** True once tool discovery has run, so a user who removed every tool does not get them back. */
    @get:OptionTag("TOOLS_DISCOVERED")
    var toolsDiscovered: Boolean by property(false)

    fun activeTool(): ToolEntry? {
        val configured = tools.filter { !it.executablePath.isNullOrBlank() }
        return configured.firstOrNull { it.name == activeToolName } ?: configured.firstOrNull()
    }

    /**
     * True when a semantic diff could actually be produced right now.
     *
     * There is deliberately no separate "enable semantic diff" flag. It did exactly what disabling
     * the plugin does — hide the tool from the chooser and stop it being the default — and every one
     * of this plugin's extension points is dynamic, so disabling the plugin needs no restart. Having
     * a configured tool *is* the switch: an empty tool table turns the feature off, and the diff
     * window's viewer chooser turns it off one place at a time.
     */
    fun isUsable(): Boolean = activeTool() != null

    companion object {
        @JvmStatic
        val instance: SemanticDiffSettings
            get() = service()
    }
}
