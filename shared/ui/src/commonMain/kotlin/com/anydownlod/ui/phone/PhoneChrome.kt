package com.anydownlod.ui.phone

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anydownlod.ui.generated.resources.Res
import com.anydownlod.ui.generated.resources.download
import org.jetbrains.compose.resources.stringResource

internal data class PhoneColors(
    val accent: Color,
    val ink: Color,
    val muted: Color,
    val card: Color,
    val field: Color,
    val track: Color,
    val canvas: Color,
    val failed: Color,
    val onAccent: Color,
    val well: Color,
)

private val LightPhoneColors = PhoneColors(
    accent = Color(0xFF005FCC),
    ink = Color(0xFF1D1D1F),
    muted = Color(0xFF6E6E73),
    card = Color.White,
    field = Color.White,
    track = Color(0xFFE6E8EE),
    canvas = Color(0xFFF4F5F7),
    failed = Color(0xFFC41C1C),
    onAccent = Color.White,
    well = Color(0xFFEBEDF2),
)

private val DarkPhoneColors = PhoneColors(
    accent = Color(0xFF6AADFF),
    ink = Color(0xFFF5F5F7),
    muted = Color(0xFFAEAEB2),
    card = Color(0xFF232326),
    field = Color(0xFF2C2C31),
    track = Color(0xFF3A3A40),
    canvas = Color(0xFF141416),
    failed = Color(0xFFFF8A80),
    onAccent = Color(0xFF04243F),
    well = Color(0xFF1C1C1E),
)

@Composable
internal fun phoneColors(): PhoneColors {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return if (dark) DarkPhoneColors else LightPhoneColors
}

@Composable
internal fun phoneCanvas(colors: PhoneColors): Color = colors.canvas

@Composable
internal fun PhoneDownloadButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tag: String = "add-download-button",
    colors: PhoneColors = phoneColors(),
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(54.dp).testTag(tag),
        shape = RoundedCornerShape(28.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.accent,
            contentColor = colors.onAccent,
        ),
    ) {
        Text(
            text = stringResource(Res.string.download),
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
        )
    }
}

@Composable
internal fun PhoneIconWell(
    onClick: (() -> Unit)?,
    tag: String? = null,
    background: Color,
    size: Dp = 42.dp,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .then(
                if (onClick != null) {
                    Modifier.clickable(role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .then(if (tag != null) Modifier.testTag(tag) else Modifier),
        contentAlignment = Alignment.Center,
        content = { content() },
    )
}

@Composable
internal fun Glyph(
    modifier: Modifier,
    draw: DrawScope.() -> Unit,
) {
    Canvas(modifier, onDraw = draw)
}

internal fun DrawScope.drawChevronLeft(color: Color) {
    val stroke = Stroke(
        width = size.minDimension * 0.14f,
        cap = StrokeCap.Round,
        join = StrokeJoin.Round,
    )
    val path = Path().apply {
        moveTo(size.width * 0.62f, size.height * 0.22f)
        lineTo(size.width * 0.34f, size.height * 0.50f)
        lineTo(size.width * 0.62f, size.height * 0.78f)
    }
    drawPath(path, color, style = stroke)
}

internal fun DrawScope.drawPlay(color: Color) {
    val path = Path().apply {
        moveTo(size.width * 0.36f, size.height * 0.22f)
        lineTo(size.width * 0.36f, size.height * 0.78f)
        lineTo(size.width * 0.78f, size.height * 0.50f)
        close()
    }
    drawPath(path, color)
}

internal fun DrawScope.drawDownload(color: Color) {
    val stroke = Stroke(width = size.minDimension * 0.1f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val shaft = Path().apply {
        moveTo(size.width * 0.5f, size.height * 0.16f)
        lineTo(size.width * 0.5f, size.height * 0.62f)
    }
    drawPath(shaft, color, style = stroke)
    val head = Path().apply {
        moveTo(size.width * 0.28f, size.height * 0.46f)
        lineTo(size.width * 0.5f, size.height * 0.68f)
        lineTo(size.width * 0.72f, size.height * 0.46f)
    }
    drawPath(head, color, style = stroke)
    drawLine(
        color = color,
        start = Offset(size.width * 0.24f, size.height * 0.82f),
        end = Offset(size.width * 0.76f, size.height * 0.82f),
        strokeWidth = size.minDimension * 0.1f,
        cap = StrokeCap.Round,
    )
}

internal fun DrawScope.drawShare(color: Color) {
    val r = size.minDimension * 0.09f
    val points = listOf(
        Offset(size.width * 0.72f, size.height * 0.28f),
        Offset(size.width * 0.28f, size.height * 0.46f),
        Offset(size.width * 0.72f, size.height * 0.74f),
    )
    val stroke = size.minDimension * 0.07f
    drawLine(color, points[0], points[1], stroke, StrokeCap.Round)
    drawLine(color, points[1], points[2], stroke, StrokeCap.Round)
    points.forEach { center -> drawCircle(color, r, center) }
}

internal fun DrawScope.drawLink(color: Color) {
    val stroke = Stroke(width = size.minDimension * 0.1f, cap = StrokeCap.Round)
    drawCircle(color, size.minDimension * 0.16f, Offset(size.width * 0.36f, size.height * 0.62f), style = stroke)
    drawCircle(color, size.minDimension * 0.16f, Offset(size.width * 0.64f, size.height * 0.38f), style = stroke)
    drawLine(
        color,
        Offset(size.width * 0.42f, size.height * 0.56f),
        Offset(size.width * 0.58f, size.height * 0.44f),
        strokeWidth = size.minDimension * 0.1f,
        cap = StrokeCap.Round,
    )
}

internal fun DrawScope.drawBell(color: Color) {
    val stroke = Stroke(width = size.minDimension * 0.09f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val body = Path().apply {
        moveTo(size.width * 0.5f, size.height * 0.16f)
        lineTo(size.width * 0.28f, size.height * 0.42f)
        lineTo(size.width * 0.28f, size.height * 0.62f)
        lineTo(size.width * 0.18f, size.height * 0.72f)
        lineTo(size.width * 0.82f, size.height * 0.72f)
        lineTo(size.width * 0.72f, size.height * 0.62f)
        lineTo(size.width * 0.72f, size.height * 0.42f)
        close()
    }
    drawPath(body, color, style = stroke)
    drawCircle(color, size.minDimension * 0.07f, Offset(size.width * 0.5f, size.height * 0.82f))
}

internal fun DrawScope.drawRefresh(color: Color) {
    val stroke = Stroke(width = size.minDimension * 0.12f, cap = StrokeCap.Round)
    drawArc(
        color = color,
        startAngle = 40f,
        sweepAngle = 280f,
        useCenter = false,
        style = stroke,
        topLeft = Offset(size.width * 0.16f, size.height * 0.16f),
        size = Size(size.width * 0.68f, size.height * 0.68f),
    )
    val head = Path().apply {
        moveTo(size.width * 0.78f, size.height * 0.18f)
        lineTo(size.width * 0.78f, size.height * 0.40f)
        lineTo(size.width * 0.56f, size.height * 0.30f)
        close()
    }
    drawPath(head, color)
}
