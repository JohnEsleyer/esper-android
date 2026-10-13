package com.esper.android.ui.canvas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.esper.android.ui.theme.BorderSlate
import com.esper.android.ui.theme.CosmicSlate
import com.esper.android.ui.theme.IndigoGlow
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject

data class CanvasDrawingData(
    val elements: List<CanvasElement> = emptyList()
)

sealed class CanvasElement {
    data class FreehandPath(
        val points: List<Point>,
        val color: Color,
        val strokeWidth: Float
    ) : CanvasElement()

    data class Line(
        val start: Point,
        val end: Point,
        val color: Color,
        val strokeWidth: Float
    ) : CanvasElement()

    data class Rectangle(
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float,
        val color: Color,
        val strokeWidth: Float,
        val fill: Boolean = false
    ) : CanvasElement()

    data class Circle(
        val cx: Float,
        val cy: Float,
        val radius: Float,
        val color: Color,
        val strokeWidth: Float,
        val fill: Boolean = false
    ) : CanvasElement()

    data class Label(
        val text: String,
        val x: Float,
        val y: Float,
        val color: Color,
        val fontSize: Float
    ) : CanvasElement()
}

data class Point(val x: Float, val y: Float)

object CanvasDataParser {
    private val gson = Gson()

    fun parse(rawJson: String): List<CanvasElement> {
        if (rawJson.isBlank()) return emptyList()
        val elements = mutableListOf<CanvasElement>()

        try {
            val root = gson.fromJson(rawJson, JsonObject::class.java)

            val array: JsonArray? = when {
                root.has("elements") && root.get("elements").isJsonArray -> root.getAsJsonArray("elements")
                root.has("shapes") && root.get("shapes").isJsonArray -> root.getAsJsonArray("shapes")
                else -> null
            }

            array?.forEach { elemJson ->
                if (!elemJson.isJsonObject) return@forEach
                val obj = elemJson.asJsonObject
                val type = obj.get("type")?.asString?.lowercase() ?: "path"
                val strokeHex = obj.get("color")?.asString ?: obj.get("strokeColor")?.asString ?: "#818CF8"
                val strokeColor = parseHexColor(strokeHex)
                val strokeWidth = obj.get("strokeWidth")?.asFloat ?: 3f

                when (type) {
                    "path", "freehand", "freedraw" -> {
                        val pts = mutableListOf<Point>()
                        val baseX = obj.get("x")?.asFloat ?: 0f
                        val baseY = obj.get("y")?.asFloat ?: 0f

                        val ptsArray = if (obj.has("points") && obj.get("points").isJsonArray) obj.getAsJsonArray("points") else null
                        ptsArray?.forEach { ptElem ->
                            if (ptElem.isJsonArray) {
                                val pair = ptElem.asJsonArray
                                if (pair.size() >= 2) {
                                    pts.add(Point(baseX + pair[0].asFloat, baseY + pair[1].asFloat))
                                }
                            } else if (ptElem.isJsonObject) {
                                val pObj = ptElem.asJsonObject
                                pts.add(Point(baseX + (pObj.get("x")?.asFloat ?: 0f), baseY + (pObj.get("y")?.asFloat ?: 0f)))
                            }
                        }
                        if (pts.isNotEmpty()) {
                            elements.add(CanvasElement.FreehandPath(pts, strokeColor, strokeWidth))
                        }
                    }
                    "line" -> {
                        val sx = obj.get("startX")?.asFloat ?: obj.get("x")?.asFloat ?: 0f
                        val sy = obj.get("startY")?.asFloat ?: obj.get("y")?.asFloat ?: 0f
                        val ex = obj.get("endX")?.asFloat ?: (sx + (obj.get("width")?.asFloat ?: 100f))
                        val ey = obj.get("endY")?.asFloat ?: (sy + (obj.get("height")?.asFloat ?: 100f))
                        elements.add(CanvasElement.Line(Point(sx, sy), Point(ex, ey), strokeColor, strokeWidth))
                    }
                    "rectangle", "rect" -> {
                        val x = obj.get("x")?.asFloat ?: 0f
                        val y = obj.get("y")?.asFloat ?: 0f
                        val w = obj.get("width")?.asFloat ?: 100f
                        val h = obj.get("height")?.asFloat ?: 100f
                        elements.add(CanvasElement.Rectangle(x, y, w, h, strokeColor, strokeWidth))
                    }
                    "circle", "ellipse" -> {
                        val x = obj.get("x")?.asFloat ?: 0f
                        val y = obj.get("y")?.asFloat ?: 0f
                        val w = obj.get("width")?.asFloat ?: 80f
                        elements.add(CanvasElement.Circle(x + w / 2, y + w / 2, w / 2, strokeColor, strokeWidth))
                    }
                    "text" -> {
                        val t = obj.get("text")?.asString ?: ""
                        val x = obj.get("x")?.asFloat ?: 0f
                        val y = obj.get("y")?.asFloat ?: 0f
                        val fs = obj.get("fontSize")?.asFloat ?: 16f
                        elements.add(CanvasElement.Label(t, x, y, strokeColor, fs))
                    }
                }
            }
        } catch (_: Exception) {}

        return elements
    }

    private fun parseHexColor(hex: String): Color {
        return try {
            val clean = hex.removePrefix("#")
            when (clean.length) {
                6, 8 -> Color(android.graphics.Color.parseColor("#$clean"))
                else -> Color(0xFF818CF8)
            }
        } catch (_: Exception) {
            Color(0xFF818CF8)
        }
    }
}

@Composable
fun DrawingCanvasView(
    rawJson: String,
    modifier: Modifier = Modifier
) {
    val elements = remember(rawJson) { CanvasDataParser.parse(rawJson) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF020617))
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(0.4f, 4f)
                    offset += pan
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawGrid(scale, offset)

            elements.forEach { elem ->
                when (elem) {
                    is CanvasElement.FreehandPath -> {
                        if (elem.points.size > 1) {
                            val path = Path()
                            val first = transformPoint(elem.points.first(), scale, offset)
                            path.moveTo(first.x, first.y)
                            for (i in 1 until elem.points.size) {
                                val pt = transformPoint(elem.points[i], scale, offset)
                                path.lineTo(pt.x, pt.y)
                            }
                            drawPath(
                                path = path,
                                color = elem.color,
                                style = Stroke(width = elem.strokeWidth * scale)
                            )
                        }
                    }
                    is CanvasElement.Line -> {
                        val s = transformPoint(elem.start, scale, offset)
                        val e = transformPoint(elem.end, scale, offset)
                        drawLine(
                            color = elem.color,
                            start = s,
                            end = e,
                            strokeWidth = elem.strokeWidth * scale
                        )
                    }
                    is CanvasElement.Rectangle -> {
                        val p = transformPoint(Point(elem.x, elem.y), scale, offset)
                        drawRect(
                            color = elem.color,
                            topLeft = p,
                            size = Size(elem.width * scale, elem.height * scale),
                            style = Stroke(width = elem.strokeWidth * scale)
                        )
                    }
                    is CanvasElement.Circle -> {
                        val c = transformPoint(Point(elem.cx, elem.cy), scale, offset)
                        drawCircle(
                            color = elem.color,
                            radius = elem.radius * scale,
                            center = c,
                            style = Stroke(width = elem.strokeWidth * scale)
                        )
                    }
                    is CanvasElement.Label -> {
                        val p = transformPoint(Point(elem.x, elem.y), scale, offset)
                        val paint = android.graphics.Paint().apply {
                            color = elem.color.toArgb()
                            textSize = elem.fontSize * scale
                            isAntiAlias = true
                        }
                        drawContext.canvas.nativeCanvas.drawText(elem.text, p.x, p.y, paint)
                    }
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(8.dp),
            color = CosmicSlate.copy(alpha = 0.85f),
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderSlate),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(10.dp)
        ) {
            Text(
                text = "Canvas Engine · ${elements.size} elements · ${(scale * 100).toInt()}%",
                color = IndigoGlow,
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

private fun transformPoint(p: Point, scale: Float, offset: Offset): Offset {
    return Offset(p.x * scale + offset.x, p.y * scale + offset.y)
}

private fun DrawScope.drawGrid(scale: Float, offset: Offset) {
    val step = 32f * scale
    if (step < 12f) return
    val startX = (offset.x % step)
    val startY = (offset.y % step)

    var x = startX
    while (x < size.width) {
        var y = startY
        while (y < size.height) {
            drawCircle(
                color = Color(0xFF1E293B).copy(alpha = 0.6f),
                radius = 1.2f,
                center = Offset(x, y)
            )
            y += step
        }
        x += step
    }
}
