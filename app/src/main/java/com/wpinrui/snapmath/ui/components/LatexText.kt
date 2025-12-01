package com.wpinrui.snapmath.ui.components

import android.util.Log
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import ru.noties.jlatexmath.JLatexMathDrawable

private const val TAG = "Snapmath.LatexText"

/**
 * Renders LaTeX math expressions as images using jlatexmath-android.
 * Falls back to plain text if rendering fails.
 */
@Composable
fun LatexText(
    latex: String,
    modifier: Modifier = Modifier,
    textSize: Float = 48f,
    textColor: Color = LocalContentColor.current,
    backgroundColor: Color = Color.Transparent,
    minHeight: Dp = 24.dp
) {
    val colorArgb = textColor.toArgb()
    val bgArgb = backgroundColor.toArgb()

    val drawable = remember(latex, textSize, colorArgb, bgArgb) {
        try {
            JLatexMathDrawable.builder(latex)
                .textSize(textSize)
                .color(colorArgb)
                .background(bgArgb)
                .padding(4)
                .build()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to render LaTeX: '${latex.take(50)}${if (latex.length > 50) "..." else ""}' - ${e.message}")
            null
        }
    }

    if (drawable != null) {
        val bitmap = remember(drawable) {
            drawable.toBitmap()
        }
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "LaTeX: $latex",
            modifier = modifier
                .heightIn(min = minHeight),
            contentScale = ContentScale.Fit
        )
    } else {
        // Fallback to plain text
        Text(
            text = latex,
            modifier = modifier,
            style = LocalTextStyle.current
        )
    }
}

/**
 * Composable that renders mixed content containing both regular text and LaTeX expressions.
 * LaTeX expressions should be wrapped in $ delimiters (inline) or $$ (display).
 */
@Composable
fun MathText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    textColor: Color = LocalContentColor.current
) {
    val density = LocalDensity.current
    val textSizePx = with(density) { style.fontSize.toPx() }

    // Parse text for LaTeX expressions
    val segments = remember(text) { parseMathSegments(text) }

    // For now, if there's any LaTeX, try to render the whole thing as LaTeX
    // This is simpler and works well for math solutions
    val hasLatex = segments.any { it.isLatex }

    if (hasLatex && segments.size == 1 && segments[0].isLatex) {
        // Pure LaTeX expression
        LatexText(
            latex = segments[0].content,
            modifier = modifier,
            textSize = textSizePx * 1.2f,
            textColor = textColor
        )
    } else {
        // Mixed content - render as text with LaTeX segments inline
        // For simplicity, we'll render LaTeX blocks separately
        androidx.compose.foundation.layout.Column(modifier = modifier) {
            segments.forEach { segment ->
                if (segment.isLatex) {
                    LatexText(
                        latex = segment.content,
                        textSize = textSizePx * 1.2f,
                        textColor = textColor,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text(
                        text = segment.content,
                        style = style,
                        color = textColor
                    )
                }
            }
        }
    }
}

private data class MathSegment(
    val content: String,
    val isLatex: Boolean
)

/**
 * Parses text into segments of regular text and LaTeX expressions.
 * Supports $...$ for inline math and $$...$$ for display math.
 * Also supports \[...\] and \(...\) delimiters.
 */
private fun parseMathSegments(text: String): List<MathSegment> {
    val segments = mutableListOf<MathSegment>()
    var remaining = text

    // Pattern to match various LaTeX delimiters
    val patterns = listOf(
        Regex("""\$\$(.*?)\$\$""", RegexOption.DOT_MATCHES_ALL),  // Display math $$...$$
        Regex("""\\\[(.*?)\\\]""", RegexOption.DOT_MATCHES_ALL),  // Display math \[...\]
        Regex("""\$([^$]+)\$"""),  // Inline math $...$
        Regex("""\\\((.*?)\\\)""", RegexOption.DOT_MATCHES_ALL)   // Inline math \(...\)
    )

    while (remaining.isNotEmpty()) {
        var earliestMatch: MatchResult? = null
        var earliestPattern: Regex? = null

        for (pattern in patterns) {
            val match = pattern.find(remaining)
            if (match != null && (earliestMatch == null || match.range.first < earliestMatch.range.first)) {
                earliestMatch = match
                earliestPattern = pattern
            }
        }

        if (earliestMatch != null) {
            // Add text before the match
            if (earliestMatch.range.first > 0) {
                val textBefore = remaining.substring(0, earliestMatch.range.first).trim()
                if (textBefore.isNotEmpty()) {
                    segments.add(MathSegment(textBefore, false))
                }
            }

            // Add the LaTeX content
            val latexContent = earliestMatch.groupValues[1].trim()
            if (latexContent.isNotEmpty()) {
                segments.add(MathSegment(latexContent, true))
            }

            remaining = remaining.substring(earliestMatch.range.last + 1)
        } else {
            // No more matches, add remaining text
            val trimmed = remaining.trim()
            if (trimmed.isNotEmpty()) {
                segments.add(MathSegment(trimmed, false))
            }
            break
        }
    }

    return segments.ifEmpty { listOf(MathSegment(text, false)) }
}
