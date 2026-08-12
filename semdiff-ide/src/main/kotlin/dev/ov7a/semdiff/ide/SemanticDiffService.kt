package dev.ov7a.semdiff.ide

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import dev.ov7a.semdiff.model.DiffInputs
import dev.ov7a.semdiff.model.SemanticDiffResult
import dev.ov7a.semdiff.tools.HandlerRegistry
import dev.ov7a.semdiff.tools.SemanticDiffRunner
import dev.ov7a.semdiff.tools.SemanticDiffToolHandler
import dev.ov7a.semdiff.tools.ToolInvocation
import dev.ov7a.semdiff.tools.VersionDetection
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

/**
 * Turns the persisted settings into something runnable.
 *
 * Everything that needs to invoke a tool — the diff viewer and the settings Test button — goes
 * through here, so there is one place where a half-configured tool is turned into a clear absence
 * rather than a crash.
 */
@Service(Service.Level.APP)
class SemanticDiffService {

    private val runner = SemanticDiffRunner(IdeCommandRunner())

    fun activeInvocation(): ToolInvocation? = invocationFor(SemanticDiffSettings.instance.activeTool())

    fun invocationFor(entry: ToolEntry?): ToolInvocation? {
        if (entry == null) return null
        val handler = HandlerRegistry.byId(entry.handlerId.orEmpty()) ?: return null
        val executable = entry.executablePath?.takeIf { it.isNotBlank() }?.let(Path::of) ?: return null

        return ToolInvocation(
            handler = handler,
            executable = executable,
            argumentPattern = entry.arguments?.takeIf { it.isNotBlank() } ?: handler.defaultArgumentPattern,
            environment = entry.environment.ifEmpty { handler.defaultEnvironment },
            timeout = SemanticDiffSettings.instance.timeoutSeconds.coerceAtLeast(1).seconds,
        )
    }

    fun diff(invocation: ToolInvocation, inputs: DiffInputs): SemanticDiffResult = runner.diff(invocation, inputs)

    fun detectVersion(handler: SemanticDiffToolHandler, executable: Path): VersionDetection =
        runner.detectVersion(handler, executable)

    /** First handler that recognises the binary, or null when none does. */
    fun detectHandler(executable: Path): Pair<SemanticDiffToolHandler, VersionDetection>? =
        HandlerRegistry.all
            .asSequence()
            .map { it to runner.detectVersion(it, executable) }
            .firstOrNull { (_, detection) ->
                detection is VersionDetection.Supported || detection is VersionDetection.OutOfRange
            }

    companion object {
        @JvmStatic
        val instance: SemanticDiffService
            get() = service()
    }
}
