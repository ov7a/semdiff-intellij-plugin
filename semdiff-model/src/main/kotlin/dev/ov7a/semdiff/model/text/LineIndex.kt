package dev.ov7a.semdiff.model.text

/**
 * Line boundaries of a document, in char offsets.
 *
 * A "line" runs from its first char up to and including its line terminator, matching the
 * IntelliJ `LineFragment` contract. The final line has no terminator unless the text ends with one,
 * in which case there is one more (empty) line after it — the same convention IntelliJ documents
 * use, so line numbers line up without adjustment.
 */
class LineIndex private constructor(
    private val text: String,
    private val lineStarts: IntArray,
) {
    val lineCount: Int get() = lineStarts.size

    /** Char offset of the first character of [line]. Accepts [lineCount] to mean end of text. */
    fun startOffset(line: Int): Int {
        require(line in 0..lineCount) { "line $line out of bounds (lineCount=$lineCount)" }
        return if (line == lineCount) text.length else lineStarts[line]
    }

    /** Char offset just past [line]'s terminator, or end of text for the last line. */
    fun endOffsetWithSeparator(line: Int): Int {
        require(line in 0 until lineCount) { "line $line out of bounds (lineCount=$lineCount)" }
        return if (line == lineCount - 1) text.length else lineStarts[line + 1]
    }

    /** Char offset of [line]'s terminator, i.e. the end of its visible content. */
    fun endOffsetWithoutSeparator(line: Int): Int {
        var end = endOffsetWithSeparator(line)
        if (end > startOffset(line) && text[end - 1] == '\n') end--
        if (end > startOffset(line) && text[end - 1] == '\r') end--
        return end
    }

    fun lineLength(line: Int): Int = endOffsetWithoutSeparator(line) - startOffset(line)

    /** Every line without its terminator, for matching lines by content. */
    fun allLines(): List<String> = (0 until lineCount).map(::lineText)

    /** Content of [line] without its terminator. */
    fun lineText(line: Int): String = text.substring(startOffset(line), endOffsetWithoutSeparator(line))

    companion object {
        fun of(text: String): LineIndex {
            val starts = ArrayList<Int>()
            starts.add(0)
            var i = 0
            while (i < text.length) {
                if (text[i] == '\n') starts.add(i + 1)
                i++
            }
            return LineIndex(text, starts.toIntArray())
        }
    }
}
