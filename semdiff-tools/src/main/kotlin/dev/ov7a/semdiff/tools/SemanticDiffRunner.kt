package dev.ov7a.semdiff.tools

import dev.ov7a.semdiff.model.CommandRunner
import dev.ov7a.semdiff.model.DiffInputs
import dev.ov7a.semdiff.model.ProcessResult
import dev.ov7a.semdiff.model.SemanticDiffResult
import dev.ov7a.semdiff.model.ToolVersion
import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** A configured tool: which handler parses it, where the binary is, and how to invoke it. */
data class ToolInvocation(
    val handler: SemanticDiffToolHandler,
    val executable: Path,
    val argumentPattern: String,
    val environment: Map<String, String>,
    val timeout: Duration = 10.seconds,
) {
    companion object {
        /** An invocation using the handler's own defaults, for detection and tests. */
        fun defaults(handler: SemanticDiffToolHandler, executable: Path): ToolInvocation =
            ToolInvocation(
                handler = handler,
                executable = executable,
                argumentPattern = handler.defaultArgumentPattern,
                environment = handler.defaultEnvironment,
            )
    }
}

/**
 * Runs a configured tool and parses its output.
 *
 * Never throws for tool-side problems: a missing binary, a crash, or a timeout all come back as
 * [SemanticDiffResult.Unsupported] so callers have exactly one failure path to handle.
 */
class SemanticDiffRunner(private val commandRunner: CommandRunner) {

    fun diff(invocation: ToolInvocation, inputs: DiffInputs): SemanticDiffResult {
        if (inputs.isThreeWay && !invocation.handler.supportsThreeWay) {
            return SemanticDiffResult.Unsupported("${invocation.handler.displayName} does not support three-way diff")
        }

        val result = runCatching {
            commandRunner.run(
                executable = invocation.executable,
                arguments = ArgumentPattern.expand(invocation.argumentPattern, inputs),
                environment = invocation.environment,
                timeout = invocation.timeout,
            )
        }.getOrElse { failure ->
            return SemanticDiffResult.Unsupported("could not run ${invocation.executable}: ${failure.message}")
        }

        return runCatching { invocation.handler.parseOutput(result, inputs) }
            .getOrElse { failure ->
                SemanticDiffResult.Unsupported(
                    "${invocation.handler.displayName} output could not be parsed: ${failure.message}",
                )
            }
    }

    /** Runs `--version` and asks [handler] whether the binary is its tool. */
    fun detectVersion(
        handler: SemanticDiffToolHandler,
        executable: Path,
        timeout: Duration = 5.seconds,
    ): VersionDetection {
        val result = runCatching {
            commandRunner.run(executable, handler.versionArguments, handler.defaultEnvironment, timeout)
        }.getOrElse { failure ->
            return VersionDetection.NotRunnable(failure.message ?: failure.toString())
        }

        val version = handler.parseVersion(result)
            ?: return VersionDetection.NotThisTool(result)

        return if (version in handler.supportedVersions) {
            VersionDetection.Supported(version)
        } else {
            VersionDetection.OutOfRange(version, handler.supportedVersions)
        }
    }
}

sealed interface VersionDetection {
    data class Supported(val version: ToolVersion) : VersionDetection

    /** Recognised, but this handler is not known to parse that version's output. */
    data class OutOfRange(
        val version: ToolVersion,
        val supported: dev.ov7a.semdiff.model.VersionRange,
    ) : VersionDetection

    /** The binary ran but is not this handler's tool. */
    data class NotThisTool(val result: ProcessResult) : VersionDetection

    data class NotRunnable(val reason: String) : VersionDetection
}
