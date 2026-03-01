package io.github.acedroidx.frp

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle

private data class AnsiStyle(
    var fg: Color? = null,
    var bg: Color? = null,
    var bold: Boolean = false,
    var italic: Boolean = false,
    var underline: Boolean = false
)

private val ansiNormalColors = mapOf(
    30 to Color(0xFF000000),
    31 to Color(0xFFCC0000),
    32 to Color(0xFF00A300),
    33 to Color(0xFFB58900),
    34 to Color(0xFF0066CC),
    35 to Color(0xFF8E44AD),
    36 to Color(0xFF008B8B),
    37 to Color(0xFFD0D0D0)
)

private val ansiBrightColors = mapOf(
    90 to Color(0xFF808080),
    91 to Color(0xFFFF5555),
    92 to Color(0xFF55FF55),
    93 to Color(0xFFFFFF55),
    94 to Color(0xFF55AAFF),
    95 to Color(0xFFFF55FF),
    96 to Color(0xFF55FFFF),
    97 to Color(0xFFFFFFFF)
)

fun parseAnsiToAnnotatedString(text: String, defaultColor: Color): AnnotatedString {
    if (!text.contains('\u001B')) {
        return AnnotatedString(text)
    }

    val style = AnsiStyle()
    return buildAnnotatedString {
        var index = 0
        var segmentStart = 0

        fun currentSpanStyle(): SpanStyle {
            return SpanStyle(
                color = style.fg ?: defaultColor,
                background = style.bg ?: Color.Unspecified,
                fontWeight = if (style.bold) FontWeight.Bold else null,
                fontStyle = if (style.italic) FontStyle.Italic else null,
                textDecoration = if (style.underline) TextDecoration.Underline else null
            )
        }

        fun appendSegment(endExclusive: Int) {
            if (endExclusive <= segmentStart) return
            val segment = text.substring(segmentStart, endExclusive)
            withStyle(currentSpanStyle()) {
                append(segment)
            }
        }

        while (index < text.length) {
            val ch = text[index]
            if (ch == '\u001B' && index + 1 < text.length && text[index + 1] == '[') {
                appendSegment(index)

                val commandEnd = text.indexOf('m', startIndex = index + 2)
                if (commandEnd == -1) {
                    withStyle(currentSpanStyle()) {
                        append(text.substring(index))
                    }
                    return@buildAnnotatedString
                }

                val rawCodes = text.substring(index + 2, commandEnd)
                val codes = rawCodes
                    .split(';')
                    .mapNotNull { it.toIntOrNull() }
                    .ifEmpty { listOf(0) }

                applyAnsiCodes(codes, style)

                index = commandEnd + 1
                segmentStart = index
            } else {
                index++
            }
        }

        appendSegment(text.length)
    }
}

private fun applyAnsiCodes(codes: List<Int>, style: AnsiStyle) {
    var i = 0
    while (i < codes.size) {
        when (val code = codes[i]) {
            0 -> {
                style.fg = null
                style.bg = null
                style.bold = false
                style.italic = false
                style.underline = false
            }
            1 -> style.bold = true
            3 -> style.italic = true
            4 -> style.underline = true
            22 -> style.bold = false
            23 -> style.italic = false
            24 -> style.underline = false
            39 -> style.fg = null
            49 -> style.bg = null

            in 30..37 -> style.fg = ansiNormalColors[code]
            in 90..97 -> style.fg = ansiBrightColors[code]

            in 40..47 -> style.bg = ansiNormalColors[code - 10]
            in 100..107 -> style.bg = ansiBrightColors[code - 10]

            38 -> {
                // 8-bit/24-bit foreground color: 38;5;n or 38;2;r;g;b
                val result = parseExtendedColor(codes, i)
                if (result != null) {
                    style.fg = result.color
                    i = result.newIndex
                }
            }
            48 -> {
                // 8-bit/24-bit background color: 48;5;n or 48;2;r;g;b
                val result = parseExtendedColor(codes, i)
                if (result != null) {
                    style.bg = result.color
                    i = result.newIndex
                }
            }
        }
        i++
    }
}

private data class ExtendedColorResult(val color: Color, val newIndex: Int)

private fun parseExtendedColor(codes: List<Int>, startIndex: Int): ExtendedColorResult? {
    if (startIndex + 1 >= codes.size) return null
    return when (codes[startIndex + 1]) {
        5 -> {
            if (startIndex + 2 >= codes.size) return null
            val color = ansi256ToColor(codes[startIndex + 2])
            ExtendedColorResult(color, startIndex + 2)
        }
        2 -> {
            if (startIndex + 4 >= codes.size) return null
            val r = codes[startIndex + 2].coerceIn(0, 255)
            val g = codes[startIndex + 3].coerceIn(0, 255)
            val b = codes[startIndex + 4].coerceIn(0, 255)
            ExtendedColorResult(Color(r, g, b), startIndex + 4)
        }
        else -> null
    }
}

private fun ansi256ToColor(code: Int): Color {
    val c = code.coerceIn(0, 255)
    if (c < 16) {
        val map = mapOf(
            0 to Color(0xFF000000),
            1 to Color(0xFF800000),
            2 to Color(0xFF008000),
            3 to Color(0xFF808000),
            4 to Color(0xFF000080),
            5 to Color(0xFF800080),
            6 to Color(0xFF008080),
            7 to Color(0xFFC0C0C0),
            8 to Color(0xFF808080),
            9 to Color(0xFFFF0000),
            10 to Color(0xFF00FF00),
            11 to Color(0xFFFFFF00),
            12 to Color(0xFF0000FF),
            13 to Color(0xFFFF00FF),
            14 to Color(0xFF00FFFF),
            15 to Color(0xFFFFFFFF)
        )
        return map[c] ?: Color.Unspecified
    }

    if (c in 16..231) {
        val value = c - 16
        val r = value / 36
        val g = (value % 36) / 6
        val b = value % 6
        fun component(level: Int): Int = if (level == 0) 0 else 55 + level * 40
        return Color(component(r), component(g), component(b))
    }

    val gray = 8 + (c - 232) * 10
    return Color(gray.coerceIn(0, 255), gray.coerceIn(0, 255), gray.coerceIn(0, 255))
}
