package dev.ov7a.semdiff.diff

import dev.ov7a.semdiff.model.DiffInputs
import dev.ov7a.semdiff.model.Granularity
import dev.ov7a.semdiff.model.ProcessResult
import dev.ov7a.semdiff.model.SemanticDiffResult
import dev.ov7a.semdiff.model.ToolVersion
import dev.ov7a.semdiff.model.VersionRange
import dev.ov7a.semdiff.tools.InputMode
import dev.ov7a.semdiff.tools.SemanticDiffToolHandler
import dev.ov7a.semdiff.tools.ToolInvocation
import java.nio.file.Path

/**
 * A handler that returns a canned model whatever the process printed.
 *
 * The executable is real (`/bin/echo`), so these tests still exercise temp-file materialization and
 * process execution; only the parsing step is pinned, which is what the golden suite already covers.
 */
class FakeHandler(
    private val result: SemanticDiffResult,
    override val supportsThreeWay: Boolean = false,
    override val granularity: Granularity = Granularity.INTRA_LINE,
) : SemanticDiffToolHandler {

    var invocations: Int = 0
        private set

    override val id: String = "fake"
    override val displayName: String = "Fake"
    override val executableNames: List<String> = listOf("fake")
    override val supportedVersions: VersionRange = VersionRange.of("0.0.0", "999.0.0")
    override val inputMode: InputMode = InputMode.FILE_PAIR
    override val defaultArgumentPattern: String = "%1 %2"
    override val defaultEnvironment: Map<String, String> = emptyMap()
    override val versionArguments: List<String> = listOf("--version")

    override fun parseVersion(result: ProcessResult): ToolVersion? = ToolVersion(1, 0, 0)

    override fun parseOutput(result: ProcessResult, inputs: DiffInputs): SemanticDiffResult {
        invocations++
        return this.result
    }

    fun invocation(): ToolInvocation = ToolInvocation(
        handler = this,
        executable = Path.of("/bin/echo"),
        argumentPattern = defaultArgumentPattern,
        environment = emptyMap(),
    )
}
