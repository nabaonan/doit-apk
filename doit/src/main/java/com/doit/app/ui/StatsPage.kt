package com.doit.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.doit.app.data.CompletionLogEntity
import com.doit.app.ui.gesture.EaseOutExpo
import com.doit.app.ui.components.PillTab
import com.doit.app.ui.components.PillTabs
import com.doit.app.ui.components.PillTabsMode
import com.doit.app.ui.theme.HeatColors
import com.doit.app.ui.theme.Doit
import com.doit.app.ui.theme.numeric
import com.doit.app.ui.theme.titleStyle
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private enum class StatRange(val label: String, val days: Int) {
    WEEK("近一周", 7),
    MONTH("近一月", 30),
    YEAR("近一年", 365),
}

@Composable
fun StatsPage(state: UiState, onBack: () -> Unit, modifier: Modifier = Modifier) {
    var range by remember { mutableStateOf(StatRange.MONTH) }
    val today = LocalDate.now()
    val counts = remember(state.logs) { dayCounts(state.logs) }
    val categories = state.categories
    val scroll = rememberScrollState()

    // 内容滚起来之后，标题栏底部浮出一条细线，交代这一层是钉住的
    val stuck by animateFloatAsState(
        targetValue = if (scroll.value > 4) 1f else 0f,
        animationSpec = tween(180),
        label = "statsHeaderLine",
    )

    // 换时间范围时内容轻轻淡入并上浮，避免整屏"啪"地换掉
    val contentFade = remember { Animatable(1f) }
    LaunchedEffect(range) {
        contentFade.snapTo(0.15f)
        contentFade.animateTo(1f, tween(260, easing = EaseOutExpo))
    }

    Column(
        modifier
            .fillMaxSize()
            .background(Doit.Surface),
    ) {
        // ---------- 固定在顶部的标题 + 时间范围 ----------
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 22.dp, end = 22.dp, top = 14.dp, bottom = 10.dp),
        ) {
            PageHead(title = "统计", latin = "STATS", onBack = onBack)
            Spacer(Modifier.height(14.dp))
            RangeSwitcher(current = range, onSelect = { range = it })
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Doit.Line.copy(alpha = 0.55f * stuck)))

        // ---------- 可滚动的内容 ----------
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(scroll)
                .padding(top = 16.dp, bottom = 28.dp)
                .graphicsLayer {
                    alpha = contentFade.value
                    translationY = (1f - contentFade.value) * 14f
                },
        ) {
            // ---------- 热力图（年视图内容整屏铺满，不留左右边距） ----------
            StatSection(
                title = "热力图",
                caption = when (range) {
                    StatRange.WEEK -> "近 7 天 · 一格一天，颜色越深完成越多"
                    StatRange.MONTH -> "近 30 天 · 一格一天，颜色越深完成越多"
                    StatRange.YEAR -> "近一年 · 一格一天，颜色越深完成越多"
                },
                contentFullBleed = range == StatRange.YEAR,
            ) {
                when (range) {
                    StatRange.WEEK -> {
                        HeatWeek(counts, today)
                        HeatLegend()
                    }

                    StatRange.MONTH -> {
                        HeatMonth(counts, today)
                        HeatLegend()
                    }

                    StatRange.YEAR -> {
                        HeatYear(counts, today)
                        HeatLegend(Modifier.padding(horizontal = 22.dp))
                    }
                }
            }

            // ---------- 环形图 ----------
            StatSection(
                title = "环形图",
                caption = when (range) {
                    StatRange.WEEK -> "按分类 · 近 7 天完成占比"
                    StatRange.MONTH -> "按分类 · 近 30 天完成占比"
                    StatRange.YEAR -> "按分类 · 近一年完成占比"
                },
            ) {
                DonutChart(categories = categories, logs = state.logs, days = range.days)
            }

            // ---------- 折线图 ----------
            StatSection(
                title = "折线图",
                caption = when (range) {
                    StatRange.WEEK -> "近 7 天 · 每天完成数"
                    StatRange.MONTH -> "近 30 天 · 每天完成数"
                    StatRange.YEAR -> "按月 · 近一年每月完成数"
                },
                last = true,
            ) {
                LineChart(counts = counts, today = today, range = range)
            }
        }
    }
}

/** 时间范围切换：交给全局统一的胶囊 tab 组件（深色胶囊平滑滑到选中项） */
@Composable
private fun RangeSwitcher(current: StatRange, onSelect: (StatRange) -> Unit) {
    PillTabs(
        tabs = StatRange.entries.map { PillTab(key = it.name, label = it.label, accent = Doit.Accent) },
        selectedKey = current.name,
        onSelect = { key -> StatRange.entries.firstOrNull { it.name == key }?.let(onSelect) },
        mode = PillTabsMode.Equal,
        height = 32.dp,
        gap = 6.dp,
        indicatorColor = Doit.Accent,
    )
}

@Composable
fun PageHead(title: String, latin: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .tapFeedback { onBack() }
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DoitIcon(DoitIconKind.Back, 15.dp, Doit.AccentInk, stroke = 2.dp)
            Spacer(Modifier.width(3.dp))
            Text("返回", fontSize = 13.sp, color = Doit.AccentInk)
        }
        Spacer(Modifier.width(12.dp))
        Text(title, style = titleStyle.copy(fontSize = 19.sp))
        Spacer(Modifier.width(8.dp))
        Text(latin, fontSize = 9.sp, letterSpacing = 2.sp, color = Doit.InkFaint)
    }
}

@Composable
private fun StatSection(
    title: String,
    caption: String,
    last: Boolean = false,
    contentFullBleed: Boolean = false,
    content: @Composable () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = if (last) 0.dp else 18.dp),
    ) {
        Column(Modifier.padding(horizontal = 22.dp)) {
            Text(title, style = titleStyle.copy(fontSize = 14.sp))
            Text(
                caption,
                fontSize = 11.sp,
                color = Doit.InkFaint,
                modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
            )
        }
        Column(if (contentFullBleed) Modifier.fillMaxWidth() else Modifier.padding(horizontal = 22.dp)) {
            content()
        }
        if (!last) {
            Spacer(Modifier.height(18.dp))
            Box(Modifier.fillMaxWidth().padding(horizontal = 22.dp).height(1.dp).background(Doit.LineSoft))
        }
    }
}

// ---------- 图表气泡（tooltip） ----------

/**
 * 气泡锚点是尾尖指向的位置：
 * - below = false：气泡在锚点上方，尾尖朝下顶着锚点
 * - below = true：气泡在锚点下方，尾尖朝上顶着锚点
 *
 * 贴边时**只让气泡本体让位**，尾尖始终钉在锚点上（左右各留 8dp 余量），
 * 所以点最左/最右那一格时，气泡会往里收、但尖仍然指着那一格。
 * 节点自身报告 0×0（不参与父级排版），允许画出父级边界之外（父级不裁剪）。
 */
@Composable
private fun TipBubble(text: String, anchorX: Dp, anchorY: Dp, below: Boolean = false) {
    Layout(
        modifier = Modifier
            // 气泡必须盖在刻度文字、网格线之上（同级后绘制的兄弟节点会压在它上面）
            .zIndex(2f),
        content = {
            TipBody(text)
            TipTail(up = below)
        },
    ) { measurables, constraints ->
        val body = measurables[0].measure(Constraints())
        val tail = measurables[1].measure(Constraints())
        val edge = 8.dp.roundToPx()
        val ax = anchorX.roundToPx()
        val ay = anchorY.roundToPx()

        val maxW = if (constraints.hasBoundedWidth) constraints.maxWidth else Int.MAX_VALUE
        val overlap = 1.dp.roundToPx()   // 尾尖与气泡重叠 1dp，藏住接缝
        val bodyLeft = (ax - body.width / 2)
            .coerceIn(edge, (maxW - body.width - edge).coerceAtLeast(edge))
        val bodyTop = if (below) {
            ay + tail.height - overlap
        } else {
            ay - tail.height - body.height + overlap
        }

        // 尾尖跟着格子；只有气泡本身已经顶到屏幕边、尖会戳出气泡体外时才稍微收一点
        val tailLeft = (ax - tail.width / 2).coerceIn(
            bodyLeft + edge,
            (bodyLeft + body.width - tail.width - edge).coerceAtLeast(bodyLeft + edge),
        )
        val tailTop = if (below) ay else ay - tail.height

        layout(0, 0) {
            body.place(bodyLeft, bodyTop)
            tail.place(tailLeft, tailTop)
        }
    }
}

@Composable
private fun TipBody(text: String) {
    Box(
        Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(Doit.Tooltip)
            .padding(horizontal = 9.dp, vertical = 4.dp),
    ) {
        Text(text, fontSize = 10.sp, color = Doit.OnTooltip, letterSpacing = 0.3.sp)
    }
}

@Composable
private fun TipTail(up: Boolean) {
    // 真正的三角尾尖：底边贴着气泡，尖端指向锚点（原来用旋转 45° 的方块，会露出菱形边）。
    // 与气泡的 1dp 重叠交给 TipBubble 的摆放计算，这里不再自己偏移，尖端坐标才准。
    Canvas(Modifier.size(12.dp, 6.dp)) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            if (up) {
                moveTo(0f, h)
                lineTo(w, h)
                lineTo(w / 2f, 0f)
            } else {
                moveTo(0f, 0f)
                lineTo(w, 0f)
                lineTo(w / 2f, h)
            }
            close()
        }
        drawPath(path, Doit.Tooltip)
    }
}

private fun tipText(d: LocalDate, n: Int): String =
    "${d.monthValue}月${d.dayOfMonth}日 · 完成 $n 件"

@Composable
private fun HeatLegend(modifier: Modifier = Modifier) {
    Row(
        modifier.padding(top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("少", fontSize = 9.sp, color = Doit.InkFaint)
        Spacer(Modifier.width(4.dp))
        HeatColors.forEach { c ->
            Box(
                Modifier
                    .padding(end = 4.dp)
                    .size(11.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(c),
            )
        }
        Spacer(Modifier.width(2.dp))
        Text("多", fontSize = 9.sp, color = Doit.InkFaint)
    }
}

@Composable
private fun HeatWeek(counts: Map<LocalDate, Int>, today: LocalDate) {
    var selected by remember { mutableStateOf<LocalDate?>(null) }
    val gap = 4.dp
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cellW = ((maxWidth - gap * 6f).value / 7f).dp
        Box {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                (6 downTo 0).forEach { offset ->
                    val date = today.minusDays(offset.toLong())
                    val sel = selected == date
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(HeatColors[heatLevel(counts[date] ?: 0)])
                                .then(
                                    if (sel) Modifier.border(1.6.dp, Doit.AccentInk, RoundedCornerShape(8.dp))
                                    else Modifier
                                )
                                .tapFeedback { selected = if (sel) null else date },
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (offset == 0) "今天" else "${date.monthValue}/${date.dayOfMonth}",
                            fontSize = 8.5.sp,
                            color = if (sel) Doit.Ink else Doit.InkFaint,
                            fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }
            selected?.let { d ->
                // 列的顺序是「6 天前 → 今天」从左到右，所以列号 = 6 - 距今天数。
                // 之前直接用距今天数当列号，锚点被镜像到左边（点今天，气泡却挂在 6 天前上方）。
                val ago = ChronoUnit.DAYS.between(d, today).toInt() // 0 = 今天
                val col = 6 - ago
                val x = (cellW + gap) * col.toFloat() + cellW / 2f
                TipBubble(
                    text = tipText(d, counts[d] ?: 0),
                    anchorX = x,
                    anchorY = (-2).dp,
                )
            }
        }
    }
}

@Composable
private fun HeatMonth(counts: Map<LocalDate, Int>, today: LocalDate) {
    var selected by remember { mutableStateOf<LocalDate?>(null) }
    val days = 30
    val start = today.minusDays(days - 1L)
    val startDow = start.dayOfWeek.value - 1 // 周一 = 0
    val rows = ceil((startDow + days) / 7.0).toInt()
    val gap = 4.dp
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cellW = ((maxWidth - gap * 6f).value / 7f).dp
        val rowPitch = cellW + 4.dp
        val maxX = maxWidth - 52.dp
        Box {
            Column {
                repeat(rows) { r ->
                    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(gap)) {
                        repeat(7) { c ->
                            val index = r * 7 + c - startDow
                            val date = start.plusDays(index.toLong())
                            val inRange = index in 0 until days
                            val sel = inRange && selected == date
                            Box(
                                Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(if (inRange) HeatColors[heatLevel(counts[date] ?: 0)] else Color.Transparent)
                                    .then(
                                        if (sel) Modifier.border(1.6.dp, Doit.AccentInk, RoundedCornerShape(9.dp))
                                        else Modifier
                                    )
                                    .tapFeedback(enabled = inRange) {
                                        selected = if (sel) null else date
                                    },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    "一二三四五六日".forEach { d ->
                        Text(
                            d.toString(),
                            fontSize = 8.5.sp,
                            color = Doit.InkFaint,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            selected?.let { d ->
                val idx = ChronoUnit.DAYS.between(start, d).toInt()
                val r = (idx + startDow) / 7
                val c = (idx + startDow) % 7
                val x = cellW * c.toFloat() + gap * c.toFloat() + cellW / 2f
                val cellTop = rowPitch * r
                TipBubble(
                    text = tipText(d, counts[d] ?: 0),
                    anchorX = x,
                    anchorY = cellTop - 3.dp,
                )
            }
        }
    }
}

@Composable
private fun HeatYear(counts: Map<LocalDate, Int>, today: LocalDate) {
    var selected by remember { mutableStateOf<LocalDate?>(null) }
    val days = 365
    val start = today.minusDays(days - 1L)
    val startDow = start.dayOfWeek.value - 1
    val cols = ceil((startDow + days) / 7.0).toInt()
    val gap = 1.dp

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // 格宽由整屏宽度反推：格阵铺满屏幕，不留左右边距
        val cell = ((maxWidth - gap * (cols - 1).toFloat()).value / cols).dp
        val pitch = cell + gap
        val gridH = pitch * 7f - gap
        val axisW = pitch * cols - gap // 轴宽 = 格阵总宽 = 屏宽

        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(gridH)
                    .pointerInput(cols, start) {
                        detectTapGestures { off ->
                            val pitchPx = pitch.toPx()
                            val c = (off.x / pitchPx).toInt()
                            val r = (off.y / pitchPx).toInt()
                            val idx = c * 7 + r - startDow
                            val d = start.plusDays(idx.toLong())
                            selected = if (idx in 0 until days && selected != d) d else null
                        }
                    },
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    repeat(cols) { c ->
                        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                            repeat(7) { r ->
                                val index = c * 7 + r - startDow
                                val date = start.plusDays(index.toLong())
                                val inRange = index in 0 until days
                                val sel = inRange && selected == date
                                Box(
                                    Modifier
                                        .size(cell)
                                        .clip(RoundedCornerShape(1.5.dp))
                                        .background(if (inRange) HeatColors[heatLevel(counts[date] ?: 0)] else Color.Transparent)
                                        .then(
                                            if (sel) Modifier.border(1.dp, Doit.AccentInk, RoundedCornerShape(1.5.dp))
                                            else Modifier
                                        ),
                                )
                            }
                        }
                    }
                }
                selected?.let { d ->
                    val idx = ChronoUnit.DAYS.between(start, d).toInt()
                    val c = (idx + startDow) / 7
                    val r = (idx + startDow) % 7
                    val x = pitch * c + cell / 2f
                    val cellTop = pitch * r
                    val below = cellTop < 36.dp
                    TipBubble(
                        text = tipText(d, counts[d] ?: 0),
                        anchorX = x,
                        anchorY = if (below) cellTop + cell + 3.dp else cellTop - 3.dp,
                        below = below,
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            // 月份刻度：从右往左贪心，只保留放得下的标注，"今天"固定贴格阵右缘
            val marks = remember(start, cols) { monthMarks(start, startDow, cols, days) }
            Box(Modifier.width(axisW).height(14.dp)) {
                val todayX = axisW - 22.dp
                val kept = remember(marks, todayX) {
                    var limit = todayX
                    marks.asReversed().mapNotNull { (c, label) ->
                        val x = pitch * c
                        if (x + 27.dp <= limit) {
                            limit = x
                            x to label
                        } else null
                    }.reversed()
                }
                kept.forEach { (x, label) ->
                    Text(
                        label,
                        fontSize = 8.5.sp,
                        color = Doit.InkFaint,
                        maxLines = 1,
                        modifier = Modifier.offset(x = x, y = (-2).dp),
                    )
                }
                Text(
                    "今天",
                    fontSize = 8.5.sp,
                    color = Doit.InkFaint,
                    maxLines = 1,
                    modifier = Modifier.offset(x = todayX, y = (-2).dp),
                )
            }
        }
    }
}

/** 每列月份变化处的刻度：(列号, 文案)。首列显示「月/日」，其后显示「N月」 */
internal fun monthMarks(
    start: LocalDate,
    startDow: Int,
    cols: Int,
    days: Int,
): List<Pair<Int, String>> {
    val out = mutableListOf<Pair<Int, String>>()
    var lastMonth: Int? = null
    for (c in 0 until cols) {
        val maxIdx = (0..6).map { r -> c * 7 + r - startDow }.filter { it in 0 until days }.maxOrNull() ?: continue
        val d = start.plusDays(maxIdx.toLong())
        if (lastMonth == null) out += c to "${d.monthValue}/${d.dayOfMonth}"
        else if (d.monthValue != lastMonth) out += c to "${d.monthValue}月"
        lastMonth = d.monthValue
    }
    return out
}

@Composable
private fun DonutChart(categories: List<CategoryUi>, logs: List<CompletionLogEntity>, days: Int) {
    val since = LocalDate.now().minusDays(days - 1L)
    val grouped = logs
        .filter { millisToDate(it.completedAt) >= since }
        .groupingBy { it.category }
        .eachCount()
    val data = categories.map { CategoryUi(it.name, it.color, grouped[it.name] ?: 0) }
        .filter { it.count > 0 }
        .ifEmpty { categories.take(1).map { CategoryUi(it.name, it.color, 0) } }
    val total = data.sumOf { it.count }
    var selected by remember(data) { mutableStateOf<String?>(null) }

    // 分段角度：从 -90°（12 点方向）顺时针
    val segs = remember(data, total) {
        var acc = -90f
        data.map { d ->
            val sweep = if (total > 0) 360f * d.count / total else 0f
            val s = acc
            acc += sweep
            Triple(d, s, sweep)
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(118.dp), contentAlignment = Alignment.Center) {
            Canvas(
                Modifier
                    .size(118.dp)
                    .pointerInput(segs, total) {
                        detectTapGestures { off ->
                            val dx = off.x - size.width / 2f
                            val dy = off.y - size.height / 2f
                            val dist = sqrt(dx * dx + dy * dy)
                            val rPx = 40.dp.toPx()
                            selected = if (total > 0 && dist >= rPx - 14.dp.toPx() && dist <= rPx + 14.dp.toPx()) {
                                var ang = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
                                if (ang < 0f) ang += 360f
                                val hit = segs.firstOrNull { (_, s, sweep) ->
                                    if (sweep <= 0f) return@firstOrNull false
                                    val s0 = (s + 360f) % 360f
                                    val a = if (ang < s0) ang + 360f else ang
                                    a >= s0 && a < s0 + sweep
                                }
                                if (hit != null && selected != hit.first.name) hit.first.name else null
                            } else null
                        }
                    },
            ) {
                val stroke = 15.dp.toPx()
                val r = 40.dp.toPx()
                val topLeft = Offset(center.x - r, center.y - r)
                val arcSize = Size(r * 2, r * 2)
                drawArc(Doit.PaperDeep, 0f, 360f, false, topLeft = topLeft, size = arcSize, style = Stroke(stroke))
                segs.forEach { (d, s, sweep) ->
                    if (sweep > 0f) {
                        drawArc(
                            color = d.color,
                            startAngle = s,
                            sweepAngle = sweep,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(if (selected == d.name) stroke + 3.dp.toPx() else stroke),
                        )
                    }
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$total", style = numeric(22).copy(fontWeight = FontWeight.SemiBold))
                Text("件已完成", fontSize = 9.sp, color = Doit.InkFaint, letterSpacing = 1.sp)
            }
            selected?.let { name ->
                val (d, s, sweep) = segs.first { it.first.name == name }
                val mid = Math.toRadians((s + sweep / 2f).toDouble())
                // 父 Box 居中对齐，0 尺寸节点落在圆心：锚点用相对圆心的坐标
                val ax = 40.dp * cos(mid).toFloat()
                val ay = 40.dp * sin(mid).toFloat()
                TipBubble(
                    text = "${d.name} · ${d.count} 件（${if (total == 0) 0 else (d.count * 100f / total).roundToInt()}%）",
                    anchorX = ax.coerceIn((-4).dp, 111.dp),
                    anchorY = ay,
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            data.forEach { d ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(9.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(d.color)
                            .then(
                                if (selected == d.name) Modifier.border(1.dp, Doit.AccentInk, RoundedCornerShape(3.dp))
                                else Modifier
                            )
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        d.name,
                        fontSize = 11.5.sp,
                        color = if (selected == d.name) Doit.Ink else Doit.InkSoft,
                        fontWeight = if (selected == d.name) FontWeight.SemiBold else FontWeight.Normal,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (total == 0) "0%" else "${(d.count * 100f / total).roundToInt()}%",
                        style = numeric(10).copy(fontWeight = FontWeight.Normal),
                        color = Doit.InkFaint,
                    )
                }
            }
        }
    }
}

@Composable
private fun LineChart(counts: Map<LocalDate, Int>, today: LocalDate, range: StatRange) {
    val values: List<Int>
    val labels: List<String>
    when (range) {
        StatRange.WEEK -> {
            values = (6 downTo 0).map { counts[today.minusDays(it.toLong())] ?: 0 }
            labels = (6 downTo 0).map { if (it == 0) "今天" else today.minusDays(it.toLong()).dayOfMonth.toString() }
        }

        StatRange.MONTH -> {
            values = (29 downTo 0).map { counts[today.minusDays(it.toLong())] ?: 0 }
            labels = (29 downTo 0).map { offset ->
                when {
                    offset == 0 -> "今天"
                    offset % 5 == 4 -> today.minusDays(offset.toLong()).dayOfMonth.toString()
                    else -> ""
                }
            }
        }

        StatRange.YEAR -> {
            val (v, l) = yearMonthlySeries(counts, today)
            values = v
            labels = l
        }
    }
    val maxValue = max(4, ceil((values.maxOrNull() ?: 0) * 1.15).toInt())
    val lineColor = Doit.AccentInk
    var selected by remember(range, values) { mutableStateOf<Int?>(null) }

    // 气泡文案：周/月用「M月d日」，年用「N月」
    val tipLabels: List<String> = when (range) {
        StatRange.WEEK -> (6 downTo 0).map { off ->
            if (off == 0) "今天" else today.minusDays(off.toLong()).let { "${it.monthValue}月${it.dayOfMonth}日" }
        }

        StatRange.MONTH -> (29 downTo 0).map { off ->
            today.minusDays(off.toLong()).let { "${it.monthValue}月${it.dayOfMonth}日" }
        }

        StatRange.YEAR -> labels
    }

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val chartH = 124.dp
        val pad = 12.dp
        val step = if (values.size > 1) ((maxWidth - pad * 2f).value / (values.size - 1)).dp else 0.dp

        fun yOf(v: Int): Dp = chartH - 20.dp - (chartH - 44.dp) * (v.toFloat() / maxValue)

        Box(Modifier.fillMaxWidth().height(chartH)) {
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(chartH)
                    .pointerInput(values, range) {
                        detectTapGestures { off ->
                            val stepPx = step.toPx()
                            val i = if (stepPx > 0f) ((off.x - pad.toPx()) / stepPx).roundToInt() else 0
                            val idx = i.coerceIn(0, values.size - 1)
                            selected = if (selected == idx) null else idx
                        }
                    },
            ) {
                val padPx = pad.toPx()
                val w = size.width
                val h = size.height
                val stepPx = step.toPx()

                repeat(4) { k ->
                    val yy = yOf(maxValue * (k + 1) / 4).toPx()
                    drawLine(Doit.LineSoft, Offset(padPx, yy), Offset(w - padPx, yy), 1.dp.toPx())
                }

                val path = Path()
                values.forEachIndexed { i, v ->
                    val x = padPx + i * stepPx
                    if (i == 0) path.moveTo(x, yOf(v).toPx()) else path.lineTo(x, yOf(v).toPx())
                }
                drawPath(
                    path = path,
                    color = lineColor,
                    style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                )

                // 选中点：纵向参考线 + 放大圆点
                selected?.let { si ->
                    val x = padPx + si * stepPx
                    drawLine(
                        color = Doit.Line,
                        start = Offset(x, 6.dp.toPx()),
                        end = Offset(x, h - 20.dp.toPx()),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f)),
                    )
                }

                values.forEachIndexed { i, v ->
                    val x = padPx + i * stepPx
                    val on = selected == i
                    val dotR = when {
                        on -> 4.6.dp.toPx()
                        values.size > 12 -> 2.2.dp.toPx()
                        else -> 3.4.dp.toPx()
                    }
                    drawCircle(Doit.Surface, dotR, Offset(x, yOf(v).toPx()))
                    drawCircle(lineColor, dotR, Offset(x, yOf(v).toPx()), style = Stroke(1.8.dp.toPx()))
                    if (on) {
                        drawCircle(lineColor.copy(alpha = 0.25f), dotR + 3.dp.toPx(), Offset(x, yOf(v).toPx()))
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            labels.filter { it.isNotEmpty() }.let { visible ->
                if (range == StatRange.YEAR) {
                    visible.forEach { l ->
                        Text(l, fontSize = 8.5.sp, color = Doit.InkFaint)
                    }
                } else {
                    visible.forEachIndexed { i, l ->
                        Text(
                            l,
                            fontSize = 8.5.sp,
                            color = Doit.InkFaint,
                            textAlign = if (i == 0) TextAlign.Start else if (i == visible.lastIndex) TextAlign.End else TextAlign.Center,
                        )
                    }
                }
            }
        }
        // 气泡放在刻度行之后：同级兄弟按绘制顺序叠放，靠后的会盖住靠前的刻度文字
        selected?.let { i ->
            val bx = pad + step * i
            TipBubble(
                text = "${tipLabels[i]} · ${values[i]} 件",
                anchorX = bx,
                anchorY = yOf(values[i]) - 9.dp,
            )
        }
    }
}

// ---------- 数据小工具 ----------

/**
 * 近一年的折线图数据：按时间顺序回溯 12 个月（含当月）。
 * 不能按 monthValue 0..11 分组，否则跨年时 1 月会被排到最前面，标签与月份错位。
 */
internal fun yearMonthlySeries(
    counts: Map<LocalDate, Int>,
    today: LocalDate,
): Pair<List<Int>, List<String>> {
    val currentMonth = java.time.YearMonth.from(today)
    val months = (11 downTo 0).map { currentMonth.minusMonths(it.toLong()) }
    val values = months.map { ym ->
        var sum = 0
        var d: LocalDate? = ym.atDay(1)
        while (d != null && java.time.YearMonth.from(d) == ym) {
            sum += counts[d] ?: 0
            d = d.plusDays(1)
        }
        sum
    }
    return values to months.map { "${it.monthValue}月" }
}

private fun LocalDate.toEpochMillis(): Long =
    this.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

private fun millisToDate(millis: Long): LocalDate =
    java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault()).toLocalDate()

private fun dayCounts(logs: List<CompletionLogEntity>): Map<LocalDate, Int> {
    if (logs.isEmpty()) return emptyMap()
    val today = LocalDate.now()
    val from = today.minusDays(365)
    return logs.asSequence()
        .map { millisToDate(it.completedAt) }
        .filter { it in from..today }
        .groupingBy { it }
        .eachCount()
}

private fun heatLevel(count: Int): Int = when {
    count <= 0 -> 0
    count <= 2 -> 1
    count <= 4 -> 2
    count <= 7 -> 3
    else -> 4
}
