package dev.ov7a.semdiff.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.LabelPosition
import com.intellij.ui.dsl.builder.bindIntText
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import dev.ov7a.semdiff.ide.SEMANTIC_DIFF_CONFIGURABLE_NAME
import dev.ov7a.semdiff.ide.SemanticDiffSettings
import dev.ov7a.semdiff.ide.SemanticDiffSettingsListener

/** Tools → Diff & Merge → Semantic Diff. */
class SemanticDiffConfigurable : BoundSearchableConfigurable(
    SEMANTIC_DIFF_CONFIGURABLE_NAME,
    "settings.semdiff",
    "semdiff.settings",
) {

    /** Lets an already-open diff pick the change up, instead of waiting to be reopened. */
    override fun apply() {
        super.apply()
        ApplicationManager.getApplication().messageBus
            .syncPublisher(SemanticDiffSettingsListener.TOPIC)
            .settingsChanged()
    }

    override fun createPanel(): DialogPanel {
        val settings = SemanticDiffSettings.instance

        return panel {
            val tools = ToolTablePanel()
            row {
                cell(tools.component)
                    .label("Tools:", LabelPosition.TOP)
                    .align(AlignX.FILL)
                    .onIsModified { tools.isModified(settings) }
                    .onApply { tools.apply(settings) }
                    .onReset { tools.reset(settings) }
            }

            row {
                checkBox("Use the experimental semantic viewer")
                    .bindSelected(settings::useExperimentalViewer)
                    .comment("Underlines changed tokens and boxes changed declarations.")
            }

            row("Timeout (seconds):") {
                intTextField(range = 1..600)
                    .bindIntText(settings::timeoutSeconds)
                    .comment("How long to wait for the tool before falling back to the built-in diff.")
            }
        }
    }
}
