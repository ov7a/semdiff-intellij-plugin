package dev.ov7a.semdiff.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Which document a piece of the diff belongs to. */
@Serializable
enum class Side {
    @SerialName("left")
    LEFT,

    @SerialName("right")
    RIGHT,
}

/** How precise a tool's output is, which decides what the renderer is able to show. */
@Serializable
enum class Granularity {
    /** Character spans inside lines. Inner fragments are available. */
    @SerialName("intra-line")
    INTRA_LINE,

    /** Whole line ranges only. Line-level fragments, no inner fragments. */
    @SerialName("line-range")
    LINE_RANGE,
}

/** Syntactic role of a changed span, as reported by the tool. Rendered only by the rich viewer. */
@Serializable
enum class SpanKind {
    @SerialName("plain")
    PLAIN,

    @SerialName("delimiter")
    DELIMITER,

    @SerialName("string")
    STRING,

    @SerialName("type")
    TYPE,

    @SerialName("comment")
    COMMENT,

    @SerialName("keyword")
    KEYWORD,

    @SerialName("parse-error")
    PARSE_ERROR,
}

@Serializable
enum class RegionChange {
    @SerialName("added")
    ADDED,

    @SerialName("modified")
    MODIFIED,

    @SerialName("deleted")
    DELETED,

    @SerialName("moved")
    MOVED,

    @SerialName("renamed")
    RENAMED,
}

/**
 * One entry of the line alignment between the two documents.
 *
 * Both values are 0-based line numbers; `null` means the line has no counterpart on that side.
 */
@Serializable
data class LinePair(val left: Int? = null, val right: Int? = null)

/** A novel span within a single line. Offsets are UTF-16 char offsets from the line start. */
@Serializable
data class ChangedSpan(
    val side: Side,
    val line: Int,
    val startChar: Int,
    val endChar: Int,
    val kind: SpanKind = SpanKind.PLAIN,
)

/**
 * A named, changed syntactic entity. Only tools with entity-level output populate these.
 *
 * [counterpartStartLine] is where the same entity sits on the other side. It only matters for
 * [RegionChange.MOVED]: IntelliJ's fragments are a monotonic line alignment and cannot express a
 * move, so the move survives here and is drawn by the experimental viewer instead.
 */
@Serializable
data class ChangedRegion(
    val side: Side,
    val startLine: Int,
    val endLine: Int,
    val change: RegionChange,
    val entityKind: String? = null,
    val entityName: String? = null,
    val counterpartStartLine: Int? = null,
)

/**
 * Tool-agnostic result of one semantic diff invocation.
 *
 * This is the contract every handler produces and everything downstream consumes; it is also what
 * the golden tests serialize, so changing it invalidates expectations.
 */
@Serializable
sealed interface SemanticDiffResult {

    /**
     * The tool ran but its output cannot be turned into a diff — malformed JSON, a shape this
     * handler version does not know, or output that failed validation. Callers fall back to the
     * built-in diff.
     */
    @Serializable
    @SerialName("unsupported")
    data class Unsupported(val reason: String) : SemanticDiffResult

    @Serializable
    @SerialName("unchanged")
    data object Unchanged : SemanticDiffResult

    @Serializable
    @SerialName("changed")
    data class Changed(
        val granularity: Granularity,
        val alignment: List<LinePair>,
        val spans: List<ChangedSpan> = emptyList(),
        val regions: List<ChangedRegion> = emptyList(),
    ) : SemanticDiffResult
}
