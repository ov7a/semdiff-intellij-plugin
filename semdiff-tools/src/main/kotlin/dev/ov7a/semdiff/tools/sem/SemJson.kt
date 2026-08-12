package dev.ov7a.semdiff.tools.sem

import kotlinx.serialization.Serializable

/**
 * Wire model for `sem diff <old> <new> --format json`.
 *
 * Line numbers are **1-based and inclusive** on both ends, unlike every other tool here.
 */
@Serializable
internal data class SemOutput(
    val summary: SemSummary = SemSummary(),
    val changes: List<SemChange> = emptyList(),
)

@Serializable
internal data class SemSummary(val total: Int = 0)

@Serializable
internal data class SemChange(
    val changeType: String = "",
    val entityType: String? = null,
    val entityName: String? = null,
    /** New-side range, 1-based inclusive. Absent for a deleted entity. */
    val startLine: Int? = null,
    val endLine: Int? = null,
    /** Old-side range, 1-based inclusive. Absent for an added entity. */
    val oldStartLine: Int? = null,
    val oldEndLine: Int? = null,
)
