package com.scholia.reader.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/**
 * The four highlighter swatches from the design. [key] is what gets stored in
 * [com.scholia.reader.data.Highlight.color]; the fills adapt to light/dark.
 */
enum class HighlightColor(
    val label: String,
    val key: Int,
    private val lightFill: Color,
    private val darkFill: Color,
    private val lightAccent: Color,
    private val darkAccent: Color,
) {
    Amber("Amber", 0xFFF2C14E.toInt(), Color(0xFFF6DE9C), Color(0xFF8A5A2B), Color(0xFFE39B3B), Color(0xFFE0884E)),
    Sage("Mint Sage", 0xFF8FBF9F.toInt(), Color(0xFFC6E0CC), Color(0xFF3F6B53), Color(0xFF5E8C6A), Color(0xFF6FAF8C)),
    Coral("Dusty Coral", 0xFFF0A890.toInt(), Color(0xFFF8CDBE), Color(0xFF7E3F4D), Color(0xFFCF6A4E), Color(0xFFC9727F)),
    Lavender("Lavender", 0xFFB9B0F0.toInt(), Color(0xFFDCD6FA), Color(0xFF4F4785), Color(0xFF8479CF), Color(0xFF9A8FE0));

    /**
     * Opaque colour for blending over a rendered PDF page: multiplied in light mode (black text
     * stays black) and screened in dark mode (light text stays light).
     */
    fun pageFill(dark: Boolean): Color = if (dark) darkFill else lightFill

    /** Solid tone for swatches, bars and dots. */
    @Composable
    fun accent(): Color = if (isSystemInDarkTheme()) darkAccent else lightAccent

    /** Opaque tint used for Readium decorations (Readium applies its own alpha). */
    fun tint(dark: Boolean): Int = (if (dark) darkAccent else lightFill).toArgb()

    companion object {
        fun fromKey(key: Int): HighlightColor = entries.firstOrNull { it.key == key } ?: Amber
    }
}
