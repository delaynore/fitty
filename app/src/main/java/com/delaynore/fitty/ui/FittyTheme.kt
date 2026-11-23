package com.delaynore.fitty.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val FittyColors = darkColorScheme(
    primary = Color(0xFFC5FA55),
    onPrimary = Color(0xFF1A2506),
    primaryContainer = Color(0xFF303E1B),
    onPrimaryContainer = Color(0xFFDDFF98),
    background = Color(0xFF0D100D),
    onBackground = Color(0xFFF0F3EA),
    surface = Color(0xFF141914),
    onSurface = Color(0xFFF0F3EA),
    surfaceVariant = Color(0xFF252D24),
    onSurfaceVariant = Color(0xFFB6C1B3),
    surfaceContainer = Color(0xFF1C231C),
    outline = Color(0xFF81907D),
    outlineVariant = Color(0xFF343E33),
)

@Composable
internal fun FittyTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = FittyColors, content = content)
}

@Composable
internal fun ScreenColumn(
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier.fillMaxSize().verticalScroll(scrollState).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content,
    )
}

@Composable
internal fun PageHeader(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: (@Composable () -> Unit)? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = "Назад" }) {
                FittyGlyph(FittyIcon.Back)
            }
        }
        val titleModifier = if (actions == null) Modifier else Modifier.weight(1f)
        Text(title, modifier = titleModifier, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        actions?.invoke()
    }
}

internal enum class FittyIcon { Home, History, Settings, Back, Check, Stopwatch, Stop }

@Composable
internal fun FittyGlyph(icon: FittyIcon) {
    val color = LocalContentColor.current
    Canvas(Modifier.size(22.dp)) {
        val scale = size.width / 24
        val stroke = Stroke(1.8f * scale, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val path = Path()
        when (icon) {
            FittyIcon.Home -> {
                path.moveTo(3 * scale, 11 * scale)
                path.lineTo(12 * scale, 3 * scale)
                path.lineTo(21 * scale, 11 * scale)
                path.lineTo(21 * scale, 20 * scale)
                path.lineTo(3 * scale, 20 * scale)
                path.close()
                path.moveTo(9 * scale, 20 * scale)
                path.lineTo(9 * scale, 14 * scale)
                path.lineTo(15 * scale, 14 * scale)
                path.lineTo(15 * scale, 20 * scale)
            }
            FittyIcon.History -> {
                drawCircle(color, radius = 9 * scale, center = Offset(12 * scale, 12 * scale), style = stroke)
                path.moveTo(12 * scale, 7 * scale)
                path.lineTo(12 * scale, 12 * scale)
                path.lineTo(16 * scale, 14 * scale)
            }
            FittyIcon.Settings -> {
                drawLine(color, Offset(4 * scale, 7 * scale), Offset(20 * scale, 7 * scale), stroke.width, StrokeCap.Round)
                drawLine(color, Offset(4 * scale, 12 * scale), Offset(20 * scale, 12 * scale), stroke.width, StrokeCap.Round)
                drawLine(color, Offset(4 * scale, 17 * scale), Offset(20 * scale, 17 * scale), stroke.width, StrokeCap.Round)
                path.moveTo(9 * scale, 5 * scale)
                path.lineTo(9 * scale, 9 * scale)
                path.moveTo(15 * scale, 10 * scale)
                path.lineTo(15 * scale, 14 * scale)
                path.moveTo(9 * scale, 15 * scale)
                path.lineTo(9 * scale, 19 * scale)
            }
            FittyIcon.Back -> {
                path.moveTo(13 * scale, 5 * scale)
                path.lineTo(6 * scale, 12 * scale)
                path.lineTo(13 * scale, 19 * scale)
                path.moveTo(6 * scale, 12 * scale)
                path.lineTo(21 * scale, 12 * scale)
            }
            FittyIcon.Check -> {
                path.moveTo(5 * scale, 12 * scale)
                path.lineTo(10 * scale, 17 * scale)
                path.lineTo(19 * scale, 7 * scale)
            }
            FittyIcon.Stopwatch -> {
                drawCircle(color, radius = 7.5f * scale, center = Offset(12 * scale, 13.5f * scale), style = stroke)
                path.moveTo(10 * scale, 3 * scale)
                path.lineTo(14 * scale, 3 * scale)
                path.moveTo(12 * scale, 3 * scale)
                path.lineTo(12 * scale, 6 * scale)
                path.moveTo(12 * scale, 10 * scale)
                path.lineTo(12 * scale, 14 * scale)
                path.lineTo(15 * scale, 14 * scale)
            }
            FittyIcon.Stop -> drawRect(color, topLeft = Offset(7 * scale, 7 * scale), size = Size(10 * scale, 10 * scale))
        }
        drawPath(path, color, style = stroke)
    }
}
