package dev.ov7a.semdiff.diff

import com.intellij.diff.comparison.ComparisonManager
import com.intellij.diff.comparison.ComparisonPolicy
import com.intellij.diff.fragments.DiffFragment
import com.intellij.diff.fragments.DiffFragmentImpl
import com.intellij.diff.fragments.LineFragment
import com.intellij.diff.fragments.LineFragmentImpl
import com.intellij.diff.util.DiffUserDataKeysEx
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import dev.ov7a.semdiff.ide.DiffInputFiles
import dev.ov7a.semdiff.ide.SemanticDiffNotifications
import dev.ov7a.semdiff.ide.SemanticDiffService
import dev.ov7a.semdiff.ide.SideText
import dev.ov7a.semdiff.model.SemanticDiffResult
import dev.ov7a.semdiff.model.fragments.FragmentPlan
import dev.ov7a.semdiff.model.fragments.FragmentPlanner
import dev.ov7a.semdiff.model.fragments.FragmentSpec
import dev.ov7a.semdiff.tools.ToolInvocation
import java.util.concurrent.atomic.AtomicReference

/**
 * Feeds tool-computed fragments into the stock diff viewer.
 *
 * Installed on the request under [DiffUserDataKeysEx.CUSTOM_DIFF_COMPUTER], which
 * `DiffUtil.createTextDiffProvider` picks up — that is the whole integration. The viewer calls this
 * from its background rediff, so running a subprocess here is expected and cancellable.
 */
class SemanticDiffComputer(
    private val project: Project?,
    private val invocation: ToolInvocation,
    private val fileName: String,
) : DiffUserDataKeysEx.DiffComputer {

    /** Last successful result, for the rich viewer to colour spans from. */
    private val lastResult = AtomicReference<SemanticDiffResult.Changed?>()

    fun lastChanged(): SemanticDiffResult.Changed? = lastResult.get()

    /** For diagnostics: which tool produced the current result. */
    val toolId: String get() = invocation.handler.id

    override fun compute(
        text1: CharSequence,
        text2: CharSequence,
        policy: ComparisonPolicy,
        innerChanges: Boolean,
        indicator: ProgressIndicator,
    ): List<LineFragment> {
        // The ignore policy is deliberately not consulted. An earlier version fell back to the
        // built-in computer for anything but ComparisonPolicy.DEFAULT, which silently disabled the
        // whole plugin for everyone who has "Ignore whitespaces" turned on — a common setting. A
        // semantic diff is already whitespace-insensitive, so it answers that request at least as
        // well as the built-in comparison does; there is nothing to re-apply on top.
        val leftText = text1.toString()
        val rightText = text2.toString()

        val result = try {
            runTool(leftText, rightText)
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: Throwable) {
            LOG.warn("Semantic diff failed", e)
            SemanticDiffResult.Unsupported(e.message ?: e.toString())
        }

        lastResult.set(null)

        return when (result) {
            is SemanticDiffResult.Unchanged -> emptyList()

            is SemanticDiffResult.Unsupported -> {
                SemanticDiffNotifications.reportFallback(project, invocation.handler.displayName, result.reason)
                builtIn(text1, text2, policy, innerChanges, indicator)
            }

            is SemanticDiffResult.Changed -> when (
                val plan = FragmentPlanner.plan(result, leftText, rightText, innerChanges)
            ) {
                is FragmentPlan.Fragments -> {
                    lastResult.set(result)
                    plan.specs.map(::toLineFragment)
                }

                is FragmentPlan.Rejected -> {
                    SemanticDiffNotifications.reportFallback(
                        project,
                        invocation.handler.displayName,
                        plan.reason,
                    )
                    builtIn(text1, text2, policy, innerChanges, indicator)
                }
            }
        }
    }

    private fun runTool(leftText: String, rightText: String): SemanticDiffResult =
        DiffInputFiles.create(
            left = SideText("left", leftText, fileName),
            right = SideText("right", rightText, fileName),
        ).use { files ->
            SemanticDiffService.instance.diff(invocation, files.inputs)
        }

    private fun builtIn(
        text1: CharSequence,
        text2: CharSequence,
        policy: ComparisonPolicy,
        innerChanges: Boolean,
        indicator: ProgressIndicator,
    ): List<LineFragment> {
        val comparison = ComparisonManager.getInstance()
        return if (innerChanges) {
            comparison.compareLinesInner(text1, text2, policy, indicator)
        } else {
            comparison.compareLines(text1, text2, policy, indicator)
        }
    }

    private fun toLineFragment(spec: FragmentSpec): LineFragment =
        LineFragmentImpl(
            spec.startLine1,
            spec.endLine1,
            spec.startLine2,
            spec.endLine2,
            spec.startOffset1,
            spec.endOffset1,
            spec.startOffset2,
            spec.endOffset2,
            spec.inner.map<_, DiffFragment> {
                DiffFragmentImpl(it.startOffset1, it.endOffset1, it.startOffset2, it.endOffset2)
            }.ifEmpty { null },
        )

    companion object {
        private val LOG = Logger.getInstance(SemanticDiffComputer::class.java)

        /** Lets the rich viewer reach the computer that produced its fragments. */
        val KEY: Key<SemanticDiffComputer> = Key.create("semdiff.computer")
    }
}
