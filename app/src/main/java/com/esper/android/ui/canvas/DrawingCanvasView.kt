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
import androidx.compose.ui.layout.ContentScale
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
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun DrawingCanvasView(
    rawJson: String,
    modifier: Modifier = Modifier
) {
    var renderedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var elementCount by remember { mutableIntStateOf(0) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(rawJson) {
        val (bmp, count) = withContext(Dispatchers.Default) {
            WhiteboardBitmapRenderer.render(rawJson)
        }
        renderedBitmap = bmp
        elementCount = count
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
                    scale = (scale * zoom).coerceIn(0.25f, 6.0f)
                    offset += pan
                }
            }
    ) {
        val bitmap = renderedBitmap
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Whiteboard Canvas",
                contentScale = ContentScale.Fit,
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

        // Top Status Badge
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
                    text = if (elementCount > 0) "Whiteboard · $elementCount elements · ${(scale * 100).toInt()}%" else "Whiteboard · Empty",
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
                    onClick = { scale = (scale + 0.25f).coerceAtMost(6.0f) },
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
                    onClick = { scale = (scale - 0.25f).coerceAtLeast(0.25f) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Zoom Out", tint = IndigoGlow, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

private object WhiteboardBitmapRenderer {
    private const val MIN_WIDTH = 1920
    private const val MIN_HEIGHT = 1200
    private const val PADDING = 140f

    fun render(rawJson: String): Pair<Bitmap, Int> {
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

        val hasBounds = minX < Float.MAX_VALUE && maxX > Float.MIN_VALUE
        val spanW = if (hasBounds) (maxX - minX).coerceAtLeast(200f) else 800f
        val spanH = if (hasBounds) (maxY - minY).coerceAtLeast(200f) else 600f

        val bmpWidth = maxOf(MIN_WIDTH, (spanW + PADDING * 2).toInt()).coerceAtMost(3840)
        val bmpHeight = maxOf(MIN_HEIGHT, (spanH + PADDING * 2).toInt()).coerceAtMost(2560)

        val bitmap = Bitmap.createBitmap(bmpWidth, bmpHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 1. Dark Blueprint Background
        canvas.drawColor(android.graphics.Color.parseColor("#020617"))

        // 2. Blueprint Dot Grid
        val gridPaint = Paint().apply {
            color = android.graphics.Color.parseColor("#1E293B")
            alpha = 120
            isAntiAlias = true
        }
        val step = 36f
        var gx = 0f
        while (gx < bmpWidth) {
            var gy = 0f
            while (gy < bmpHeight) {
                canvas.drawCircle(gx, gy, 1.4f, gridPaint)
                gy += step
            }
            gx += step
        }

        // Center all drawings inside the bitmap
        if (hasBounds) {
            val contentCenterX = (minX + maxX) / 2f
            val contentCenterY = (minY + maxY) / 2f
            val shiftX = (bmpWidth / 2f) - contentCenterX
            val shiftY = (bmpHeight / 2f) - contentCenterY
            canvas.translate(shiftX, shiftY)
        }

        // 3. Render Whiteboard Shapes & Paths
        elements.forEach { it.draw(canvas) }

        return Pair(bitmap, elements.size)
    }

    private fun parseElements(raw: String): List<RenderableElement> {
        var text = raw.trim()
        if (text.isEmpty() || text == "{}") return emptyList()

        // Unescape if double-encoded JSON string
        if (text.startsWith("\"") && text.endsWith("\"") && text.length > 2) {
            try {
                text = Gson().fromJson(text, String::class.java) ?: text
            } catch (_: Exception) {}
        }

        val list = mutableListOf<RenderableElement>()

        try {
            val jsonElement = JsonParser.parseString(text)
            val arr: JsonArray? = when {
                jsonElement.isJsonArray -> jsonElement.asJsonArray
                jsonElement.isJsonObject -> {
                    val obj = jsonElement.asJsonObject
                    when {
                        obj.has("elements") && obj.get("elements").isJsonArray -> obj.getAsJsonArray("elements")
                        obj.has("shapes") && obj.get("shapes").isJsonArray -> obj.getAsJsonArray("shapes")
                        obj.has("data") && obj.get("data").isJsonObject -> {
                            val d = obj.getAsJsonObject("data")
                            if (d.has("elements") && d.get("elements").isJsonArray) d.getAsJsonArray("elements") else null
                        }
                        else -> null
                    }
                }
                else -> null
            }

            arr?.forEach { elem ->
                if (!elem.isJsonObject) return@forEach
                val o = elem.asJsonObject
                val kind = (o.get("type")?.takeIf { it.isJsonPrimitive }?.asString
                    ?: o.get("tool")?.takeIf { it.isJsonPrimitive }?.asString ?: "path").lowercase()
                val hex = o.get("strokeColor")?.takeIf { it.isJsonPrimitive }?.asString
                    ?: o.get("color")?.takeIf { it.isJsonPrimitive }?.asString ?: "#818CF8"
                val strokeColor = parseColor(hex)
                val strokeW = (o.get("strokeWidth")?.takeIf { it.isJsonPrimitive }?.asFloat
                    ?: o.get("width")?.takeIf { it.isJsonPrimitive }?.asFloat ?: 3.5f).coerceAtLeast(3f)

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
                        val ptsArr = o.get("points")?.takeIf { it.isJsonArray }?.asJsonArray
                        if (ptsArr != null && ptsArr.size() >= 2) {
                            val pStart = ptsArr.first().asJsonArray
                            val pEnd = ptsArr.last().asJsonArray
                            val x1 = x + pStart[0].asFloat
                            val y1 = y + pStart[1].asFloat
                            val x2 = x + pEnd[0].asFloat
                            val y2 = y + pEnd[1].asFloat
                            list.add(ArrowElement(x1, y1, x2, y2, strokeColor, strokeW))
                        } else {
                            val w = o.get("w")?.asFloat ?: o.get("width")?.asFloat ?: 120f
                            val h = o.get("h")?.asFloat ?: o.get("height")?.asFloat ?: 60f
                            list.add(ArrowElement(x, y, x + w, y + h, strokeColor, strokeW))
                        }
                    }
                    "pencil", "freedraw", "freehand", "path" -> {
                        val pts = mutableListOf<Pair<Float, Float>>()
                        val bx = o.get("x")?.takeIf { it.isJsonPrimitive }?.asFloat ?: 0f
                        val by = o.get("y")?.takeIf { it.isJsonPrimitive }?.asFloat ?: 0f
                        val isExcalidrawRelative = o.has("type") && (bx != 0f || by != 0f)

                        val ptsArr = o.get("points")?.takeIf { it.isJsonArray }?.asJsonArray
                        ptsArr?.forEach { p ->
                            if (p.isJsonArray) {
                                val pair = p.asJsonArray
                                if (pair.size() >= 2) {
                                    val px = pair[0].asFloat
                                    val py = pair[1].asFloat
                                    pts.add(Pair(if (isExcalidrawRelative) bx + px else px, if (isExcalidrawRelative) by + py else py))
                                }
                            } else if (p.isJsonObject) {
                                val po = p.asJsonObject
                                val px = po.get("x")?.asFloat ?: 0f
                                val py = po.get("y")?.asFloat ?: 0f
                                pts.add(Pair(px, py))
                            }
                        }
                        if (pts.isNotEmpty()) list.add(PathElement(pts, strokeColor, strokeW))
                    }
                    "text" -> {
                        val textStr = o.get("text")?.takeIf { it.isJsonPrimitive }?.asString ?: "Text"
                        val x = o.get("x")?.asFloat ?: 0f
                        val y = o.get("y")?.asFloat ?: 0f
                        val size = o.get("fontSize")?.asFloat ?: 20f
                        list.add(TextElement(textStr, x, y, strokeColor, size))
                    }
                }
            }
        } catch (_: Exception) {}

        return list
    }

    private fun parseColor(hex: String): Int {
        return try {
            val clean = hex.removePrefix("#").trim()
            when (clean.length) {
                3 -> {
                    val r = clean[0]; val g = clean[1]; val b = clean[2]
                    android.graphics.Color.parseColor("#FF$r$r$g$g$b$b")
                }
                6 -> android.graphics.Color.parseColor("#FF$clean")
                8 -> android.graphics.Color.parseColor("#$clean")
                else -> android.graphics.Color.parseColor("#818CF8")
            }
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
        override val bounds = RectF(minOf(x1, x2) - 24f, minOf(y1, y2) - 24f, maxOf(x1, x2) + 24f, maxOf(y1, y2) + 24f)
        override fun draw(canvas: Canvas) {
            val paint = Paint().apply {
                color = this@ArrowElement.color
                style = Paint.Style.STROKE
                strokeWidth = this@ArrowElement.strokeWidth
                strokeCap = Paint.Cap.ROUND
                isAntiAlias = true
            }
            canvas.drawLine(x1, y1, x2, y2, paint)

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
        override val bounds = RectF(x, y - fontSize, x + text.length * fontSize * 0.65f, y + 10f)
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
