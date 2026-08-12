package dev.ov7a.semdiff.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ToolVersionTest {

    @Test
    fun `parses a plain three-part version`() {
        assertThat(ToolVersion.parseFirst("Difftastic 0.69.0")).isEqualTo(ToolVersion(0, 69, 0))
    }

    @Test
    fun `a missing patch component defaults to zero`() {
        assertThat(ToolVersion.parseFirst("tool 1.2")).isEqualTo(ToolVersion(1, 2, 0))
    }

    @Test
    fun `a suffix is kept but ignored when comparing`() {
        val beta = ToolVersion.parseFirst("tool 2.0.0-beta1")

        assertThat(beta).isEqualTo(ToolVersion(2, 0, 0, "-beta1"))
        assertThat(beta!!.compareTo(ToolVersion(2, 0, 0))).isZero()
    }

    @Test
    fun `text without a version yields null`() {
        assertThat(ToolVersion.parseFirst("no numbers here")).isNull()
    }

    @Test
    fun `components compare numerically, not lexicographically`() {
        assertThat(ToolVersion(0, 9, 0)).isLessThan(ToolVersion(0, 10, 0))
    }

    @Test
    fun `a range excludes its upper bound`() {
        val range = VersionRange.of("0.60.0", "1.0.0")

        assertThat(ToolVersion(0, 60, 0) in range).isTrue()
        assertThat(ToolVersion(0, 69, 0) in range).isTrue()
        assertThat(ToolVersion(1, 0, 0) in range).isFalse()
        assertThat(ToolVersion(0, 59, 9) in range).isFalse()
    }

    @Test
    fun `toString round-trips through the parser`() {
        val version = ToolVersion(1, 2, 3, "-rc1")

        assertThat(ToolVersion.parseFirst(version.toString())).isEqualTo(version)
    }
}
