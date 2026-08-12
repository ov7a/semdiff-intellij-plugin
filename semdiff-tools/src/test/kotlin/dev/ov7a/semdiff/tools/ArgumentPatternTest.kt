package dev.ov7a.semdiff.tools

import dev.ov7a.semdiff.model.DiffInputs
import dev.ov7a.semdiff.model.SideInput
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Path

class ArgumentPatternTest {

    private val inputs = DiffInputs(
        left = SideInput(Path.of("/tmp/left.kt"), "", "left.kt"),
        right = SideInput(Path.of("/tmp/right.kt"), "", "right.kt"),
    )

    @Test
    fun `positional operands, as difftastic wants them`() {
        val argv = ArgumentPattern.expand("--display json --color never %1 %2", inputs)

        assertThat(argv).containsExactly("--display", "json", "--color", "never", "/tmp/left.kt", "/tmp/right.kt")
    }

    @Test
    fun `flagged operands, as diffsitter wants them`() {
        val argv = ArgumentPattern.expand("-r json --old %1 --new %2", inputs)

        assertThat(argv).containsExactly("-r", "json", "--old", "/tmp/left.kt", "--new", "/tmp/right.kt")
    }

    @Test
    fun `a leading subcommand, as sem wants it`() {
        val argv = ArgumentPattern.expand("diff %1 %2 --format json", inputs)

        assertThat(argv).containsExactly("diff", "/tmp/left.kt", "/tmp/right.kt", "--format", "json")
    }

    @Test
    fun `an unused base placeholder drops its token`() {
        val argv = ArgumentPattern.expand("%1 %2 %3", inputs)

        assertThat(argv).containsExactly("/tmp/left.kt", "/tmp/right.kt")
    }

    @Test
    fun `the base placeholder expands when a base is present`() {
        val withBase = inputs.copy(base = SideInput(Path.of("/tmp/base.kt"), "", "base.kt"))

        assertThat(ArgumentPattern.expand("%1 %3 %2", withBase))
            .containsExactly("/tmp/left.kt", "/tmp/base.kt", "/tmp/right.kt")
    }

    @Test
    fun `quoting keeps a path with spaces in one argument`() {
        val spaced = inputs.copy(left = SideInput(Path.of("/tmp/my project/left.kt"), "", "left.kt"))

        assertThat(ArgumentPattern.expand("--old \"%1\"", spaced))
            .containsExactly("--old", "/tmp/my project/left.kt")
    }

    @Test
    fun `an unquoted path with spaces still yields one argument`() {
        val spaced = inputs.copy(left = SideInput(Path.of("/tmp/my project/left.kt"), "", "left.kt"))

        // Substitution happens after tokenizing, so the path cannot be split by its own spaces.
        assertThat(ArgumentPattern.expand("--old %1", spaced))
            .containsExactly("--old", "/tmp/my project/left.kt")
    }

    @Test
    fun `repeated whitespace does not create empty arguments`() {
        assertThat(ArgumentPattern.tokenize("  a   b  ")).containsExactly("a", "b")
    }

    @Test
    fun `an empty quoted token is preserved`() {
        assertThat(ArgumentPattern.tokenize("--sep '' x")).containsExactly("--sep", "", "x")
    }

    @Test
    fun `single quotes group as well as double quotes`() {
        assertThat(ArgumentPattern.tokenize("'a b' \"c d\"")).containsExactly("a b", "c d")
    }
}
