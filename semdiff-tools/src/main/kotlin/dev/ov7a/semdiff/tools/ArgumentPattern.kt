package dev.ov7a.semdiff.tools

import dev.ov7a.semdiff.model.DiffInputs

/**
 * Builds argv from the user-editable argument pattern.
 *
 * This is the reason handlers do not build their own command lines: every argv shape found in the
 * researched tools — positional operands, flagged operands, a leading subcommand — is expressible
 * as a pattern, so it can live in settings where the user can correct it.
 *
 * Placeholders are `%1` (left), `%2` (right) and `%3` (base). A placeholder with no corresponding
 * input expands to nothing and its token is dropped.
 */
object ArgumentPattern {

    fun expand(pattern: String, inputs: DiffInputs): List<String> {
        val replacements = buildMap {
            put("%1", inputs.left.path.toString())
            put("%2", inputs.right.path.toString())
            put("%3", inputs.base?.path?.toString() ?: "")
        }

        return tokenize(pattern)
            .map { token -> replacements.entries.fold(token) { acc, (key, value) -> acc.replace(key, value) } }
            .filter { it.isNotEmpty() }
    }

    /**
     * Splits on whitespace, honouring single and double quotes so paths with spaces survive a
     * pattern like `--old "%1"`. Backslash escaping is deliberately not supported: on Windows it
     * would collide with path separators, and quoting covers the real cases.
     */
    fun tokenize(pattern: String): List<String> {
        val tokens = mutableListOf<String>()
        val current = StringBuilder()
        var quote: Char? = null
        var started = false

        for (char in pattern) {
            when {
                quote != null && char == quote -> quote = null
                quote != null -> current.append(char)
                char == '"' || char == '\'' -> {
                    quote = char
                    started = true
                }
                char.isWhitespace() -> {
                    if (started) {
                        tokens += current.toString()
                        current.setLength(0)
                        started = false
                    }
                }
                else -> {
                    current.append(char)
                    started = true
                }
            }
        }
        if (started) tokens += current.toString()
        return tokens
    }
}
