package dev.ov7a.semdiff.model

import java.nio.file.Path
import kotlin.time.Duration

/**
 * One side of a diff as the CLI sees it.
 *
 * [text] is the original content rather than something re-read from [path], because handlers need
 * it to translate tool-reported offsets (difftastic reports UTF-8 byte offsets) into char offsets.
 */
data class SideInput(
    val path: Path,
    val text: String,
    val displayName: String,
)

data class DiffInputs(
    val left: SideInput,
    val right: SideInput,
    val base: SideInput? = null,
) {
    val isThreeWay: Boolean get() = base != null
}

data class ProcessResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
)

/**
 * Runs an external process. The IntelliJ layer backs this with `GeneralCommandLine`; tests use a
 * plain [java.lang.ProcessBuilder] implementation or a canned one.
 */
fun interface CommandRunner {
    fun run(
        executable: Path,
        arguments: List<String>,
        environment: Map<String, String>,
        timeout: Duration,
    ): ProcessResult
}

class CommandFailedException(message: String, cause: Throwable? = null) : Exception(message, cause)
