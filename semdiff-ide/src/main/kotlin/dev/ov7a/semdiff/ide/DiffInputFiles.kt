package dev.ov7a.semdiff.ide

import com.intellij.openapi.util.io.FileUtil
import dev.ov7a.semdiff.model.DiffInputs
import dev.ov7a.semdiff.model.SideInput
import java.io.Closeable
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * Materializes diff contents as temp files for the CLI.
 *
 * Diff contents are usually in-memory documents (a VCS revision, a patch preview), so there is
 * rarely a real file to hand the tool. The extension is preserved because every researched tool
 * detects its parser from the file name.
 */
class DiffInputFiles private constructor(
    val inputs: DiffInputs,
    private val files: List<File>,
) : Closeable {

    override fun close() {
        files.forEach { FileUtil.delete(it) }
    }

    companion object {

        fun create(left: SideText, right: SideText, base: SideText? = null): DiffInputFiles {
            val created = mutableListOf<File>()
            try {
                val leftInput = write(left, created)
                val rightInput = write(right, created)
                val baseInput = base?.let { write(it, created) }
                return DiffInputFiles(DiffInputs(leftInput, rightInput, baseInput), created)
            } catch (e: Throwable) {
                created.forEach { FileUtil.delete(it) }
                throw e
            }
        }

        private fun write(side: SideText, created: MutableList<File>): SideInput {
            val suffix = side.fileName.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
            val file = FileUtil.createTempFile("semdiff_${side.slot}_", suffix, true)
            created += file
            file.writeText(side.text, StandardCharsets.UTF_8)
            return SideInput(file.toPath(), side.text, side.fileName)
        }
    }
}

/** One side's content plus the file name whose extension drives the tool's language detection. */
data class SideText(val slot: String, val text: String, val fileName: String)
