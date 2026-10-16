package com.esper.android.ui.canvas

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.esper.android.ui.theme.BorderSlate
import com.esper.android.ui.theme.CosmicSlate
import com.esper.android.ui.theme.IndigoAccent
import com.esper.android.ui.theme.IndigoGlow
import com.esper.android.ui.theme.SpaceBlack
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Android Read-Only Whiteboard:
 * Rasterizes Excalidraw/vector elements into an offscreen high-DPI Bitmap once.
 * Touch gestures pan and zoom the pre-rendered image smoothly at 60/120fps.
 */
@Composable
fun DrawingCanvasView(
    rawJson: String,
    modifier: Modifier = Modifier
) {
    var renderedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(rawJson) {
        val bmp = withContext(Dispatchers.Default) {
            WhiteboardBitmapRenderer.render(rawJson)
        }
        renderedBitmap = bmp
        scale = 1f
        offset = Offset.Zero
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SpaceBlack)
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        scale = 1f
                        offset = Offset.Zero
                    }
                )
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(0.3f, 5.0f)
                    offset += pan
                }
            }
    ) {
        val bitmap = renderedBitmap
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Whiteboard Canvas",
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    }
            )
        }

        // Top Status Badge: Read-Only Whiteboard
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = CosmicSlate.copy(alpha = 0.85f),
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderSlate),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(IndigoAccent)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Whiteboard · Read Only · ${(scale * 100).toInt()}%",
                    color = IndigoGlow,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Floating Zoom / Pan Reset Controls
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = CosmicSlate.copy(alpha = 0.9f),
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderSlate),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { scale = (scale + 0.25f).coerceAtMost(5.0f) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Zoom In", tint = IndigoGlow, modifier = Modifier.size(16.dp))
                }
                IconButton(
                    onClick = {
                        scale = 1f
                        offset = Offset.Zero
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.CenterFocusStrong, contentDescription = "Reset View", tint = Color.White, modifier = Modifier.size(16.dp))
                }
                IconButton(
                    onClick = { scale = (scale - 0.25f).coerceAtLeast(0.3f) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Zoom Out", tint = IndigoGlow, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

private object WhiteboardBitmapRenderer {
    private const val DEFAULT_WIDTH = 2048
    private const val DEFAULT_HEIGHT = 1440
    private const val PADDING = 100f

    fun render(rawJson: String): Bitmap {
        val elements = parseElements(rawJson)

        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE

        elements.forEach { el ->
            minX = minOf(minX, el.bounds.left)
            minY = minOf(minY, el.bounds.top)
            maxX = maxOf(maxX, el.bounds.right)
            maxY = maxOf(maxY, el.bounds.bottom)
        }

        val contentWidth = if (maxX > minX) (maxX - minX + PADDING * 2).toInt() else DEFAULT_WIDTH
        val contentHeight = if (maxY > minY) (maxY - minY + PADDING * 2).toInt() else DEFAULT_HEIGHT

        val bmpWidth = contentWidth.coerceIn(1200, 3200)
        val bmpHeight = contentHeight.coerceIn(800, 2400)

        val bitmap = Bitmap.createBitmap(bmpWidth, bmpHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 1. Dark Blueprint Background
        canvas.drawColor(android.graphics.Color.parseColor("#020617"))

        // 2. Blueprint Dot Grid
        val gridPaint = Paint().apply {
            color = android.graphics.Color.parseColor("#1E293B")
            alpha = 110
            isAntiAlias = true
        }
        val step = 40f
        var gx = 0f
        while (gx < bmpWidth) {
            var gy = 0f
            while (gy < bmpHeight) {
                canvas.drawCircle(gx, gy, 1.4f, gridPaint)
                gy += step
            }
            gx += step
        }

        // Center elements if bounding box is smaller than canvas
        val shiftX = if (minX < Float.MAX_VALUE) PADDING - minX else 0f
        val shiftY = if (minY < Float.MAX_VALUE) PADDING - minY else 0f
        canvas.translate(shiftX, shiftY)

        // 3. Render Whiteboard Shapes & Paths
        elements.forEach { it.draw(canvas) }

        return bitmap
    }

    private fun parseElements(raw: String): List<RenderableElement> {
        if (raw.isBlank()) return emptyList()
        val list = mutableListOf<RenderableElement>()

        try {
            val root = Gson().fromJson(raw, JsonObject::class.java)
            val arr: JsonArray? = when {
                root.has("elements") && root.get("elements").isJsonArray -> root.getAsJsonArray("elements")
                root.has("shapes") && root.get("shapes").isJsonArray -> root.getAsJsonArray("shapes")
                else -> null
            }

            arr?.forEach { elem ->
                if (!elem.isJsonObject) return@forEach
                val o = elem.asJsonObject
                val kind = (o.get("type")?.asString ?: o.get("tool")?.asString ?: "path").lowercase()
                val hex = o.get("strokeColor")?.asString ?: o.get("color")?.asString ?: "#818CF8"
                val strokeColor = parseColor(hex)
                val strokeW = o.get("strokeWidth")?.asFloat ?: o.get("width")?.asFloat ?: 3.5f

                when (kind) {
                    "rect", "rectangle" -> {
                        val x = o.get("x")?.asFloat ?: 0f
                        val y = o.get("y")?.asFloat ?: 0f
                        val w = o.get("w")?.asFloat ?: o.get("width")?.asFloat ?: 100f
                        val h = o.get("h")?.asFloat ?: o.get("height")?.asFloat ?: 100f
                        list.add(RectElement(x, y, w, h, strokeColor, strokeW))
                    }
                    "circle", "ellipse" -> {
                        val x = o.get("x")?.asFloat ?: 0f
                        val y = o.get("y")?.asFloat ?: 0f
                        val w = o.get("w")?.asFloat ?: o.get("width")?.asFloat ?: 100f
                        val h = o.get("h")?.asFloat ?: o.get("height")?.asFloat ?: 100f
                        list.add(CircleElement(x, y, w, h, strokeColor, strokeW))
                    }
                    "arrow", "line" -> {
                        val x = o.get("x")?.asFloat ?: 0f
                        val y = o.get("y")?.asFloat ?: 0f
                        val w = o.get("w")?.asFloat ?: o.get("width")?.asFloat ?: 120f
                        val h = o.get("h")?.asFloat ?: o.get("height")?.asFloat ?: 60f
                        list.add(ArrowElement(x, y, x + w, y + h, strokeColor, strokeW))
                    }
                    "pencil", "freedraw", "freehand", "path" -> {
                        val pts = mutableListOf<Pair<Float, Float>>()
                        val bx = o.get("x")?.asFloat ?: 0f
                        val by = o.get("y")?.asFloat ?: 0f
                        val ptsArr = o.get("points")?.takeIf { it.isJsonArray }?.asJsonArray
                        ptsArr?.forEach { p ->
                            if (p.isJsonArray) {
                                val pair = p.asJsonArray
                                if (pair.size() >= 2) pts.add(Pair(bx + pair[0].asFloat, by + pair[1].asFloat))
                            } else if (p.isJsonObject) {
                                val po = p.asJsonObject
                                pts.add(Pair(bx + (po.get("x")?.asFloat ?: 0f), by + (po.get("y")?.asFloat ?: 0f)))
                            }
                        }
                        if (pts.isNotEmpty()) list.add(PathElement(pts, strokeColor, strokeW))
                    }
                    "text" -> {
                        val text = o.get("text")?.asString ?: "Text"
                        val x = o.get("x")?.asFloat ?: 0f
                        val y = o.get("y")?.asFloat ?: 0f
                        val size = o.get("fontSize")?.asFloat ?: 18f
                        list.add(TextElement(text, x, y, strokeColor, size))
                    }
                }
            }
        } catch (_: Exception) {}

        return list
    }

    private fun parseColor(hex: String): Int {
        return try {
            val clean = hex.removePrefix("#")
            android.graphics.Color.parseColor("#$clean")
        } catch (_: Exception) {
            android.graphics.Color.parseColor("#818CF8")
        }
    }

    private interface RenderableElement {
        val bounds: RectF
        fun draw(canvas: Canvas)
    }

    private class RectElement(
        val x: Float, val y: Float, val w: Float, val h: Float,
        val color: Int, val strokeWidth: Float
    ) : RenderableElement {
        override val bounds = RectF(minOf(x, x + w), minOf(y, y + h), maxOf(x, x + w), maxOf(y, y + h))
        override fun draw(canvas: Canvas) {
            val paint = Paint().apply {
                color = this@RectElement.color
                style = Paint.Style.STROKE
                strokeWidth = this@RectElement.strokeWidth
                isAntiAlias = true
            }
            canvas.drawRoundRect(bounds, 8f, 8f, paint)
        }
    }

    private class CircleElement(
        val x: Float, val y: Float, val w: Float, val h: Float,
        val color: Int, val strokeWidth: Float
    ) : RenderableElement {
        override val bounds = RectF(minOf(x, x + w), minOf(y, y + h), maxOf(x, x + w), maxOf(y, y + h))
        override fun draw(canvas: Canvas) {
            val paint = Paint().apply {
                color = this@CircleElement.color
                style = Paint.Style.STROKE
                strokeWidth = this@CircleElement.strokeWidth
                isAntiAlias = true
            }
            canvas.drawOval(bounds, paint)
        }
    }

    private class ArrowElement(
        val x1: Float, val y1: Float, val x2: Float, val y2: Float,
        val color: Int, val strokeWidth: Float
    ) : RenderableElement {
        override val bounds = RectF(minOf(x1, x2) - 20f, minOf(y1, y2) - 20f, maxOf(x1, x2) + 20f, maxOf(y1, y2) + 20f)
        override fun draw(canvas: Canvas) {
            val paint = Paint().apply {
                color = this@ArrowElement.color
                style = Paint.Style.STROKE
                strokeWidth = this@ArrowElement.strokeWidth
                strokeCap = Paint.Cap.ROUND
                isAntiAlias = true
            }
            canvas.drawLine(x1, y1, x2, y2, paint)

            // Sharp arrow tip
            val angle = atan2((y2 - y1).toDouble(), (x2 - x1).toDouble())
            val headLen = 22f
            val path = android.graphics.Path()
            path.moveTo(x2, y2)
            path.lineTo(
                (x2 - headLen * cos(angle - Math.PI / 6)).toFloat(),
                (y2 - headLen * sin(angle - Math.PI / 6)).toFloat()
            )
            path.moveTo(x2, y2)
            path.lineTo(
                (x2 - headLen * cos(angle + Math.PI / 6)).toFloat(),
                (y2 - headLen * sin(angle + Math.PI / 6)).toFloat()
            )
            canvas.drawPath(path, paint)
        }
    }

    private class PathElement(
        val points: List<Pair<Float, Float>>,
        val color: Int, val strokeWidth: Float
    ) : RenderableElement {
        override val bounds: RectF = RectF().apply {
            if (points.isNotEmpty()) {
                left = points.minOf { it.first }
                top = points.minOf { it.second }
                right = points.maxOf { it.first }
                bottom = points.maxOf { it.second }
            }
        }

        override fun draw(canvas: Canvas) {
            if (points.size < 2) return
            val paint = Paint().apply {
                color = this@PathElement.color
                style = Paint.Style.STROKE
                strokeWidth = this@PathElement.strokeWidth
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                isAntiAlias = true
            }
            val path = android.graphics.Path()
            path.moveTo(points[0].first, points[0].second)
            for (i in 1 until points.size) {
                path.lineTo(points[i].first, points[i].second)
            }
            canvas.drawPath(path, paint)
        }
    }

    private class TextElement(
        val text: String, val x: Float, val y: Float,
        val color: Int, val fontSize: Float
    ) : RenderableElement {
        override val bounds = RectF(x, y - fontSize, x + text.length * fontSize * 0.6f, y + 10f)
        override fun draw(canvas: Canvas) {
            val paint = Paint().apply {
                color = this@TextElement.color
                textSize = this@TextElement.fontSize
                isAntiAlias = true
                typeface = android.graphics.Typeface.SANS_SERIF
            }
            canvas.drawText(text, x, y, paint)
        }
    }
}
