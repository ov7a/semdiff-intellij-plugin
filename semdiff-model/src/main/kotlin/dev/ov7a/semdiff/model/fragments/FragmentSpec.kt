package dev.ov7a.semdiff.model.fragments

/**
 * An IntelliJ `LineFragment` described without depending on IntelliJ.
 *
 * Line ranges and [startOffset1]/[endOffset1]/[startOffset2]/[endOffset2] are absolute and cover
 * whole lines including their terminators. Inner fragment offsets are relative to the enclosing
 * fragment's start on their own side, which is what `LineFragment` requires.
 */
data class FragmentSpec(
    val startLine1: Int,
    val endLine1: Int,
    val startLine2: Int,
    val endLine2: Int,
    val startOffset1: Int,
    val endOffset1: Int,
    val startOffset2: Int,
    val endOffset2: Int,
    val inner: List<InnerFragmentSpec> = emptyList(),
)

data class InnerFragmentSpec(
    val startOffset1: Int,
    val endOffset1: Int,
    val startOffset2: Int,
    val endOffset2: Int,
)

sealed interface FragmentPlan {
    data class Fragments(val specs: List<FragmentSpec>) : FragmentPlan

    /** The result could not be turned into valid fragments; the caller must fall back. */
    data class Rejected(val reason: String) : FragmentPlan
}
