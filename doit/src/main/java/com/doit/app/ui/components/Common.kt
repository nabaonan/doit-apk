package com.doit.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.doit.app.data.TaskEntity
import com.doit.app.ui.DoitIcon
import com.doit.app.ui.DoitIconKind
import com.doit.app.ui.tapFeedback
import com.doit.app.ui.theme.FlowSkin
import com.doit.app.ui.theme.Priority
import com.doit.app.ui.theme.Doit
import com.doit.app.ui.theme.checkFill
import com.doit.app.ui.theme.fillFor
import com.doit.app.ui.theme.labelOn
import com.doit.app.ui.theme.numeric
import com.doit.app.ui.theme.readableOn
import kotlin.math.roundToInt

/**
 * 圆形勾选框：
 * - 进度环直接画在圆圈自身的边缘上（不再额外套一圈）
 * - 勾本身随进度一笔一笔描出
 * - 有子任务没做完时圈是虚线，点它会抖一下
 */
@Composable
fun TaskCheck(
    done: Boolean,
    locked: Boolean,
    p: Float,
    ring: Color,
    modifier: Modifier = Modifier,
    boxSize: Dp = 34.dp,
    dotSize: Dp = 26.dp,
) {
    val density = LocalDensity.current
    val dotPx = with(density) { dotSize.toPx() }
    Canvas(modifier.size(boxSize)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val dotR = dotPx / 2f

        // 先画底圈的轮廓，再画进度环——环压在上层，不会被灰色圆遮住
        if (done) {
            // 实心圆：浅色下把浅薄荷压深一档配白勾，深色下保持亮薄荷配深勾
            drawCircle(color = checkFill(ring), radius = dotR, center = center)
        } else {
            drawCircle(
                color = Doit.InkFaint,
                radius = dotR,
                center = center,
                style = Stroke(
                    width = 1.8.dp.toPx(),
                    pathEffect = if (locked) {
                        PathEffect.dashPathEffect(floatArrayOf(4.2.dp.toPx(), 3.2.dp.toPx()))
                    } else null,
                ),
            )
        }

        if (p > 0.001f && !done) {
            // 环直接绘制在圆圈边缘，覆盖在轮廓线之上
            drawArc(
                color = ring,
                startAngle = -90f,
                sweepAngle = 360f * p.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = Offset(center.x - dotR, center.y - dotR),
                size = Size(dotR * 2, dotR * 2),
                style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round),
            )
        }

        val checkFrac = when {
            done -> 1f
            p > 0.001f -> p.coerceIn(0f, 1f)
            else -> 0f
        }
        if (checkFrac > 0.001f) {
            val unit = dotPx / 20f
            val path = Path().apply {
                moveTo(4f * unit + center.x - dotR, 10.5f * unit + center.y - dotR)
                lineTo(8f * unit + center.x - dotR, 14.5f * unit + center.y - dotR)
                lineTo(16f * unit + center.x - dotR, 5.5f * unit + center.y - dotR)
            }
            val measure = PathMeasure().apply { setPath(path, false) }
            val partial = Path()
            measure.getSegment(0f, measure.length * checkFrac, partial, true)
            drawPath(
                path = partial,
                // 勾色跟着底的明暗走：实心圆是彩底，一律白勾（浅到压不住白的地方会自动退回深墨）
                color = if (done) readableOn(checkFill(ring)) else ring,
                style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}

/** 完成划线：一条细线，多行标题也每行都有；颜色与删除态文字同款（跟着整卡 0.5 透明度一起变灰） */
@Composable
fun StrikeText(
    text: String,
    style: TextStyle,
    done: Boolean,
    modifier: Modifier = Modifier,
    maxLines: Int = 2,
) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Box(modifier) {
        Text(
            text = text,
            style = style,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { layout = it },
        )
        if (done) {
            Canvas(Modifier.matchParentSize()) {
                val lr = layout ?: return@Canvas
                for (i in 0 until lr.lineCount) {
                    val top = lr.getLineTop(i)
                    val bottom = lr.getLineBottom(i)
                    val y = (top + bottom) / 2f
                    val startX = lr.getLineLeft(i)
                    val endX = lr.getLineRight(i)
                    drawLine(
                        // 直接用文字同款 Ink：本组件位于删除态整卡的 0.5 透明度内，
                        // 划线会和文字一起被淡化到同一个浅灰，不再单独用黄/橘色
                        color = Doit.Ink,
                        start = Offset(startX, y),
                        end = Offset(endX, y),
                        strokeWidth = 1.3.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
            }
        }
    }
}

/** 卡片底部的一行小字：分类 · 时间（没归类的待办只显示时间，不留一个空的分隔点） */
@Composable
fun MetaRow(task: TaskEntity, modifier: Modifier = Modifier, showCategory: Boolean = true) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (showCategory && task.category.isNotBlank()) {
            Text(task.category, fontSize = 10.5.sp, color = Doit.InkFaint, letterSpacing = 0.4.sp)
            Text(" · ", fontSize = 10.5.sp, color = Doit.InkFaint)
        }
        Text(task.timeLabel, fontSize = 10.5.sp, color = Doit.InkFaint, letterSpacing = 0.4.sp)
    }
}

/** 优先级标签（tag）：放在卡片最右侧，替代原来的圆点 + 文字 */
@Composable
fun PriorityTag(
    prio: Priority,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Row(
        modifier
            .clip(RoundedCornerShape(7.dp))
            .background(prio.color.copy(alpha = 0.14f))
            .border(1.dp, prio.color.copy(alpha = 0.4f), RoundedCornerShape(7.dp))
            .padding(horizontal = if (compact) 6.dp else 8.dp, vertical = if (compact) 2.dp else 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            prio.label,
            fontSize = if (compact) 9.sp else 10.sp,
            fontWeight = FontWeight.SemiBold,
            // 语义色的原色太浅，当 10sp 字色压不住；同色相压暗一档，色相还是那个色相
            color = labelOn(prio.color, 0.34f),
            letterSpacing = 0.3.sp,
        )
    }
}

/** done/total 的完成百分比文案：`67%`；total<=0 一律返回 `0%` */
fun percentText(done: Int, total: Int): String {
    if (total <= 0) return "0%"
    val pct = (done.toFloat() * 100f / total).roundToInt()
    return "${pct}%"
}

/** 顶部完成进度环 */
@Composable
fun ProgressRing(done: Int, total: Int, modifier: Modifier = Modifier, size: Dp = 56.dp) {
    val target = if (total <= 0) 0f else done.toFloat() / total
    val animated by animateFloatAsState(target, tween(340), label = "progressRing")
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val stroke = 3.4.dp.toPx()
            val r = this.size.minDimension / 2f - stroke
            val topLeft = Offset(center.x - r, center.y - r)
            val arcSize = Size(r * 2, r * 2)
            // 底环用 InkFaint：它是"可读文字灰"，在浅绿底上能看清一圈轨道。
            // Line/LineSoft 都是低透明度描边，叠在接近同色的浅底上几乎隐形，
            // 与左侧有实底、有描边的大云按钮放一起会显得"只有云没有环"。
            drawArc(Doit.InkFaint, 0f, 360f, false, topLeft = topLeft, size = arcSize, style = Stroke(stroke))
            if (animated > 0.001f) {
                drawArc(
                    // 细弧线用 accentInk：#34C7A8 当 3.4dp 的线画在浅绿底上只有 1.9:1，会"虚"
                    color = Doit.AccentInk,
                    startAngle = -90f,
                    sweepAngle = 360f * animated,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
        }
        // 环内显示完成百分比（不是 2/3 这种数量）：与卡片上子任务百分比共用 percentText，观感统一
        Text(percentText(done, total), style = numeric(13))
    }
}

/** 滑动进度层：三套皮肤共用同一套位移，只换视觉 */
@Composable
fun FillLayer(skin: FlowSkin, modifier: Modifier = Modifier) {
    // 深色下填充本身就是深色，白色高光要压得很低，否则像开了手电
    val sheen = if (Doit.IsDark) 0.14f else 0.42f
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f) return@Canvas
        drawRect(
            brush = Brush.horizontalGradient(
                0f to skin.fillStart,
                0.58f to skin.fillMid,
                1f to skin.fillEnd,
                startX = 0f,
                endX = w.coerceAtLeast(1f),
            ),
        )
        when (skin) {
            FlowSkin.GREEN -> {
                // 极淡斜纹：进度看起来像在生长
                var x = -h
                while (x < w + h) {
                    drawLine(
                        color = Color.White.copy(alpha = sheen),
                        start = Offset(x, 0f),
                        end = Offset(x + h * 0.42f, h),
                        strokeWidth = 2.dp.toPx(),
                    )
                    x += 15.dp.toPx()
                }
            }

            FlowSkin.WATER -> {
                var x = -h
                while (x < w + h) {
                    drawLine(
                        color = Color.White.copy(alpha = sheen),
                        start = Offset(x, 0f),
                        end = Offset(x + h * 0.55f, h),
                        strokeWidth = 4.dp.toPx(),
                    )
                    x += 24.dp.toPx()
                }
                // 前端浪光
                drawRect(
                    brush = Brush.horizontalGradient(
                        0.7f to Color.Transparent,
                        1f to Color.White.copy(alpha = if (Doit.IsDark) 0.20f else 0.55f),
                        startX = 0f,
                        endX = w.coerceAtLeast(1f),
                    ),
                )
            }

            FlowSkin.SPARK -> {
                // 火星四溅
                val sparks = listOf(
                    0.80f to 0.26f, 0.91f to 0.58f, 0.73f to 0.76f, 0.96f to 0.20f, 0.86f to 0.88f,
                )
                sparks.forEachIndexed { i, (fx, fy) ->
                    drawCircle(
                        color = if (i % 2 == 0) Color(0xFFFFFBE8) else Color(0xFFFFD479),
                        radius = if (i % 2 == 0) 1.5.dp.toPx() else 1.1.dp.toPx(),
                        center = Offset(w * fx, h * fy),
                    )
                }
                drawRect(
                    brush = Brush.horizontalGradient(
                        0.75f to Color.Transparent,
                        1f to Color(0xFFFFD678).copy(alpha = 0.75f),
                        startX = 0f,
                        endX = w.coerceAtLeast(1f),
                    ),
                )
            }
        }
    }
}

/**
 * 左滑动作按钮的唯一尺寸表。
 *
 * 父任务卡、子任务卡、分类管理卡都用这一套槽宽 / 间距 / 圆径：
 * 槽宽一样、间距一样，所以不管滑开的是哪一张卡、露出的是一列还是四列，
 * 按钮之间的空隙看起来完全一致，不会出现"父任务挤、子任务松"的错位感。
 */
object ActionMetrics {
    /** 单个动作占的横向槽宽（图标圆 26dp 居中，靠两侧留白形成间距） */
    val Slot = 38.dp

    /** 相邻两个动作之间的额外间距 */
    val Gap = 5.dp

    /** 最右一个动作到卡片右缘的距离 */
    val Edge = 6.dp

    /** 图标圆的直径与里面图标的大小 */
    val Circle = 26.dp
    val IconSize = 13.dp

    /**
     * n 个动作所需的总宽度。
     * 卡片的让位距离也取这个值 —— 两者必须严格相等，滑开时按钮才刚好露完、不多不少。
     */
    fun widthOf(count: Int): Dp =
        if (count <= 0) 0.dp
        else Slot * count + Gap * (count - 1) + Edge
}

/** 左滑露出的动作按钮（父任务：图标 + 文案；子任务：只有图标） */
@Composable
fun RowScopeAction(
    label: String?,
    icon: DoitIconKind,
    color: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .width(ActionMetrics.Slot)
            .fillMaxHeight()
            .clip(RoundedCornerShape(12.dp))
            .tapFeedback { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(
                Modifier
                    .size(ActionMetrics.Circle)
                    .clip(CircleShape)
                    // 所有动作图标统一白色：亮黄 / 橘黄 / 金这类浅底直接压白图标只有 ~2:1，
                    // 所以圆底按 fillFor 等比压深到白字够读（色相饱和度不变，仍是黄 / 橘黄）。
                    // 与胶囊、勾选圆的实心底同一套规则，深浅主题都成立。
                    .background(fillFor(color)),
                contentAlignment = Alignment.Center,
            ) {
                DoitIcon(
                    kind = icon,
                    size = ActionMetrics.IconSize,
                    tint = Doit.OnAction,
                    stroke = 1.9.dp,
                )
            }
            if (label != null) {
                Spacer(Modifier.height(4.dp))
                Text(label, fontSize = 9.sp, color = Doit.OnAction, lineHeight = 9.sp)
            }
        }
    }
}

/** 空出来的地方，也是设计 */
@Composable
fun Breather(completedCount: Int, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(Modifier.width(96.dp).height(30.dp)) {
            val p = Path().apply {
                moveTo(2.dp.toPx(), 20.dp.toPx())
                cubicTo(
                    22.dp.toPx(), 4.dp.toPx(),
                    42.dp.toPx(), 26.dp.toPx(),
                    62.dp.toPx(), 12.dp.toPx(),
                )
                cubicTo(
                    76.dp.toPx(), 2.dp.toPx(),
                    88.dp.toPx(), 6.dp.toPx(),
                    94.dp.toPx(), 16.dp.toPx(),
                )
            }
            drawPath(
                path = p,
                color = Doit.InkFaint.copy(alpha = 0.55f),
                style = Stroke(width = 1.3.dp.toPx(), cap = StrokeCap.Round),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text("空出来的地方，也是设计。", fontSize = 13.sp, color = Doit.InkSoft, letterSpacing = 0.8.sp)
        Spacer(Modifier.height(6.dp))
        Text("已收起 $completedCount 件完成的事", fontSize = 10.sp, color = Doit.InkFaint, letterSpacing = 1.sp)
    }
}
