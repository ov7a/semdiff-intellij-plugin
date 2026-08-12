package dev.ov7a.semdiff.tools.difftastic

import dev.ov7a.semdiff.model.DiffInputs
import dev.ov7a.semdiff.model.ProcessResult
import dev.ov7a.semdiff.model.SemanticDiffResult
import dev.ov7a.semdiff.model.Side
import dev.ov7a.semdiff.model.SideInput
import dev.ov7a.semdiff.model.SpanKind
import dev.ov7a.semdiff.model.ToolVersion
import dev.ov7a.semdiff.tools.SemanticDiffRunner
import dev.ov7a.semdiff.tools.ToolInvocation
import dev.ov7a.semdiff.tools.VersionDetection
import dev.ov7a.semdiff.tools.testing.ProcessCommandRunner
import dev.ov7a.semdiff.tools.testing.TestCorpus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.readText

/** Behaviour specific to difftastic, on top of the shared corpus goldens. */
class DifftasticQuirksTest {

    private val handler = DifftasticHandler()
    private val runner = SemanticDiffRunner(ProcessCommandRunner())

    @Test
    fun `version detection recognises difft`() {
        val detection = runner.detectVersion(handler, TestCorpus.requireToolPath(handler.id))

        assertThat(detection).isInstanceOf(VersionDetection.Supported::class.java)
        assertThat((detection as VersionDetection.Supported).version)
            .isEqualTo(ToolVersion(0, 69, 0))
    }

    @Test
    fun `version detection rejects a binary that is not difft`() {
        val detection = runner.detectVersion(handler, Path.of("/bin/echo"))

        assertThat(detection).isInstanceOf(VersionDetection.NotThisTool::class.java)
    }

    @Test
    fun `a missing binary is reported, not thrown`() {
        val result = runner.diff(
            ToolInvocation.defaults(handler, Path.of("/nonexistent/difft")),
            corpusInputs("rename-local"),
        )

        assertThat(result).isInstanceOf(SemanticDiffResult.Unsupported::class.java)
        assertThat((result as SemanticDiffResult.Unsupported).reason).contains("could not run")
    }

    /**
     * JSON output is gated behind `DFT_UNSTABLE=yes`. Without it difft prints its usual
     * side-by-side view, which must degrade to a fallback rather than a parse crash.
     */
    @Test
    fun `output without DFT_UNSTABLE is reported as unsupported`() {
        val invocation = ToolInvocation.defaults(handler, TestCorpus.requireToolPath(handler.id))
            .copy(environment = emptyMap())

        val result = runner.diff(invocation, corpusInputs("rename-local"))

        assertThat(result).isInstanceOf(SemanticDiffResult.Unsupported::class.java)
    }

    @Test
    fun `three-way input is refused before the tool runs`() {
        val inputs = corpusInputs("rename-local").let { it.copy(base = it.left) }

        val result = runner.diff(
            ToolInvocation.defaults(handler, Path.of("/nonexistent/difft")),
            inputs,
        )

        assertThat((result as SemanticDiffResult.Unsupported).reason).contains("three-way")
    }

    /**
     * Upstream documents that changes within block-scoped chunks come out in a different order on
     * each run. The handler normalizes them; without that the goldens would flake.
     */
    @Test
    fun `repeated runs produce an identical model`() {
        val executable = TestCorpus.requireToolPath(handler.id)
        val inputs = corpusInputs("extract-function")

        val results = (1..5).map { runner.diff(ToolInvocation.defaults(handler, executable), inputs) }

        assertThat(results.distinct()).hasSize(1)
    }

    @Test
    fun `spans carry the syntax kind difft reported`() {
        val result = runner.diff(
            ToolInvocation.defaults(handler, TestCorpus.requireToolPath(handler.id)),
            corpusInputs("string-literal"),
        )

        val kinds = (result as SemanticDiffResult.Changed).spans.map { it.kind }.toSet()
        assertThat(kinds).contains(SpanKind.STRING)
    }

    @Test
    fun `unicode spans are converted from bytes to characters`() {
        val inputs = corpusInputs("unicode-spans")

        val result = runner.diff(
            ToolInvocation.defaults(handler, TestCorpus.requireToolPath(handler.id)),
            inputs,
        )

        val rightLines = inputs.right.text.split("\n")
        (result as SemanticDiffResult.Changed).spans
            .filter { it.side == Side.RIGHT }
            .forEach { span ->
                assertThat(span.endChar)
                    .describedAs("span %s must fit line %s", span, rightLines[span.line])
                    .isLessThanOrEqualTo(rightLines[span.line].length)
            }
    }

    @Test
    fun `garbage on stdout is reported as unsupported`() {
        val result = handler.parseOutput(
            ProcessResult(0, "not json at all", ""),
            corpusInputs("rename-local"),
        )

        assertThat((result as SemanticDiffResult.Unsupported).reason).contains("not the expected JSON shape")
    }

    @Test
    fun `empty output is reported with the exit code`() {
        val result = handler.parseOutput(
            ProcessResult(101, "", "difft panicked"),
            corpusInputs("rename-local"),
        )

        assertThat((result as SemanticDiffResult.Unsupported).reason)
            .contains("exit 101")
            .contains("difft panicked")
    }

    @Test
    fun `a line number outside the document is reported instead of trusted`() {
        val json = """
            {"path":"x.kt","language":"Kotlin","status":"changed",
             "aligned_lines":[[0,0]],
             "chunks":[[{"lhs":{"line_number":99,"changes":[{"start":0,"end":1,"content":"a","highlight":"normal"}]}}]]}
        """.trimIndent()

        val result = handler.parseOutput(ProcessResult(1, json, ""), inputs("a", "b"))

        assertThat((result as SemanticDiffResult.Unsupported).reason).contains("line 99")
    }

    @Test
    fun `a byte offset inside a code point is reported instead of guessed`() {
        val json = """
            {"path":"x.kt","language":"Kotlin","status":"changed",
             "aligned_lines":[[0,0]],
             "chunks":[[{"lhs":{"line_number":0,"changes":[{"start":1,"end":2,"content":"?","highlight":"normal"}]}}]]}
        """.trimIndent()

        val result = handler.parseOutput(ProcessResult(1, json, ""), inputs("東", "x"))

        assertThat((result as SemanticDiffResult.Unsupported).reason).contains("does not map to characters")
    }

    private fun corpusInputs(caseId: String): DiffInputs =
        TestCorpus.cases().single { it.id == caseId }.inputs()

    private fun inputs(left: String, right: String): DiffInputs {
        val case = TestCorpus.cases().first()
        return DiffInputs(
            SideInput(case.left, left, "left"),
            SideInput(case.right, right, "right"),
        )
    }

    @Suppress("unused")
    private fun Path.text(): String = readText()
}
