package dev.ov7a.semdiff.diff

import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.ui.JBColor
import dev.ov7a.semdiff.model.RegionChange
import dev.ov7a.semdiff.model.SpanKind
import java.awt.Color

/**
 * How the experimental viewer marks what the tool reported.
 *
 * Three rules, all of them learned from marks that turned out to be invisible:
 *
 * The **foreground is never touched**. Recolouring the text does nothing on a syntax-highlighted
 * file, where the editor has already coloured strings green and keywords blue. The background is
 * left alone too — the diff viewer owns it, and overwriting it would erase the added/removed signal.
 *
 * The colours deliberately **clash** with the syntax colour of what they mark: magenta under a green
 * string, teal under a blue keyword. An earlier version reused each kind's own syntax colour, so
 * every mark was the same hue as the glyphs above it.
 *
 * Every colour has a **built-in default** and only optionally comes from the scheme. Reading them
 * from the scheme alone meant nothing was drawn at all under any scheme this plugin does not ship
 * attributes for — and it ships them for `Default` and `Darcula`, while a current IDE uses
 * `IntelliJ Light` and `Dark`. The scheme still wins where it defines a value, so the marks stay
 * retheme-able; it just cannot make them vanish.
 */
object SpanColors {

    /**
     * TEMPORARY diagnostic. Draws every mark as a magenta block with a thick red underline, ignoring
     * kinds and the scheme, so "nothing visible" can be told apart from "too subtle to notice".
     */
    private const val DIAGNOSTIC = false

    /** Lets tests skip the per-kind styling checks while the diagnostic override is on. */
    val isDiagnostic: Boolean get() = DIAGNOSTIC

    private val spanStyles: Map<SpanKind, Style> = mapOf(
        // Bold, not thin: PLAIN is every span a kindless tool reports, so it is the only mark
        // diffsitter draws and a hairline under already-highlighted text is easy to miss.
        SpanKind.PLAIN to Style("PLAIN", EffectType.BOLD_LINE_UNDERSCORE, 0xE8710A, 0xFFA657),
        SpanKind.STRING to Style("STRING", EffectType.BOLD_LINE_UNDERSCORE, 0xC2185B, 0xFF7EB6),
        SpanKind.KEYWORD to Style("KEYWORD", EffectType.WAVE_UNDERSCORE, 0x00838F, 0x4DD0E1),
        SpanKind.TYPE to Style("TYPE", EffectType.BOLD_DOTTED_LINE, 0x558B2F, 0xAED581),
        SpanKind.COMMENT to Style("COMMENT", EffectType.BOLD_LINE_UNDERSCORE, 0x1565C0, 0x64B5F6),
        SpanKind.DELIMITER to Style("DELIMITER", EffectType.BOLD_DOTTED_LINE, 0x6D4C41, 0xBCAAA4),
        SpanKind.PARSE_ERROR to Style("PARSE_ERROR", EffectType.WAVE_UNDERSCORE, 0xD50000, 0xFF5252),
    )

    private val movedStyle = Style("MOVED", EffectType.BOXED, 0x6A1B9A, 0xCE93D8)
    private val entityStyle = Style("ENTITY", EffectType.BOXED, 0x00695C, 0x4DB6AC)

    /** Null only for a kind with no style at all. */
    fun attributesFor(kind: SpanKind, scheme: EditorColorsScheme): TextAttributes? {
        if (DIAGNOSTIC) return diagnosticAttributes()
        val style = spanStyles[kind] ?: return null
        return TextAttributes().apply {
            effectColor = style.colour(scheme)
            effectType = style.effect
        }
    }

    /**
     * A box around a whole reported entity.
     *
     * This is the only thing the experimental viewer can add for a tool that works at entity
     * granularity: sem reports no character spans, so without it the experimental and normal viewers
     * looked identical with sem selected.
     */
    fun regionAttributes(change: RegionChange, scheme: EditorColorsScheme): TextAttributes {
        if (DIAGNOSTIC) return diagnosticAttributes()
        val style = if (change == RegionChange.MOVED) movedStyle else entityStyle
        val colour = style.colour(scheme)
        return TextAttributes().apply {
            effectColor = colour
            effectType = style.effect
            errorStripeColor = colour
        }
    }

    /** Every span kind this marks, so tests can check none of them resolves to nothing. */
    fun colouredKinds(): Set<SpanKind> = spanStyles.keys

    private fun diagnosticAttributes() = TextAttributes().apply {
        backgroundColor = Color(255, 0, 255)
        effectColor = Color(255, 0, 0)
        effectType = EffectType.BOLD_LINE_UNDERSCORE
    }

    private class Style(name: String, val effect: EffectType, light: Int, dark: Int) {
        private val key: TextAttributesKey = TextAttributesKey.createTextAttributesKey("SEMDIFF_$name")
        private val fallback: Color = JBColor(Color(light), Color(dark))

        /** The scheme's colour where it has one, the built-in default otherwise. */
        fun colour(scheme: EditorColorsScheme): Color =
            scheme.getAttributes(key)?.foregroundColor ?: fallback
    }
}
