package dev.ov7a.semdiff.settings

import dev.ov7a.semdiff.ide.DiffInputFiles
import dev.ov7a.semdiff.ide.SemanticDiffService
import dev.ov7a.semdiff.ide.SideText
import dev.ov7a.semdiff.model.SemanticDiffResult
import dev.ov7a.semdiff.model.fragments.FragmentPlan
import dev.ov7a.semdiff.model.fragments.FragmentPlanner
import dev.ov7a.semdiff.tools.HandlerRegistry
import dev.ov7a.semdiff.tools.SemanticDiffToolHandler
import dev.ov7a.semdiff.tools.ToolInvocation
import dev.ov7a.semdiff.tools.VersionDetection
import java.nio.file.Path

/**
 * Backs the Detect and Test buttons.
 *
 * Test goes further than the platform's external-tool test: it parses the output and reports how
 * many fragments came out. A tool that runs cleanly but emits a shape we cannot read is the failure
 * that actually matters, and a raw stdout dump would hide it.
 *
 * It also probes **several languages**. An earlier version used one Kotlin sample, so testing a
 * perfectly good diffsitter — which has no Kotlin grammar — reported "FAILED" for the whole tool.
 * Language coverage differs sharply between these tools, so the report is per language and the
 * overall verdict is only FAILED when nothing worked at all.
 */
object ToolProbe {

    data class DetectionReport(
        val handler: SemanticDiffToolHandler?,
        val version: String?,
        val log: String,
    )

    fun detect(executablePath: String): DetectionReport {
        if (executablePath.isBlank()) {
            return DetectionReport(null, null, "Point at an executable first.")
        }
        val executable = Path.of(executablePath)
        val service = SemanticDiffService.instance

        HandlerRegistry.all.forEach { handler ->
            when (val detection = service.detectVersion(handler, executable)) {
                is VersionDetection.Supported -> return DetectionReport(
                    handler,
                    detection.version.toString(),
                    "Detected ${handler.displayName} ${detection.version}.",
                )

                is VersionDetection.OutOfRange -> return DetectionReport(
                    handler,
                    detection.version.toString(),
                    "Detected ${handler.displayName} ${detection.version}, but this plugin is only known to " +
                        "parse ${detection.supported}. It may still work; check the Test output below.",
                )

                is VersionDetection.NotRunnable -> return DetectionReport(
                    null,
                    null,
                    "Could not run $executablePath: ${detection.reason}",
                )

                is VersionDetection.NotThisTool -> Unit
            }
        }

        return DetectionReport(
            null,
            null,
            "Could not identify this tool — it may be a wrapper script or a version this plugin does not know.\n" +
                "Pick a parser manually in the Parser box; detection will not override your choice.",
        )
    }

    fun test(invocation: ToolInvocation): String {
        val outcomes = SAMPLES.map { sample -> sample to run(invocation, sample) }
        val working = outcomes.count { it.second.usable }

        return buildString {
            appendLine(commandLine(invocation))
            appendLine()

            when {
                working == outcomes.size -> appendLine("OK — this tool works.")
                working > 0 -> appendLine(
                    "Partly OK — this tool works for $working of ${outcomes.size} sample languages. " +
                        "Files in the others fall back to the built-in diff.",
                )

                else -> appendLine("FAILED — every sample failed, so the built-in diff would always be used.")
            }
            appendLine()

            outcomes.forEach { (sample, outcome) ->
                appendLine("${sample.language.padEnd(10)} ${outcome.summary}")
            }
        }
    }

    /** The command line as it will really be run, with the placeholders left visible. */
    private fun commandLine(invocation: ToolInvocation): String {
        val environment = invocation.environment.entries
            .joinToString(" ") { "${it.key}=${it.value}" }
            .let { if (it.isEmpty()) "" else "$it " }
        return "$ $environment${invocation.executable} ${invocation.argumentPattern}"
    }

    private data class Outcome(val usable: Boolean, val summary: String)

    private fun run(invocation: ToolInvocation, sample: Sample): Outcome =
        DiffInputFiles.create(
            left = SideText("left", sample.left, "Sample.${sample.extension}"),
            right = SideText("right", sample.right, "Sample.${sample.extension}"),
        ).use { files ->
            when (val result = SemanticDiffService.instance.diff(invocation, files.inputs)) {
                is SemanticDiffResult.Unsupported -> Outcome(false, "not usable — ${result.reason}")

                is SemanticDiffResult.Unchanged -> Outcome(
                    false,
                    "reported no changes, but the samples differ — the arguments are probably wrong",
                )

                is SemanticDiffResult.Changed -> when (
                    val plan = FragmentPlanner.plan(result, sample.left, sample.right)
                ) {
                    is FragmentPlan.Fragments -> Outcome(
                        true,
                        "OK — ${result.spans.size} changed spans, ${result.regions.size} regions, " +
                            "${plan.specs.size} diff fragments",
                    )

                    is FragmentPlan.Rejected -> Outcome(
                        false,
                        "parsed, but the result cannot be shown as a diff — ${plan.reason}",
                    )
                }
            }
        }

    private data class Sample(
        val language: String,
        val extension: String,
        val left: String,
        val right: String,
    )

    /**
     * One sample per language, each with a real token change so a tool that works cannot answer
     * "unchanged". Java is first because it is the only one all three bundled tools parse.
     */
    private val SAMPLES = listOf(
        Sample(
            language = "Java",
            extension = "java",
            left = """
                class Order {
                    int total(int[] lines) {
                        int sum = 0;
                        for (int line : lines) {
                            sum += line;
                        }
                        return sum;
                    }
                }
            """.trimIndent() + "\n",
            right = """
                class Order {
                    long total(int[] lines) {
                        int subtotal = 0;
                        for (int line : lines) {
                            subtotal += line;
                        }
                        return subtotal;
                    }
                }
            """.trimIndent() + "\n",
        ),
        Sample(
            language = "Kotlin",
            extension = "kt",
            left = """
                fun total(lines: List<Int>): Int {
                    val sum = lines.sum()
                    return sum
                }
            """.trimIndent() + "\n",
            right = """
                fun total(lines: List<Int>): Long {
                    val subtotal = lines.sum()
                    return subtotal
                }
            """.trimIndent() + "\n",
        ),
        Sample(
            language = "Python",
            extension = "py",
            left = """
                def total(lines):
                    sum_ = 0
                    for line in lines:
                        sum_ += line
                    return sum_
            """.trimIndent() + "\n",
            right = """
                def total(lines):
                    subtotal = 0
                    for line in lines:
                        subtotal += line
                    return subtotal
            """.trimIndent() + "\n",
        ),
    )
}
