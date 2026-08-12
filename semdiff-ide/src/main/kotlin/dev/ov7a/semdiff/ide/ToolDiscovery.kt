package dev.ov7a.semdiff.ide

import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.openapi.diagnostic.Logger
import dev.ov7a.semdiff.tools.HandlerRegistry
import dev.ov7a.semdiff.tools.SemanticDiffToolHandler
import dev.ov7a.semdiff.tools.VersionDetection
import java.io.File
import java.nio.file.Path

/**
 * Finds already-installed tools so the plugin does something useful without being configured first.
 *
 * Runs once. If the user later deletes every tool, they stay deleted — discovery does not fight the
 * user, it only fills an empty configuration.
 */
object ToolDiscovery {

    private val LOG = Logger.getInstance(ToolDiscovery::class.java)

    /**
     * Homebrew and Cargo are not on the PATH of a GUI-launched IDE on macOS, which is exactly where
     * these tools usually live.
     */
    private val EXTRA_DIRECTORIES = listOf(
        "/opt/homebrew/bin",
        "/usr/local/bin",
        "/usr/bin",
        System.getProperty("user.home") + "/.cargo/bin",
        System.getProperty("user.home") + "/.local/bin",
    )

    fun discoverOnce() {
        val settings = SemanticDiffSettings.instance
        if (settings.toolsDiscovered) return
        settings.toolsDiscovered = true

        val found = HandlerRegistry.all.mapNotNull(::discover)
        if (found.isEmpty()) {
            LOG.info("No semantic diff tool found on PATH")
            return
        }

        val existingNames = settings.tools.mapNotNull { it.name }.toSet()
        found.filterNot { it.name in existingNames }.forEach { settings.tools.add(it) }
        if (settings.activeToolName.isNullOrBlank()) {
            settings.activeToolName = found.first().name
        }
        LOG.info("Discovered semantic diff tools: " + found.joinToString { "${it.name} ${it.detectedVersion}" })
    }

    private fun discover(handler: SemanticDiffToolHandler): ToolEntry? {
        val executable = handler.executableNames.firstNotNullOfOrNull(::locate) ?: return null

        val detection = SemanticDiffService.instance.detectVersion(handler, executable)
        val version = when (detection) {
            is VersionDetection.Supported -> detection.version
            // A version outside the tested range is still worth offering; the settings page says so.
            is VersionDetection.OutOfRange -> detection.version
            else -> return null
        }

        return ToolEntry().apply {
            name = handler.displayName
            handlerId = handler.id
            executablePath = executable.toString()
            arguments = handler.defaultArgumentPattern
            environment = handler.defaultEnvironment.toMutableMap()
            detectedVersion = version.toString()
        }
    }

    private fun locate(name: String): Path? {
        PathEnvironmentVariableUtil.findInPath(name)?.let { return it.toPath() }

        return EXTRA_DIRECTORIES
            .map { File(it, name) }
            .firstOrNull { it.isFile && it.canExecute() }
            ?.toPath()
    }
}
