package dev.ov7a.semdiff.tools

import dev.ov7a.semdiff.model.DiffInputs
import dev.ov7a.semdiff.model.Granularity
import dev.ov7a.semdiff.model.ProcessResult
import dev.ov7a.semdiff.model.SemanticDiffResult
import dev.ov7a.semdiff.model.ToolVersion
import dev.ov7a.semdiff.model.VersionRange

/**
 * Everything the plugin needs to know about one CLI tool.
 *
 * Adding support for a tool, or for a version of a tool whose output shape changed, means writing
 * one implementation of this and one directory of golden files. Nothing outside this module moves.
 *
 * Note there is no `buildArguments`: argv is produced by substituting the file paths into the
 * argument pattern held in settings, which the handler only seeds via [defaultArgumentPattern].
 */
interface SemanticDiffToolHandler {

    /** Stable identifier, persisted in settings. Never change it for an existing tool. */
    val id: String

    val displayName: String

    /**
     * Executable names to look for when discovering installed tools.
     *
     * Tool knowledge, so it belongs here: difftastic's binary is `difft`, not `difftastic`.
     */
    val executableNames: List<String>

    /** Versions whose output this implementation is known to parse. */
    val supportedVersions: VersionRange

    val granularity: Granularity

    val supportsThreeWay: Boolean

    /** How the tool receives its inputs. */
    val inputMode: InputMode

    /** Seeds the user-editable setting. `%1` = left, `%2` = right, `%3` = base. */
    val defaultArgumentPattern: String

    /** Seeds the user-editable setting. */
    val defaultEnvironment: Map<String, String>

    /** Argv that makes the tool print its version. Not user-editable. */
    val versionArguments: List<String>

    /**
     * Reads the version out of `--version` output.
     *
     * Returning null means "this is not my tool" and is how auto-detection rules handlers out, so
     * implementations must match something specific to the tool, not just any version-shaped text.
     */
    fun parseVersion(result: ProcessResult): ToolVersion?

    /** Converts one invocation's output into the shared model. Must not throw. */
    fun parseOutput(result: ProcessResult, inputs: DiffInputs): SemanticDiffResult
}

enum class InputMode {
    /** Paths are passed as arguments. */
    FILE_PAIR,

    /** Content is piped in; not used by any handler yet. */
    STDIN,
}
