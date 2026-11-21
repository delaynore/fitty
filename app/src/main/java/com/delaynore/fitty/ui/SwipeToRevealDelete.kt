package com.delaynore.fitty.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
internal fun SwipeToRevealDelete(
    description: String,
    modifier: Modifier = Modifier,
    onDelete: () -> Unit,
    content: @Composable () -> Unit,
) {
    val revealWidth = 64.dp
    val revealPx = with(LocalDensity.current) { revealWidth.toPx() }
    var offsetPx by remember { mutableFloatStateOf(0f) }
    Box(modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))) {
        if (offsetPx > 0f) {
            Box(Modifier.matchParentSize(), contentAlignment = Alignment.CenterStart) {
                DeleteIconButton(description, onClick = onDelete, modifier = Modifier.width(revealWidth))
            }
        }
        Box(
            Modifier.fillMaxWidth()
                .offset { IntOffset(offsetPx.roundToInt(), 0) }
                .semantics {
                    customActions = listOf(CustomAccessibilityAction(description) {
                        onDelete()
                        true
                    })
                }
                .pointerInput(revealPx) {
                    detectHorizontalDragGestures(
                        onDragEnd = { offsetPx = if (offsetPx >= revealPx / 2) revealPx else 0f },
                        onDragCancel = { offsetPx = 0f },
                    ) { change, delta ->
                        change.consume()
                        offsetPx = (offsetPx + delta).coerceIn(0f, revealPx)
                    }
                },
        ) { content() }
    }
}

@Composable
internal fun DeleteIconButton(description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.error
    IconButton(onClick = onClick, modifier = modifier.semantics { contentDescription = description }) {
        Canvas(Modifier.size(20.dp)) {
            val unit = size.width / 20f
            val stroke = 1.6f * unit
            drawLine(color, Offset(3f * unit, 5f * unit), Offset(17f * unit, 5f * unit), stroke)
            drawLine(color, Offset(7f * unit, 2f * unit), Offset(13f * unit, 2f * unit), stroke)
            drawRect(
                color,
                topLeft = Offset(5f * unit, 5f * unit),
                size = Size(10f * unit, 13f * unit),
                style = Stroke(stroke),
            )
        }
    }
}
