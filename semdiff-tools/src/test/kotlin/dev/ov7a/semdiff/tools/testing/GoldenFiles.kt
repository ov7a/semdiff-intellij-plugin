package dev.ov7a.semdiff.tools.testing

import dev.ov7a.semdiff.model.SemanticDiffResult
import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import java.nio.file.Path
import kotlin.io.path.createParentDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * Input-output golden comparison.
 *
 * Expectations are checked in so a tool upgrade shows up as a reviewable diff rather than a
 * mysterious behaviour change. `-Psemdiff.updateGolden=true` rewrites them.
 */
object GoldenFiles {

    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    private val json = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
        encodeDefaults = true
    }

    fun assertMatches(golden: Path, actual: SemanticDiffResult) {
        val serialized = json.encodeToString(SemanticDiffResult.serializer(), actual) + "\n"

        if (System.getProperty("semdiff.updateGolden").toBoolean()) {
            golden.createParentDirectories()
            golden.writeText(serialized)
            return
        }

        check(golden.exists()) {
            "Missing golden file $golden. Run with -Psemdiff.updateGolden=true to create it."
        }
        assertThat(serialized)
            .describedAs("golden %s", golden)
            .isEqualTo(golden.readText())
    }
}
