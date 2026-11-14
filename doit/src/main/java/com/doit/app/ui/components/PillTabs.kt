package com.doit.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.doit.app.ui.tapFeedback
import com.doit.app.ui.theme.Doit
import com.doit.app.ui.theme.chipFill
import com.doit.app.ui.theme.chipOn
import com.doit.app.ui.theme.labelOn
import com.doit.app.ui.theme.numeric
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** 胶囊 tab 的内容模型。[accent] 是这个 tab 自己的颜色：浅底、虚线、选中实底都由它派生。 */
data class PillTab(
    val key: String,
    val label: String,
    val badge: Int? = null,
    val accent: Color = Doit.Accent,
    /** 可选的小圆点（设置页的滑动皮肤用渐变色点） */
    val dot: Brush? = null,
)

enum class PillTabsMode {
    /** 等宽等分，深色胶囊滑到选中项（统计页时间范围、设置页外观） */
    Equal,

    /** 内容宽度自适应，未选中虚线描边 + 同色浅底（首页分类、弹框内选择） */
    Chip,
}

private val CapsuleShape = RoundedCornerShape(999.dp)

/**
 * 全局统一的 tab 切换组件。
 * 两种形态共用同一套圆角、时长与缓动，也就是同一套"手感"。
 */
@Composable
fun PillTabs(
    tabs: List<PillTab>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    mode: PillTabsMode = PillTabsMode.Chip,
    height: Dp = 32.dp,
    gap: Dp = 6.dp,
    /** Equal 模式固定指示器颜色；传 null 则跟随选中项的 accent */
    indicatorColor: Color? = null,
    /** Chip 模式下超出一行时换行（底部弹框内用），默认单行横向滚动 */
    wrap: Boolean = false,
) {
    when (mode) {
        PillTabsMode.Equal -> EqualPillTabs(
            tabs = tabs,
            selectedKey = selectedKey,
            onSelect = onSelect,
            modifier = modifier,
            height = height,
            gap = gap,
            indicatorColor = indicatorColor,
        )

        PillTabsMode.Chip -> ChipPillTabs(
            tabs = tabs,
            selectedKey = selectedKey,
            onSelect = onSelect,
            modifier = modifier,
            height = height,
            gap = gap,
            wrap = wrap,
        )
    }
}

@Composable
private fun EqualPillTabs(
    tabs: List<PillTab>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    modifier: Modifier,
    height: Dp,
    gap: Dp,
    indicatorColor: Color?,
) {
    if (tabs.isEmpty()) return

    val index = tabs.indexOfFirst { it.key == selectedKey }.coerceIn(0, tabs.lastIndex)

    BoxWithConstraints(modifier.fillMaxWidth().height(height)) {
        val chipWidth = (maxWidth - gap * (tabs.size - 1)) / tabs.size
        // 弹簧而不是固定时长：连点时从当前速度接上去，不会"重新起步"那一下顿感
        val pillX by animateDpAsState(
            targetValue = (chipWidth + gap) * index,
            animationSpec = spring(dampingRatio = 0.86f, stiffness = 640f),
            label = "pillTabX",
        )
        val pillColor by animateColorAsState(
            // Equal 模式的指示器就是主题色实心块；实底比浅面深一档，白字才立得住
            targetValue = chipFill(indicatorColor ?: tabs[index].accent),
            animationSpec = tween(240),
            label = "pillTabColor",
        )

        // 指示器：整块胶囊滑到选中项，宽度不随内容变化
        Box(
            Modifier
                .offset(x = pillX)
                .width(chipWidth)
                .fillMaxHeight()
                .clip(CapsuleShape)
                .background(pillColor),
        )

        Row(
            Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
            tabs.forEach { tab ->
                val on = tab.key == selectedKey
                val fg by animateColorAsState(
                    targetValue = if (on) Doit.OnAccent else Doit.InkSoft,
                    // 新选中的那格等胶囊滑到跟前再翻色，否则会在半路上先闪一下
                    animationSpec = tween(durationMillis = 200, delayMillis = if (on) 60 else 0),
                    label = "pillTabFg",
                )
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CapsuleShape)
                        .tapFeedback { if (!on) onSelect(tab.key) },
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        tab.dot?.let { brush ->
                            Box(
                                Modifier
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(brush),
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(
                            tab.label,
                            fontSize = 12.sp,
                            color = fg,
                            fontWeight = if (on) FontWeight.Medium else FontWeight.Normal,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipPillTabs(
    tabs: List<PillTab>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    modifier: Modifier,
    height: Dp,
    gap: Dp,
    wrap: Boolean,
) {
    if (tabs.isEmpty()) return

    val density = LocalDensity.current
    val scroll = rememberScrollState()

    // 每个 tab 在内容坐标系里的位置（随内容变化自动刷新）
    val bounds = remember { mutableStateMapOf<String, Rect>() }
    val pillX = remember { Animatable(0f) }
    val pillW = remember { Animatable(0f) }
    // 0~0.12 的横向"拉伸量"：起步时被拉长一点、落位时弹回，
    // 胶囊看起来是被"推"过去的，而不是一块色板整体平移
    val stretch = remember { Animatable(0f) }
    var placed by remember { mutableStateOf(false) }

    val selectedTab = tabs.firstOrNull { it.key == selectedKey } ?: tabs.first()
    val target = bounds[selectedKey]
    // 选中实底就是这一项自己的颜色（分类色 / 优先级语义色 / 「全部」的主题绿）；
    // 浅彩会被 fillFor 压深一档，保证上面的白字看得清
    val pillColor by animateColorAsState(
        targetValue = chipFill(selectedTab.accent),
        animationSpec = tween(240),
        label = "chipPillColor",
    )

    LaunchedEffect(selectedKey, target?.left, target?.width) {
        val t = target ?: return@LaunchedEffect
        if (!placed) {
            // 首次定位不做动画，避免进页面时胶囊从左侧飞进来
            pillX.snapTo(t.left)
            pillW.snapTo(t.width)
            stretch.snapTo(0f)
            placed = true
            return@LaunchedEffect
        }
        if (pillW.value <= 0f) {
            pillX.snapTo(t.left)
            pillW.snapTo(t.width)
            return@LaunchedEffect
        }
        val dist = abs(t.left - pillX.value)
        launch {
            // 移动越远拉得越长，停下用低阻尼弹回：落点有一点"果冻"的手感
            stretch.snapTo((dist / 1000f).coerceIn(0f, 0.12f))
            stretch.animateTo(0f, spring(dampingRatio = 0.45f, stiffness = 220f))
        }
        // 位置比宽度更快到位，宽度略慢半拍 —— 这点"不同步"正是自然感的来源
        launch { pillX.animateTo(t.left, spring(dampingRatio = 0.86f, stiffness = 640f)) }
        launch { pillW.animateTo(t.width, spring(dampingRatio = 0.97f, stiffness = 480f)) }
    }

    // 内容变窄（删掉分类）时把胶囊收回到合法范围
    LaunchedEffect(bounds.size) {
        if (placed && bounds.isEmpty()) {
            pillX.snapTo(0f)
            pillW.snapTo(0f)
            placed = false
        }
    }

    // 选中的胶囊如果被滚出了可视区，顺手把它滚回来（横滑模式才有）
    if (!wrap) {
        LaunchedEffect(selectedKey, target?.left, target?.right) {
            val t = target ?: return@LaunchedEffect
            val viewport = scroll.viewportSize
            if (viewport <= 0) return@LaunchedEffect
            val pad = with(density) { 10.dp.toPx() }
            val start = scroll.value.toFloat()
            val end = start + viewport
            val to = when {
                t.left - pad < start -> t.left - pad
                t.right + pad > end -> t.right + pad - viewport
                else -> return@LaunchedEffect
            }
            scroll.animateScrollTo(to.roundToInt().coerceIn(0, scroll.maxValue))
        }
    }

    // 换行模式（弹框内）不滚动；bounds 记录的是相对同一个父 Box 的位置，滑动胶囊逻辑两版通用
    val container: Modifier = if (wrap) modifier.fillMaxWidth() else modifier.fillMaxWidth().horizontalScroll(scroll)

    Box(container) {
        Box {
            if (placed && pillW.value > 0.5f) {
                val w = pillW.value
                val extra = w * stretch.value
                Box(
                    Modifier
                        // 拉伸以中心为基准，所以左侧多让出半个拉伸量
                        .offset { IntOffset((pillX.value - extra / 2f).roundToInt(), 0) }
                        .width(with(density) { (w + extra).toDp() })
                        .height(height)
                        .clip(CapsuleShape)
                        .background(pillColor),
                )
            }

            if (wrap) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(gap),
                    verticalArrangement = Arrangement.spacedBy(gap),
                ) {
                    tabs.forEach { tab -> ChipTab(tab, selectedKey, placed, height, bounds, onSelect) }
                }
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(gap),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    tabs.forEach { tab -> ChipTab(tab, selectedKey, placed, height, bounds, onSelect) }
                }
            }
        }
    }
}

/** Chip 模式下的单个胶囊 tab，滚动/换行两种布局共用 */
@Composable
private fun ChipTab(
    tab: PillTab,
    selectedKey: String,
    placed: Boolean,
    height: Dp,
    bounds: androidx.compose.runtime.snapshots.SnapshotStateMap<String, Rect>,
    onSelect: (String) -> Unit,
) {
    val on = tab.key == selectedKey
    // 未选中：分类色薄薄一层（16%）+ 虚线描边，整排像一组彩色标签；
    // 用透明度而不是混色，滑动的胶囊从底下经过时能透出来，不会"钻到纸背后消失"。
    val restingFill = tab.accent.copy(alpha = if (Doit.IsDark) 0.22f else 0.16f)
    val bg by animateColorAsState(
        targetValue = if (on) Color.Transparent else restingFill,
        animationSpec = tween(200),
        label = "chipBg",
    )
    val borderAlpha by animateFloatAsState(
        targetValue = if (on) 0f else 1f,
        animationSpec = tween(180),
        label = "chipBorder",
    )
    // 未选中文字：同一色相压暗到能读（分类色直接当字色会太浅）
    val labelColor = remember(tab.accent, Doit.palette) { labelOn(tab.accent) }
    // 选中字色：实底上一律白字（深色模式里如果实底是亮薄荷，会自动退回深墨）
    val onTint = remember(tab.accent) { chipOn(tab.accent) }
    val fg by animateColorAsState(
        targetValue = when {
            // 胶囊还没摆上时先用墨色兜底，摆上之后再翻成实底上的字色
            on && placed -> onTint
            on -> Doit.Ink
            else -> labelColor
        },
        // 选中那格延后 60ms 翻色，等胶囊滑到跟前，避免"字先在浅底上变白"
        animationSpec = tween(durationMillis = 200, delayMillis = if (on && placed) 60 else 0),
        label = "chipFg",
    )
    val badgeColor by animateColorAsState(
        targetValue = if (on && placed) onTint.copy(alpha = 0.7f) else labelColor.copy(alpha = 0.75f),
        animationSpec = tween(durationMillis = 200, delayMillis = if (on && placed) 60 else 0),
        label = "chipBadge",
    )

    Row(
        Modifier
            .height(height)
            .clip(CapsuleShape)
            .background(bg)
            .drawBehind {
                if (borderAlpha > 0.01f) {
                    drawRoundRect(
                        // 虚线交代"这是哪个分类"：0.5 的浓度在浅底上认得出来，又不抢眼
                        color = tab.accent.copy(alpha = 0.5f * borderAlpha),
                        style = Stroke(
                            width = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(
                                floatArrayOf(4.5.dp.toPx(), 3.dp.toPx()),
                            ),
                        ),
                        cornerRadius = CornerRadius(size.height / 2f),
                    )
                }
            }
            .onGloballyPositioned { bounds[tab.key] = it.boundsInParent() }
            .tapFeedback { if (!on) onSelect(tab.key) }
            .padding(horizontal = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            tab.label,
            fontSize = 12.sp,
            // 选中态是白字压在彩色实底上，加半档字重才不显单薄
            fontWeight = if (on) FontWeight.Medium else FontWeight.Normal,
            color = fg,
        )
        tab.badge?.let { n ->
            if (n > 0 || tab.key == "all") {
                Spacer(Modifier.width(5.dp))
                Text(
                    "$n",
                    style = numeric(10).copy(fontWeight = FontWeight.Normal),
                    color = badgeColor,
                )
            }
        }
    }
}
