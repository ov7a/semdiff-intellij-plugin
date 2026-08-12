package dev.ov7a.semdiff.model.text

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * difftastic reports UTF-8 byte offsets. Getting this wrong silently corrupts every diff with a
 * non-ASCII character in it, so each width of code point gets a case.
 */
class Utf8OffsetsTest {

    @Test
    fun `ascii offsets are unchanged`() {
        val offsets = Utf8Offsets.forLine("val x = 1")

        assertThat(offsets.charOffsetOrNull(0)).isZero()
        assertThat(offsets.charOffsetOrNull(4)).isEqualTo(4)
        assertThat(offsets.charOffsetOrNull(9)).isEqualTo(9)
    }

    @Test
    fun `two-byte code points shrink the offset`() {
        // "ééé" is 3 chars and 6 bytes.
        val offsets = Utf8Offsets.forLine("\"ééé\"")

        assertThat(offsets.byteLength).isEqualTo(8)
        assertThat(offsets.charOffsetOrNull(8)).isEqualTo(5)
    }

    @Test
    fun `three-byte code points shrink the offset`() {
        // "東京" is 2 chars and 6 bytes.
        val offsets = Utf8Offsets.forLine("東京")

        assertThat(offsets.byteLength).isEqualTo(6)
        assertThat(offsets.charOffsetOrNull(3)).isEqualTo(1)
        assertThat(offsets.charOffsetOrNull(6)).isEqualTo(2)
    }

    @Test
    fun `astral code points map to a surrogate pair`() {
        // "🎌" is 4 bytes and two UTF-16 units.
        val offsets = Utf8Offsets.forLine("a🎌b")

        assertThat(offsets.byteLength).isEqualTo(6)
        assertThat(offsets.charOffsetOrNull(1)).isEqualTo(1)
        assertThat(offsets.charOffsetOrNull(5)).isEqualTo(3)
        assertThat(offsets.charOffsetOrNull(6)).isEqualTo(4)
    }

    @Test
    fun `combining marks are separate code points`() {
        // "e" + combining acute is 2 chars and 3 bytes.
        val offsets = Utf8Offsets.forLine("éx")

        assertThat(offsets.byteLength).isEqualTo(4)
        assertThat(offsets.charOffsetOrNull(1)).isEqualTo(1)
        assertThat(offsets.charOffsetOrNull(3)).isEqualTo(2)
    }

    @Test
    fun `an offset inside a code point has no character position`() {
        val offsets = Utf8Offsets.forLine("東")

        assertThat(offsets.charOffsetOrNull(1)).isNull()
        assertThat(offsets.charOffsetOrNull(2)).isNull()
    }

    @Test
    fun `out of range offsets are rejected rather than clamped`() {
        val offsets = Utf8Offsets.forLine("ab")

        assertThat(offsets.charOffsetOrNull(-1)).isNull()
        assertThat(offsets.charOffsetOrNull(3)).isNull()
    }
}
