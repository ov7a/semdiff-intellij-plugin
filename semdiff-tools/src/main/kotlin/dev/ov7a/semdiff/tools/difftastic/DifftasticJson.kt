package dev.ov7a.semdiff.tools.difftastic

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire model for `difft --display json`, mirroring `src/display/json.rs` upstream.
 *
 * The format is explicitly unstable and gated behind `DFT_UNSTABLE=yes`, which is why it is pinned
 * here rather than parsed loosely. Two file arguments always produce a single object; only
 * directory mode produces an array.
 */
@Serializable
internal data class DifftasticFile(
    val path: String = "",
    val language: String = "",
    val status: String = "",
    /** Two-element `[lhs, rhs]` arrays; either entry may be null when a line has no counterpart. */
    @SerialName("aligned_lines")
    val alignedLines: List<List<Int?>> = emptyList(),
    val chunks: List<List<DifftasticLine>> = emptyList(),
)

@Serializable
internal data class DifftasticLine(
    val lhs: DifftasticSide? = null,
    val rhs: DifftasticSide? = null,
)

@Serializable
internal data class DifftasticSide(
    @SerialName("line_number")
    val lineNumber: Int,
    val changes: List<DifftasticChange> = emptyList(),
)

/** [start] and [end] are UTF-8 **byte** offsets within the line, not char offsets. */
@Serializable
internal data class DifftasticChange(
    val start: Int,
    val end: Int,
    val content: String = "",
    val highlight: String = "normal",
)
