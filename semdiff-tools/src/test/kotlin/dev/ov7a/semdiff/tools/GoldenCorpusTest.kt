package dev.ov7a.semdiff.tools

import dev.ov7a.semdiff.model.SemanticDiffResult
import dev.ov7a.semdiff.model.fragments.FragmentPlan
import dev.ov7a.semdiff.model.fragments.FragmentPlanner
import dev.ov7a.semdiff.tools.diffsitter.DiffsitterHandler
import dev.ov7a.semdiff.tools.difftastic.DifftasticHandler
import dev.ov7a.semdiff.tools.sem.SemHandler
import dev.ov7a.semdiff.tools.testing.GoldenFiles
import dev.ov7a.semdiff.tools.testing.ProcessCommandRunner
import dev.ov7a.semdiff.tools.testing.TestCorpus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DynamicContainer
import org.junit.jupiter.api.DynamicNode
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

/**
 * Runs every registered tool over the shared corpus and pins the resulting model.
 *
 * The version family in the golden path is major.minor: these output formats are unstable, so a
 * patch bump is expected to be compatible while a minor bump is not.
 */
class GoldenCorpusTest {

    private val runner = SemanticDiffRunner(ProcessCommandRunner())

    private val tools = listOf(
        DifftasticHandler() to "0.69",
        DiffsitterHandler() to "0.9",
        SemHandler() to "0.21",
    )

    @TestFactory
    fun corpus(): List<DynamicNode> = tools.map { (handler, versionFamily) ->
        DynamicContainer.dynamicContainer(
            handler.id,
            TestCorpus.cases().map { case ->
                DynamicTest.dynamicTest(case.id) {
                    val executable = TestCorpus.requireToolPath(handler.id)
                    val inputs = case.inputs()
                    val result = runner.diff(ToolInvocation.defaults(handler, executable), inputs)

                    GoldenFiles.assertMatches(TestCorpus.goldenFile(handler.id, versionFamily, case.id), result)

                    // A pinned DTO is only worth pinning if the viewer can actually use it: a
                    // `Changed` result that the planner rejects would silently fall back in the IDE.
                    if (result is SemanticDiffResult.Changed) {
                        val plan = FragmentPlanner.plan(result, inputs.left.text, inputs.right.text)
                        assertThat(plan)
                            .describedAs("%s / %s produced a Changed result the planner rejects", handler.id, case.id)
                            .isInstanceOf(FragmentPlan.Fragments::class.java)
                    }
                }
            },
        )
    }
}
