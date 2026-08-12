package dev.ov7a.semdiff.model

import kotlinx.serialization.Serializable

/**
 * A tool version. Only the three numeric components participate in comparison; [suffix] keeps
 * whatever the tool appended (`-beta1`, `+build.7`) so it can be shown back to the user.
 */
@Serializable
data class ToolVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val suffix: String = "",
) : Comparable<ToolVersion> {

    override fun compareTo(other: ToolVersion): Int =
        compareValuesBy(this, other, ToolVersion::major, ToolVersion::minor, ToolVersion::patch)

    override fun toString(): String = "$major.$minor.$patch$suffix"

    companion object {
        private val PATTERN = Regex("""(\d+)\.(\d+)(?:\.(\d+))?([^\s]*)""")

        /** Finds the first version-looking token in [text]. Returns null when there is none. */
        fun parseFirst(text: String): ToolVersion? {
            val match = PATTERN.find(text) ?: return null
            return ToolVersion(
                major = match.groupValues[1].toInt(),
                minor = match.groupValues[2].toInt(),
                patch = match.groupValues[3].ifEmpty { "0" }.toInt(),
                suffix = match.groupValues[4],
            )
        }
    }
}

/** Half-open version range, `[from, until)`. */
data class VersionRange(val from: ToolVersion, val until: ToolVersion) {
    operator fun contains(version: ToolVersion): Boolean = version >= from && version < until

    override fun toString(): String = "[$from, $until)"

    companion object {
        fun of(from: String, until: String): VersionRange =
            VersionRange(
                requireNotNull(ToolVersion.parseFirst(from)) { "not a version: $from" },
                requireNotNull(ToolVersion.parseFirst(until)) { "not a version: $until" },
            )
    }
}
