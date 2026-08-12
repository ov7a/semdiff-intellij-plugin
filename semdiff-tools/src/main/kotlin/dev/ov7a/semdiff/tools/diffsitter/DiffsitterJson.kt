package dev.ov7a.semdiff.tools.diffsitter

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire model for `diffsitter -r json`, a serde dump of the renderer's `DisplayData`.
 *
 * `hunks` is a list of externally tagged `Old`/`New` variants — each element is an object with a
 * single `"Old"` or `"New"` key. Entries are per code point, not per token.
 */
@Serializable
internal data class DiffsitterOutput(
    val hunks: List<DiffsitterHunk> = emptyList(),
    val old: DiffsitterDocument = DiffsitterDocument(),
    val new: DiffsitterDocument = DiffsitterDocument(),
)

@Serializable
internal data class DiffsitterDocument(
    val filename: String = "",
    val text: String = "",
)

@Serializable
internal data class DiffsitterHunk(
    @SerialName("Old")
    val old: List<DiffsitterLine>? = null,
    @SerialName("New")
    val new: List<DiffsitterLine>? = null,
)

@Serializable
internal data class DiffsitterLine(
    @SerialName("line_index")
    val lineIndex: Int,
    val entries: List<DiffsitterEntry> = emptyList(),
)

@Serializable
internal data class DiffsitterEntry(
    val text: String = "",
    @SerialName("start_position")
    val startPosition: DiffsitterPosition,
    @SerialName("end_position")
    val endPosition: DiffsitterPosition,
    @SerialName("kind_id")
    val kindId: Int = 0,
)

/** [column] is a UTF-8 **byte** offset within [row]; [row] is 0-based. */
@Serializable
internal data class DiffsitterPosition(
    val row: Int,
    val column: Int,
)
