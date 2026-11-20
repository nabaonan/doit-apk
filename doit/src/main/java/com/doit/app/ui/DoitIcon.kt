package com.doit.app.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * 线性图标：全部按 24×24 的设计网格手绘，线宽圆头，
 * 与视觉稿里的 SVG 图标保持同一套语汇（圆、圆角、细线）。
 */
enum class DoitIconKind {
    Plus, Mic, Gear, FolderPlus, Heat, Flag, Folder, Sub, Trash, Palette, Back, Tick, Grid, Close, Chevron, Calendar, Pencil, CloudSync, CloudSyncOk,
}

@Composable
fun DoitIcon(
    kind: DoitIconKind,
    size: Dp,
    tint: Color,
    stroke: Dp = 1.7.dp,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.size(size)) {
        val u = this.size.minDimension / 24f
        val w = stroke.toPx()
        drawDoitIcon(kind, tint, w, u)
    }
}

private fun DrawScope.drawDoitIcon(kind: DoitIconKind, c: Color, w: Float, u: Float) {
    fun line(x1: Float, y1: Float, x2: Float, y2: Float, width: Float = w) =
        drawLine(c, Offset(x1 * u, y1 * u), Offset(x2 * u, y2 * u), width, StrokeCap.Round)

    val stroke = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round)

    when (kind) {
        DoitIconKind.Plus -> {
            line(12f, 5f, 12f, 19f); line(5f, 12f, 19f, 12f)
        }

        DoitIconKind.Close -> {
            line(6.5f, 6.5f, 17.5f, 17.5f); line(17.5f, 6.5f, 6.5f, 17.5f)
        }

        DoitIconKind.Mic -> {
            drawRoundRect(
                color = c,
                topLeft = Offset(9f * u, 2.5f * u),
                size = Size(6f * u, 11f * u),
                cornerRadius = CornerRadius(3f * u, 3f * u),
                style = stroke,
            )
            drawArc(
                color = c,
                startAngle = 0f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(5.5f * u, 3.5f * u),
                size = Size(13f * u, 13f * u),
                style = stroke,
            )
            line(12f, 16.5f, 12f, 21f)
            line(8.5f, 21f, 15.5f, 21f)
        }

        DoitIconKind.Gear -> {
            // 真正的齿轮：8 个梯形齿的闭合外轮廓 + 中间轴孔（原来那版是圆形加八根辐条，更像太阳）
            val teeth = 8
            val rOut = 9.6f
            val rIn = 7.0f
            val pitch = 2.0 * Math.PI / teeth
            val path = Path()
            for (t in 0 until teeth) {
                val base = -Math.PI / 2 + t * pitch
                val points = listOf(0.08f to rOut, 0.36f to rOut, 0.50f to rIn, 0.78f to rIn)
                points.forEachIndexed { idx, (f, r) ->
                    val a = (base + f * pitch).toFloat()
                    val x = (12f + cos(a) * r) * u
                    val y = (12f + sin(a) * r) * u
                    if (t == 0 && idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
            }
            path.close()
            drawPath(path, c, style = stroke)
            drawCircle(c, radius = 3f * u, center = Offset(12f * u, 12f * u), style = stroke)
        }

        DoitIconKind.FolderPlus -> {
            val p = Path().apply {
                moveTo(3.2f * u, 9.6f * u)
                lineTo(3.2f * u, 7.6f * u)
                quadraticBezierTo(3.2f * u, 5.6f * u, 5.2f * u, 5.6f * u)
                lineTo(8.8f * u, 5.6f * u)
                lineTo(10.8f * u, 7.9f * u)
                lineTo(18.7f * u, 7.9f * u)
                quadraticBezierTo(20.7f * u, 7.9f * u, 20.7f * u, 9.9f * u)
                lineTo(20.7f * u, 17f * u)
                quadraticBezierTo(20.7f * u, 19f * u, 18.7f * u, 19f * u)
                lineTo(5.2f * u, 19f * u)
                quadraticBezierTo(3.2f * u, 19f * u, 3.2f * u, 17f * u)
                close()
            }
            drawPath(p, c, style = stroke)
            line(12f, 11.2f, 12f, 15.6f)
            line(9.8f, 13.4f, 14.2f, 13.4f)
        }

        DoitIconKind.Folder -> {
            val p = Path().apply {
                moveTo(3.2f * u, 9.6f * u)
                lineTo(3.2f * u, 7.6f * u)
                quadraticBezierTo(3.2f * u, 5.6f * u, 5.2f * u, 5.6f * u)
                lineTo(8.8f * u, 5.6f * u)
                lineTo(10.8f * u, 7.9f * u)
                lineTo(18.7f * u, 7.9f * u)
                quadraticBezierTo(20.7f * u, 7.9f * u, 20.7f * u, 9.9f * u)
                lineTo(20.7f * u, 17f * u)
                quadraticBezierTo(20.7f * u, 19f * u, 18.7f * u, 19f * u)
                lineTo(5.2f * u, 19f * u)
                quadraticBezierTo(3.2f * u, 19f * u, 3.2f * u, 17f * u)
                close()
            }
            drawPath(p, c, style = stroke)
        }

        DoitIconKind.Heat -> {
            val alphas = listOf(.28f, .62f, .28f, .62f, 1f, .45f, .2f, .45f, .2f)
            var i = 0
            for (row in 0 until 3) for (col in 0 until 3) {
                drawRoundRect(
                    color = c.copy(alpha = alphas[i]),
                    topLeft = Offset((3f + col * 6.4f) * u, (3f + row * 6.4f) * u),
                    size = Size(5.2f * u, 5.2f * u),
                    cornerRadius = CornerRadius(1.5f * u, 1.5f * u),
                )
                i++
            }
        }

        DoitIconKind.Flag -> {
            line(5.5f, 21f, 5.5f, 4f)
            val p = Path().apply {
                moveTo(5.5f * u, 4.9f * u)
                cubicTo(8.8f * u, 3.2f * u, 11.9f * u, 6.5f * u, 15.3f * u, 4.9f * u)
                lineTo(15.3f * u, 12.5f * u)
                cubicTo(11.9f * u, 14.1f * u, 8.8f * u, 10.8f * u, 5.5f * u, 12.5f * u)
                close()
            }
            drawPath(p, c, style = stroke)
        }

        DoitIconKind.Sub -> {
            line(4f, 6.5f, 12f, 6.5f)
            line(4f, 12f, 10f, 12f)
            line(4f, 17.5f, 12f, 17.5f)
            line(17f, 10.5f, 17f, 17.5f)
            line(13.5f, 14f, 20.5f, 14f)
        }

        DoitIconKind.Trash -> {
            line(4.5f, 6.5f, 19.5f, 6.5f)
            line(9.5f, 6.5f, 9.5f, 5f)
            drawArc(
                c, 180f, 180f, false,
                topLeft = Offset(10f * u, 3.5f * u), size = Size(4f * u, 3f * u), style = stroke,
            )
            line(14.5f, 6.5f, 14.5f, 5f)
            val p = Path().apply {
                moveTo(6.5f * u, 6.5f * u)
                lineTo(7.5f * u, 18.5f * u)
                quadraticBezierTo(7.6f * u, 20.4f * u, 9.5f * u, 20.4f * u)
                lineTo(14.5f * u, 20.4f * u)
                quadraticBezierTo(16.4f * u, 20.4f * u, 16.5f * u, 18.5f * u)
                lineTo(17.5f * u, 6.5f * u)
            }
            drawPath(p, c, style = stroke)
            line(10f, 10.5f, 10f, 16.5f)
            line(14f, 10.5f, 14f, 16.5f)
        }

        DoitIconKind.Palette -> {
            drawCircle(c, radius = 8.6f * u, center = Offset(12f * u, 12f * u), style = stroke)
            drawCircle(c, radius = 1.1f * u, center = Offset(8.6f * u, 9.4f * u))
            drawCircle(c, radius = 1.1f * u, center = Offset(12f * u, 7.6f * u))
            drawCircle(c, radius = 1.1f * u, center = Offset(15.4f * u, 9.4f * u))
            val p = Path().apply {
                moveTo(12f * u, 20.6f * u)
                cubicTo(10.2f * u, 20.6f * u, 9.6f * u, 19.1f * u, 10.4f * u, 17.9f * u)
                cubicTo(11.1f * u, 16.9f * u, 10.7f * u, 15.6f * u, 9.2f * u, 15.6f * u)
                lineTo(7.5f * u, 15.6f * u)
            }
            drawPath(p, c, style = stroke)
        }

        DoitIconKind.Back -> {
            line(14.5f, 5f, 7.5f, 12f)
            line(7.5f, 12f, 14.5f, 19f)
        }

        DoitIconKind.Chevron -> {
            line(5.5f, 9.5f, 12f, 16f)
            line(12f, 16f, 18.5f, 9.5f)
        }

        DoitIconKind.Tick -> {
            line(2.4f, 6.4f, 5f, 9f)
            line(5f, 9f, 9.8f, 3.4f)
        }

        DoitIconKind.Grid -> {
            for (row in 0 until 2) for (col in 0 until 2) {
                drawRoundRect(
                    color = c,
                    topLeft = Offset((3f + col * 10.5f) * u, (3f + row * 10.5f) * u),
                    size = Size(7.5f * u, 7.5f * u),
                    cornerRadius = CornerRadius(1.6f * u, 1.6f * u),
                    style = Stroke(width = w * 0.95f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
        }

        DoitIconKind.Calendar -> {
            // 月视图：圆角外框 + 顶部两个装订环 + 头部分隔线，线框风格与全套图标一致
            val p = Path().apply {
                moveTo(4.5f * u, 8f * u)
                lineTo(4.5f * u, 6.4f * u)
                quadraticBezierTo(4.5f * u, 5f * u, 6f * u, 5f * u)
                lineTo(18f * u, 5f * u)
                quadraticBezierTo(19.5f * u, 5f * u, 19.5f * u, 6.4f * u)
                lineTo(19.5f * u, 8f * u)
            }
            drawPath(p, c, style = stroke)
            drawRoundRect(
                color = c,
                topLeft = Offset(4.5f * u, 8f * u),
                size = Size(15f * u, 11f * u),
                cornerRadius = CornerRadius(2.4f * u, 2.4f * u),
                style = stroke,
            )
            line(8f, 3.2f, 8f, 6f)
            line(16f, 3.2f, 16f, 6f)
            line(4.5f, 11.2f, 19.5f, 11.2f)
        }

        DoitIconKind.Pencil -> {
            // 编辑：一支斜放的铅笔——两条平行笔身 + 右上擦头 + 左下收尖
            line(7f, 17f, 17f, 7f)       // 笔身左缘
            line(9.2f, 15.2f, 19.2f, 5.2f) // 笔身右缘（平行）
            line(7f, 17f, 4.2f, 19.8f)   // 笔尖（左下收尖）
            line(17f, 7f, 19.8f, 4.2f)   // 擦头（右上）
        }

        DoitIconKind.CloudSync -> {
            // 待同步：一朵饱满的云 + 一支粗的向上箭头（upload），语义单一、一眼看懂：
            // 有本地改动没推上去，点它上传。箭头比云描边略粗，缩小到 30dp 仍清晰。
            drawPath(cloudPath(u), c, style = stroke)
            val aw = w * 1.5f
            drawLine(c, Offset(12f * u, 15.4f * u), Offset(12f * u, 9.5f * u), aw, StrokeCap.Round)
            drawLine(c, Offset(9.4f * u, 12.1f * u), Offset(12f * u, 9.5f * u), aw, StrokeCap.Round)
            drawLine(c, Offset(14.6f * u, 12.1f * u), Offset(12f * u, 9.5f * u), aw, StrokeCap.Round)
        }

        DoitIconKind.CloudSyncOk -> {
            // 已同步：同一朵云 + 圆头对勾，表示「与远程一致」。与上传态共用云形，
            // 只换内部符号（勾 / 上箭头），状态差异一眼可辨且风格统一。
            drawPath(cloudPath(u), c, style = stroke)
            val check = Path().apply {
                moveTo(9.1f * u, 12.4f * u)
                lineTo(11.3f * u, 14.6f * u)
                lineTo(15.2f * u, 10.2f * u)
            }
            drawPath(
                check,
                c,
                style = Stroke(width = w * 2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}

/** 24×24 网格上一朵饱满、左右对称的云；静态图标与同步动画共用，保证形状完全一致。
 * [scale] 以 (12,12) 为中心缩放，动画里让云随呼吸轻微放大时不必重算 Bézier 控制点。 */
private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

private fun cloudPath(u: Float, scale: Float = 1f): Path = Path().apply {
    fun px(v: Float) = (12f + (v - 12f) * scale) * u
    moveTo(px(7.6f), px(16.8f))
    quadraticBezierTo(px(3.6f), px(16.8f), px(4.1f), px(12.9f))
    quadraticBezierTo(px(4.5f), px(10.2f), px(7.4f), px(10.1f))
    quadraticBezierTo(px(8.6f), px(6.4f), px(12.6f), px(7.1f))
    quadraticBezierTo(px(16.2f), px(7.2f), px(16.9f), px(10.4f))
    quadraticBezierTo(px(20.4f), px(10.6f), px(20f), px(14.2f))
    quadraticBezierTo(px(19.7f), px(16.8f), px(16.6f), px(16.8f))
    close()
}

/**
 * 同步进行中的上传动画：外圈一段缺口圆环匀速旋转（表示「正在处理」），
 * 云朵内一支向上箭头循环上浮并淡出（表示「数据正在上传」）。
 * 铺满圆形按钮，尺寸传按钮边长即可。
 */
@Composable
fun CloudSyncUploadAnimation(
    size: Dp,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    val infinite = rememberInfiniteTransition(label = "cloudUpload")
    val rise by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1150, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "arrowRise",
    )
    val spin by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Restart),
        label = "ringSpin",
    )
    val breathe by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1150, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "cloudBreathe",
    )
    Canvas(modifier.size(size)) {
        val u = this.size.minDimension / 24f
        val cloudScale = lerp(0.82f, 0.88f, breathe)

        // 外圈：一段 300° 的缺口圆环匀速旋转——经典「进行中」信号，填满 54dp 也不空
        drawArc(
            color = tint.copy(alpha = 0.35f),
            startAngle = spin,
            sweepAngle = 300f,
            useCenter = false,
            topLeft = Offset(2.2f * u, 2.2f * u),
            size = Size(19.6f * u, 19.6f * u),
            style = Stroke(width = 1.7f * u, cap = StrokeCap.Round),
        )

        // 云：随呼吸轻微缩放
        drawPath(cloudPath(u, cloudScale), tint, style = Stroke(width = 1.7f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))

        // 上传箭头：在云朵内部从下往上浮，头尾淡入淡出，循环往复。
        // 注意 arrowY 是 24 网格单位，落笔前必须乘 u 换成像素，否则会漂到画布顶边。
        val arrowY = lerp(13.8f, 10.6f, rise) * u
        val fade = minOf((rise * 5f).coerceAtMost(1f), ((1f - rise) * 4f).coerceAtMost(1f))
        if (fade > 0.01f) {
            val c = tint.copy(alpha = fade)
            val aw = 2.3f * u
            val tipY = arrowY - 1.4f * u
            drawLine(c, Offset(12f * u, arrowY + 1.9f * u), Offset(12f * u, tipY), aw, StrokeCap.Round)
            drawLine(c, Offset(9.8f * u, arrowY + 0.2f * u), Offset(12f * u, tipY), aw, StrokeCap.Round)
            drawLine(c, Offset(14.2f * u, arrowY + 0.2f * u), Offset(12f * u, tipY), aw, StrokeCap.Round)
        }
    }
}
