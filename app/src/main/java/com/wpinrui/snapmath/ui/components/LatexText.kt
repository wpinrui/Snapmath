package com.wpinrui.snapmath.ui.components

import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
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
 * Represents a row in the rendered output - either a group of inline elements or a single display equation
 */
private sealed class RenderRow {
    data class InlineGroup(val segments: List<MathSegment>) : RenderRow()
    data class DisplayMath(val content: String) : RenderRow()
}

/**
 * Groups segments into render rows - inline segments are grouped together,
 * display math gets its own row.
 */
private fun groupSegmentsIntoRows(segments: List<MathSegment>): List<RenderRow> {
    val rows = mutableListOf<RenderRow>()
    val currentInlineGroup = mutableListOf<MathSegment>()

    fun flushInlineGroup() {
        if (currentInlineGroup.isNotEmpty()) {
            rows.add(RenderRow.InlineGroup(currentInlineGroup.toList()))
            currentInlineGroup.clear()
        }
    }

    for (segment in segments) {
        if (segment.isDisplayMath) {
            flushInlineGroup()
            rows.add(RenderRow.DisplayMath(segment.content))
        } else {
            currentInlineGroup.add(segment)
        }
    }
    flushInlineGroup()

    return rows
}

/**
 * Composable that renders mixed content containing both regular text and LaTeX expressions.
 * LaTeX expressions should be wrapped in $ delimiters (inline) or $$ (display).
 * - Inline math ($...$, \(...\)) flows with text on the same line
 * - Display math ($$...$$, \[...\]) renders on its own line, centered
 */
@OptIn(ExperimentalLayoutApi::class)
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

    // Check if it's a single pure LaTeX expression
    if (segments.size == 1 && segments[0].isLatex) {
        LatexText(
            latex = segments[0].content,
            modifier = modifier,
            textSize = textSizePx * 1.2f,
            textColor = textColor
        )
    } else {
        // Check if we have any display math - if so, use Column layout
        val hasDisplayMath = segments.any { it.isDisplayMath }

        if (hasDisplayMath) {
            // Pre-group segments into rows
            val rows = remember(segments) { groupSegmentsIntoRows(segments) }

            Column(modifier = modifier) {
                rows.forEach { row ->
                    when (row) {
                        is RenderRow.DisplayMath -> {
                            LatexText(
                                latex = row.content,
                                modifier = Modifier.fillMaxWidth(),
                                textSize = textSizePx * 1.2f,
                                textColor = textColor
                            )
                        }
                        is RenderRow.InlineGroup -> {
                            FlowRow(
                                horizontalArrangement = Arrangement.Start,
                                verticalArrangement = Arrangement.Center
                            ) {
                                row.segments.forEach { segment ->
                                    when {
                                        segment.isLatex -> {
                                            LatexText(
                                                latex = segment.content,
                                                textSize = textSizePx * 1.1f,
                                                textColor = textColor,
                                                minHeight = 16.dp
                                            )
                                        }
                                        segment.isBold -> {
                                            Text(
                                                text = segment.content,
                                                style = style.copy(fontWeight = FontWeight.Bold),
                                                color = textColor
                                            )
                                        }
                                        else -> {
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
                    }
                }
            }
        } else {
            // All inline content - use FlowRow
            FlowRow(
                modifier = modifier,
                horizontalArrangement = Arrangement.Start,
                verticalArrangement = Arrangement.Center
            ) {
                segments.forEach { segment ->
                    when {
                        segment.isLatex -> {
                            LatexText(
                                latex = segment.content,
                                textSize = textSizePx * 1.1f,
                                textColor = textColor,
                                minHeight = 16.dp
                            )
                        }
                        segment.isBold -> {
                            Text(
                                text = segment.content,
                                style = style.copy(fontWeight = FontWeight.Bold),
                                color = textColor
                            )
                        }
                        else -> {
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
    }
}

private enum class SegmentType {
    TEXT,
    BOLD_TEXT,
    INLINE_LATEX,
    DISPLAY_LATEX
}

private data class MathSegment(
    val content: String,
    val type: SegmentType
) {
    val isLatex: Boolean get() = type == SegmentType.INLINE_LATEX || type == SegmentType.DISPLAY_LATEX
    val isDisplayMath: Boolean get() = type == SegmentType.DISPLAY_LATEX
    val isBold: Boolean get() = type == SegmentType.BOLD_TEXT
}

private data class PatternInfo(
    val pattern: Regex,
    val segmentType: SegmentType
)

/**
 * Parses text into segments of regular text, LaTeX expressions, and markdown formatting.
 * Supports:
 * - $...$ for inline math
 * - $$...$$ for display math
 * - \[...\] and \(...\) delimiters
 * - **...** for bold text
 * Preserves spacing for inline rendering.
 */
private fun parseMathSegments(text: String): List<MathSegment> {
    val segments = mutableListOf<MathSegment>()
    var remaining = text

    // Patterns ordered by priority (longer/more specific patterns first)
    val patterns = listOf(
        PatternInfo(Regex("""\$\$(.*?)\$\$""", RegexOption.DOT_MATCHES_ALL), SegmentType.DISPLAY_LATEX),   // Display math $$...$$
        PatternInfo(Regex("""\\\[(.*?)\\\]""", RegexOption.DOT_MATCHES_ALL), SegmentType.DISPLAY_LATEX),   // Display math \[...\]
        PatternInfo(Regex("""\*\*(.+?)\*\*"""), SegmentType.BOLD_TEXT),  // Bold **...**
        PatternInfo(Regex("""\$([^$]+)\$"""), SegmentType.INLINE_LATEX),  // Inline math $...$
        PatternInfo(Regex("""\\\((.*?)\\\)""", RegexOption.DOT_MATCHES_ALL), SegmentType.INLINE_LATEX)   // Inline math \(...\)
    )

    while (remaining.isNotEmpty()) {
        var earliestMatch: MatchResult? = null
        var matchedPatternInfo: PatternInfo? = null

        for (patternInfo in patterns) {
            val match = patternInfo.pattern.find(remaining)
            if (match != null && (earliestMatch == null || match.range.first < earliestMatch.range.first)) {
                earliestMatch = match
                matchedPatternInfo = patternInfo
            }
        }

        if (earliestMatch != null && matchedPatternInfo != null) {
            // Add text before the match (preserve some spacing)
            if (earliestMatch.range.first > 0) {
                val textBefore = remaining.substring(0, earliestMatch.range.first)
                // Collapse multiple whitespaces but preserve word boundaries
                val cleaned = textBefore.replace(Regex("\\s+"), " ")
                if (cleaned.isNotBlank()) {
                    segments.add(MathSegment(cleaned, SegmentType.TEXT))
                }
            }

            // Add the matched content with appropriate type
            val matchedContent = earliestMatch.groupValues[1].trim()
            if (matchedContent.isNotEmpty()) {
                segments.add(MathSegment(matchedContent, matchedPatternInfo.segmentType))
            }

            remaining = remaining.substring(earliestMatch.range.last + 1)
        } else {
            // No more matches, add remaining text
            val cleaned = remaining.replace(Regex("\\s+"), " ").trim()
            if (cleaned.isNotEmpty()) {
                segments.add(MathSegment(cleaned, SegmentType.TEXT))
            }
            break
        }
    }

    return segments.ifEmpty { listOf(MathSegment(text, SegmentType.TEXT)) }
}
