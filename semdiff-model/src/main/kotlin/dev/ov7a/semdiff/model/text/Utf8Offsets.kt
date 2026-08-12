package dev.ov7a.semdiff.model.text

/**
 * Translates UTF-8 byte offsets into UTF-16 char offsets within a single line.
 *
 * difftastic reports column positions as byte offsets. Feeding those to IntelliJ unconverted
 * corrupts every diff containing a non-ASCII character, so conversion is mandatory and is the
 * reason handlers receive the original text.
 *
 * Build one per line and reuse it: the mapping is computed once in O(line length).
 */
class Utf8Offsets private constructor(private val charOffsetByByteOffset: IntArray) {

    val byteLength: Int get() = charOffsetByByteOffset.size - 1

    /**
     * Char offset for [byteOffset], or null when the offset is out of range or lands inside a
     * multi-byte sequence. A null means the tool and our copy of the text disagree, which callers
     * must treat as unparseable rather than guess around.
     */
    fun charOffsetOrNull(byteOffset: Int): Int? {
        if (byteOffset < 0 || byteOffset > byteLength) return null
        val charOffset = charOffsetByByteOffset[byteOffset]
        return if (charOffset == INSIDE_CODE_POINT) null else charOffset
    }

    companion object {
        private const val INSIDE_CODE_POINT = -1

        fun forLine(line: String): Utf8Offsets {
            val byteLength = utf8Length(line)
            val mapping = IntArray(byteLength + 1) { INSIDE_CODE_POINT }

            var charOffset = 0
            var byteOffset = 0
            while (charOffset < line.length) {
                mapping[byteOffset] = charOffset
                val codePoint = line.codePointAt(charOffset)
                byteOffset += utf8LengthOf(codePoint)
                charOffset += Character.charCount(codePoint)
            }
            mapping[byteLength] = line.length
            return Utf8Offsets(mapping)
        }

        fun utf8Length(text: String): Int {
            var length = 0
            var i = 0
            while (i < text.length) {
                val codePoint = text.codePointAt(i)
                length += utf8LengthOf(codePoint)
                i += Character.charCount(codePoint)
            }
            return length
        }

        private fun utf8LengthOf(codePoint: Int): Int = when {
            codePoint < 0x80 -> 1
            codePoint < 0x800 -> 2
            codePoint < 0x10000 -> 3
            else -> 4
        }
    }
}
