package dev.ov7a.semdiff.settings

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.ov7a.semdiff.tools.HandlerRegistry
import dev.ov7a.semdiff.tools.ToolInvocation
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists

/**
 * The Test button's verdict.
 *
 * Written after it reported "FAILED" for a working diffsitter: the single sample was Kotlin, which
 * diffsitter has no grammar for, so the whole tool looked broken. The verdict now has to reflect the
 * tool, not one language.
 */
class ToolProbeTest : BasePlatformTestCase() {

    fun `test a working difftastic is not reported as failed`() {
        val report = testReport("difftastic") ?: return

        assertFalse("difftastic reported as failed:\n$report", report.contains("FAILED"))
        assertTrue("expected an OK verdict:\n$report", report.contains("OK"))
    }

    /**
     * diffsitter 0.9 has no Kotlin grammar. That must show up as one language failing, not as the
     * tool failing.
     */
    fun `test a tool with partial language coverage is not reported as failed`() {
        val report = testReport("diffsitter") ?: return

        assertFalse("diffsitter reported as failed:\n$report", report.contains("FAILED"))
        assertTrue("expected a per-language verdict:\n$report", report.contains("Partly OK") || report.contains("OK —"))
    }

    fun `test every sample language is reported`() {
        val report = testReport("difftastic") ?: return

        listOf("Java", "Kotlin", "Python").forEach { language ->
            assertTrue("no line for $language:\n$report", report.contains(language))
        }
    }

    /** A binary that is not there has to be reported as a failure, or the verdict means nothing. */
    fun `test a missing binary is reported as failed`() {
        val handler = HandlerRegistry.byId("difftastic")!!
        val report = ToolProbe.test(ToolInvocation.defaults(handler, Path.of("/nonexistent/difft")))

        assertTrue("expected FAILED for a missing binary:\n$report", report.contains("FAILED"))
    }

    fun `test the report shows the command line with its placeholders`() {
        val report = testReport("difftastic") ?: return

        assertTrue("no command line in:\n$report", report.contains("%1") && report.contains("%2"))
    }

    private fun testReport(handlerId: String): String? {
        val executable = tool(handlerId) ?: return null
        val handler = HandlerRegistry.byId(handlerId)!!
        return ToolProbe.test(ToolInvocation.defaults(handler, executable))
    }

    private fun tool(id: String): Path? {
        val configured = System.getProperty("semdiff.tool.$id")
        val path = configured?.let(Path::of)
        if (path != null && path.exists() && Files.isExecutable(path)) return path

        check(!System.getProperty("semdiff.requireTools").toBoolean()) {
            "$id is not available at '$configured' and -Psemdiff.requireTools=true"
        }
        return null
    }
}
