package dev.ov7a.semdiff.tools.testing

import dev.ov7a.semdiff.model.DiffInputs
import dev.ov7a.semdiff.model.SideInput
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText

/** A directory of `left.<ext>` / `right.<ext>` pairs shared by every tool's golden suite. */
data class DiffCase(val id: String, val left: Path, val right: Path) {

    fun inputs(): DiffInputs = DiffInputs(
        left = SideInput(left, left.readText(), left.name),
        right = SideInput(right, right.readText(), right.name),
    )
}

object TestCorpus {

    val root: Path = Path.of(
        System.getProperty("semdiff.testData")
            ?: error("semdiff.testData is not set; the cli-tools convention plugin should provide it"),
    )

    fun cases(): List<DiffCase> =
        root.resolve("cases")
            .listDirectoryEntries()
            .filter { it.isDirectory() }
            .sortedBy { it.name }
            .map { directory ->
                val files = directory.listDirectoryEntries().sortedBy { it.name }
                DiffCase(
                    id = directory.name,
                    left = sole(files, "left.", directory),
                    right = sole(files, "right.", directory),
                )
            }

    /** A case is exactly one file per side; two `left.*` files silently break every tool's suite. */
    private fun sole(files: List<Path>, prefix: String, directory: Path): Path {
        val matches = files.filter { it.name.startsWith(prefix) }
        require(matches.size == 1) {
            "case '${directory.name}' must have exactly one '$prefix*' file, found " +
                matches.map { it.name }
        }
        return matches.single()
    }

    fun goldenFile(toolId: String, versionFamily: String, caseId: String): Path =
        root.resolve("expected/$toolId/$versionFamily/$caseId.json")

    /**
     * Path of a provisioned tool, or a skipped test.
     *
     * Skipping keeps the suite usable on a machine without the binaries; `-Psemdiff.requireTools=true`
     * turns the skip into a failure so CI can never be quietly green.
     */
    fun requireToolPath(toolId: String): Path {
        val configured = System.getProperty("semdiff.tool.$toolId")
        val required = System.getProperty("semdiff.requireTools").toBoolean()
        val path = configured?.let(Path::of)
        val available = path != null && path.exists() && Files.isExecutable(path)

        if (!available && required) {
            error("$toolId is not available at '$configured' and -Psemdiff.requireTools=true")
        }
        assumeTrue(available, "$toolId is not provisioned at '$configured'")
        return path!!
    }
}
