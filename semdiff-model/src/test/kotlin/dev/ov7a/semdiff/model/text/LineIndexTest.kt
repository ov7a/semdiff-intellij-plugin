package dev.ov7a.semdiff.model.text

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class LineIndexTest {

    @Test
    fun `text ending with a newline has a trailing empty line`() {
        val index = LineIndex.of("a\nb\n")

        assertThat(index.lineCount).isEqualTo(3)
        assertThat(index.lineText(2)).isEmpty()
    }

    @Test
    fun `text without a trailing newline has no extra line`() {
        val index = LineIndex.of("a\nb")

        assertThat(index.lineCount).isEqualTo(2)
        assertThat(index.lineText(1)).isEqualTo("b")
    }

    @Test
    fun `last line offsets stop at the end of the text`() {
        val index = LineIndex.of("a\nbb")

        assertThat(index.startOffset(1)).isEqualTo(2)
        assertThat(index.endOffsetWithSeparator(1)).isEqualTo(4)
        assertThat(index.endOffsetWithoutSeparator(1)).isEqualTo(4)
    }

    @Test
    fun `separator is included in the end offset but not the visible end`() {
        val index = LineIndex.of("a\nb\n")

        assertThat(index.endOffsetWithSeparator(0)).isEqualTo(2)
        assertThat(index.endOffsetWithoutSeparator(0)).isEqualTo(1)
    }

    @Test
    fun `carriage returns are excluded from visible line content`() {
        val index = LineIndex.of("a\r\nb\r\n")

        assertThat(index.lineText(0)).isEqualTo("a")
        assertThat(index.lineLength(0)).isEqualTo(1)
        assertThat(index.endOffsetWithSeparator(0)).isEqualTo(3)
    }

    @Test
    fun `startOffset accepts lineCount to mean end of text`() {
        val index = LineIndex.of("a\nb")

        assertThat(index.startOffset(2)).isEqualTo(3)
    }

    @Test
    fun `empty text is a single empty line`() {
        val index = LineIndex.of("")

        assertThat(index.lineCount).isEqualTo(1)
        assertThat(index.lineText(0)).isEmpty()
        assertThat(index.startOffset(0)).isZero()
    }
}
