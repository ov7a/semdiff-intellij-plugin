package dev.ov7a.semdiff.ide

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.util.ExecUtil
import com.intellij.openapi.progress.ProgressManager
import dev.ov7a.semdiff.model.CommandRunner
import dev.ov7a.semdiff.model.ProcessResult
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import kotlin.time.Duration

/**
 * Runs tools through the platform's process plumbing, so PATH resolution, environment handling and
 * process teardown behave the same as everywhere else in the IDE.
 */
class IdeCommandRunner : CommandRunner {

    override fun run(
        executable: Path,
        arguments: List<String>,
        environment: Map<String, String>,
        timeout: Duration,
    ): ProcessResult {
        ProgressManager.checkCanceled()

        val commandLine = GeneralCommandLine(listOf(executable.toString()) + arguments)
            .withCharset(StandardCharsets.UTF_8)
            .withEnvironment(environment)

        val output = ExecUtil.execAndGetOutput(commandLine, timeout.inWholeMilliseconds.toInt())

        if (output.isTimeout) {
            throw dev.ov7a.semdiff.model.CommandFailedException("$executable timed out after $timeout")
        }
        return ProcessResult(output.exitCode, output.stdout, output.stderr)
    }
}
