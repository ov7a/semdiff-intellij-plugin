package dev.ov7a.semdiff.tools.testing

import dev.ov7a.semdiff.model.CommandRunner
import dev.ov7a.semdiff.model.ProcessResult
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.time.Duration

/**
 * Runs real processes. Tests use this deliberately: the point of the suite is to pin what the
 * actual binaries emit, so a mock here would test nothing.
 */
class ProcessCommandRunner : CommandRunner {

    override fun run(
        executable: Path,
        arguments: List<String>,
        environment: Map<String, String>,
        timeout: Duration,
    ): ProcessResult {
        val process = ProcessBuilder(listOf(executable.toString()) + arguments)
            .apply { environment().putAll(environment) }
            .start()

        val stdout = process.inputStream.bufferedReader().readText()
        val stderr = process.errorStream.bufferedReader().readText()

        if (!process.waitFor(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)) {
            process.destroyForcibly()
            error("$executable timed out after $timeout")
        }

        return ProcessResult(process.exitValue(), stdout, stderr)
    }
}
