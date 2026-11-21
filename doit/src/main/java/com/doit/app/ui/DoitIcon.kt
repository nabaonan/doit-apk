package com.doit.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * 线性图标：全部按 24×24 的设计网格手绘，线宽圆头，
 * 与视觉稿里的 SVG 图标保持同一套语汇（圆、圆角、细线）。
 */
enum class DoitIconKind {
    Plus, Mic, Gear, FolderPlus, Heat, Flag, Folder, Sub, Trash, Palette, Back, Tick, Grid, Close, Chevron, Calendar, Pencil,
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
    }
}

/** Material Icons 官方 Cloud 轮廓路径（24×24 网格），用虚线描边即得「空心云」。 */
private const val MATERIAL_CLOUD_PATH =
    "M19.35 10.04C18.67 6.59 15.64 4 12 4c-3.11 0-5.64 1.64-6.65 4.04C2.34 8.36 0 10.91 0 14c0 3.31 2.69 6 6 6h13c2.76 0 5-2.24 5-5 0-2.64-2.05-4.78-4.65-4.96z"

/**
 * 云同步按钮的完整视觉，铺满圆形按钮（尺寸传按钮边长即可），三态互斥：
 * - 未同步（本地有改动没推）：绿色虚线环 + 虚线空心云（数据还未上传的「空」态）；
 * - 同步中 [syncing]：环形进度 + 浅绿色云彩，中央上下双向箭头做流动动画；
 * - 已同步 [consistent]：实色绿色云朵（CloudDone）+ 白色对钩，外面一圈实线圆。
 *
 * 云朵、箭头、对钩全部来自 Google 官方开源图标库 Material Icons Extended（Apache 2.0），
 * 状态外圈（虚线/进度/实线圆）用 Canvas 绘制，因为它是状态指示器而非图标。
 */
@Composable
fun CloudSyncVisual(
    size: Dp,
    consistent: Boolean,
    syncing: Boolean,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    val infinite = rememberInfiniteTransition(label = "cloudSync")
    val spin by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart),
        label = "ringSpin",
    )
    // 两支箭头各自沿云心上下往复，体现数据在手机与云之间双向流动；箭尖始终朝上/下，不旋转。
    val arrowTravel = (size.value * 0.065f).dp
    val upArrowOffset by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "upArrow",
    )
    val downArrowOffset by infinite.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "downArrow",
    )

    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        // ---- 外圈状态环 ----
        Canvas(Modifier.matchParentSize()) {
            val u = this.size.minDimension / 24f
            val ringR = 10.6f * u
            when {
                syncing -> {
                    // 环形进度：一段缺口环匀速旋转，经典 indeterminate 进度信号
                    drawArc(
                        color = tint,
                        startAngle = spin,
                        sweepAngle = 275f,
                        useCenter = false,
                        topLeft = Offset((12f * u) - ringR, (12f * u) - ringR),
                        size = Size(ringR * 2f, ringR * 2f),
                        style = Stroke(width = 1.9f * u, cap = StrokeCap.Round),
                    )
                }
                consistent -> {
                    // 已同步：外面也要有一圈圆圈（实线）
                    drawCircle(
                        color = tint,
                        radius = ringR,
                        center = Offset(12f * u, 12f * u),
                        style = Stroke(width = 1.6f * u),
                    )
                }
                else -> {
                    // 未同步：绿色虚线环 + 虚线空心云，提示本地有改动待推、云端还是「空」的
                    drawCircle(
                        color = tint,
                        radius = ringR,
                        center = Offset(12f * u, 12f * u),
                        style = Stroke(
                            width = 1.6f * u,
                            cap = StrokeCap.Round,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.6f * u, 2.2f * u)),
                        ),
                    )
                    val cloud = PathParser().parsePathString(MATERIAL_CLOUD_PATH).toPath()
                    val cloudScale = 0.92f * u
                    val cloudStroke = 1.7f * u / cloudScale
                    translate(left = 12f * u - 12f * cloudScale, top = 12f * u - 12f * cloudScale) {
                        scale(cloudScale) {
                            drawPath(
                                path = cloud,
                                color = tint,
                                style = Stroke(
                                    width = cloudStroke,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round,
                                    pathEffect = PathEffect.dashPathEffect(
                                        floatArrayOf(2.4f * u / cloudScale, 2f * u / cloudScale),
                                    ),
                                ),
                            )
                        }
                    }
                }
            }
        }

        // ---- 中心图标：全部来自 Material Icons Extended ----
        when {
            syncing -> {
                // 同步中：浅绿色云 + 两支上下箭头的流动动画（箭尖朝上/下，不旋转）
                Icon(
                    imageVector = Icons.Filled.Cloud,
                    contentDescription = null,
                    tint = tint.copy(alpha = 0.32f),
                    modifier = Modifier.size(size * 0.74f),
                )
                Icon(
                    imageVector = Icons.Filled.ArrowUpward,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier
                        .size(size * 0.21f)
                        .offset(y = (-(1f - upArrowOffset) * arrowTravel.value).dp),
                )
                Icon(
                    imageVector = Icons.Filled.ArrowDownward,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier
                        .size(size * 0.21f)
                        .offset(y = ((1f - downArrowOffset) * arrowTravel.value).dp),
                )
            }
            consistent -> Icon(
                imageVector = Icons.Filled.CloudDone,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(size * 0.66f),
            )
            // 未同步：空心云已在外圈 Canvas 里以虚线画出，这里不再叠实心图标
            else -> Unit
        }
    }
}
