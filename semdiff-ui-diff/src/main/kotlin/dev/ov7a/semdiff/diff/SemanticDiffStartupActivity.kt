package dev.ov7a.semdiff.diff

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import dev.ov7a.semdiff.ide.SemanticDiffSettings
import dev.ov7a.semdiff.ide.ToolDiscovery

/**
 * Finds installed tools, then makes semantic diff the default viewer if one was found.
 *
 * Both steps in one activity because the order matters: the tool order is only worth changing once
 * there is something to run. Runs off the EDT after the project opens, because discovery executes
 * `--version` on a handful of binaries.
 */
class SemanticDiffStartupActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        ToolDiscovery.discoverOnce()
        if (SemanticDiffSettings.instance.isUsable()) {
            SemanticDiffToolOrder.makeDefault()
        }
    }
}
