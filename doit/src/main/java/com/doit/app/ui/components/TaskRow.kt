package com.doit.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.doit.app.data.TaskNode
import com.doit.app.ui.DoitIcon
import com.doit.app.ui.DoitIconKind
import com.doit.app.ui.tapFeedback
import com.doit.app.ui.gesture.SwipeState
import com.doit.app.ui.gesture.taskSwipe
import com.doit.app.ui.theme.ActionColors
import com.doit.app.ui.theme.FlowSkin
import com.doit.app.ui.theme.Priority
import com.doit.app.ui.theme.Doit
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** 一条待办可能触发的全部动作，父任务与子任务共用 */
class TaskCallbacks(
    val onToggle: (TaskNode) -> Unit,
    val onComplete: (TaskNode) -> Unit,
    val onBlocked: () -> Unit,
    val onEdit: (TaskNode) -> Unit,
    val onPriority: (TaskNode) -> Unit,
    val onCategory: (TaskNode) -> Unit,
    val onSubtask: (TaskNode) -> Unit,
    val onDelete: (TaskNode) -> Unit,
)

/**
 * 子任务组的展开 / 收起状态。
 * 只记「被收起来的那些」，没记过的一律当展开——新加的子任务不会莫名藏着。
 */
class ExpandState {
    private val collapsed = mutableStateMapOf<Long, Boolean>()

    fun isExpanded(id: Long): Boolean = collapsed[id] != true

    fun toggle(id: Long) {
        collapsed[id] = isExpanded(id)
    }
}

// 父任务卡 / 子任务卡共用同一套尺寸，只靠缩进区分层级
private val CardShape = RoundedCornerShape(19.dp)
private const val PAD_H = 13
private const val PAD_V = 10
private val TitleSize = 15.sp

private fun Modifier.cardBackground(shape: RoundedCornerShape) = this
    .clip(shape)
    .background(Doit.Card)
    .border(1.dp, Doit.CardBorder, shape)

/**
 * 左滑时露出的动作区底色：只画在卡片右缘之外。
 * 起点向左多让出一个圆角半径，好把卡片圆角外侧那两块小三角也盖住；
 * 叠在卡片下面的部分会被卡片遮住，所以不会影响卡片本身。
 * 这样卡片哪怕只移开 1px，四角的圆角处也不会漏出深色月牙。
 */
@Composable
private fun ActionBackdrop(state: SwipeState, radius: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val reveal = (-state.offset).coerceIn(0f, size.width)
        if (reveal <= 0.5f) return@Canvas
        val cardRight = size.width - reveal
        val startX = (cardRight - radius.toPx()).coerceAtLeast(0f)
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(Doit.ActionBgStart, Doit.ActionBgEnd),
                startX = cardRight,
                endX = size.width,
            ),
            topLeft = Offset(startX, 0f),
            size = Size(size.width - startX, size.height),
        )
    }
}

/** 展开 / 收起按钮：一个小圆底 + 会转的箭头 */
@Composable
private fun ExpandChevron(expanded: Boolean) {
    val rot by animateFloatAsState(
        targetValue = if (expanded) 0f else -90f,
        animationSpec = spring(dampingRatio = 0.68f, stiffness = 780f),
        label = "chevron",
    )
    Box(
        Modifier
            .size(21.dp)
            .clip(CircleShape)
            .background(Doit.Surface2)
            .border(1.dp, Doit.CardBorderSoft, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        DoitIcon(
            kind = DoitIconKind.Chevron,
            size = 12.dp,
            tint = Doit.InkSoft,
            stroke = 2.dp,
            modifier = Modifier.graphicsLayer { rotationZ = rot },
        )
    }
}

/**
 * 卡片右端的一列：优先级标签在前、展开按钮贴在卡片最右缘，
 * 完成百分比排在展开按钮**正下方** —— 只占右侧这一条窄列，不跟标题抢宽度。
 */
@Composable
private fun CardTrailing(
    hasChildren: Boolean,
    expanded: Boolean,
    prio: Priority,
    doneChildren: Int = 0,
    totalChildren: Int = 0,
) {
    if (prio != Priority.NONE) {
        Spacer(Modifier.width(8.dp))
        PriorityTag(prio, compact = true)
    }
    if (hasChildren) {
        Spacer(Modifier.width(6.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ExpandChevron(expanded)
            Spacer(Modifier.height(2.dp))
            // 百分比跟着展开按钮走：在它下方居中，展开/收起时数字不动
            Text(
                text = percentText(doneChildren, totalChildren),
                fontSize = 9.5.sp,
                color = Doit.InkFaint,
                letterSpacing = 0.2.sp,
            )
        }
    }
}

/** 顶级待办卡片 + 它下面的子任务组 */
@Composable
fun TopTaskRow(
    node: TaskNode,
    skin: FlowSkin,
    haptics: HapticFeedback,
    cb: TaskCallbacks,
    expand: ExpandState,
    showCategory: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val task = node.task
    val density = LocalDensity.current
    // 让位距离 = 动作槽的实测总宽，滑开时刚好露完。
    // 已完成的任务只露「编辑｜删除」两个槽；未完成的露「编辑｜优先级｜分类｜子任务｜删除」全部五个。
    val actionCount = if (task.done) 1 else 5
    val actionWidth = ActionMetrics.widthOf(actionCount)
    val actionPx = with(density) { actionWidth.toPx() }
    // 已完成也能左滑删除：enabled 始终为真；右滑完成只在未完成时有效
    val state = remember(task.id, task.done, node.hasChildren) {
        SwipeState(actionWidthPx = actionPx, blocked = node.hasChildren, enabled = true)
    }
    val scope = rememberCoroutineScope()
    val shake = remember { Animatable(0f) }
    val expanded = expand.isExpanded(task.id)
    val prio = Priority.fromLevel(task.priority)

    fun shakeIt() {
        scope.launch {
            shake.snapTo(0f)
            shake.animateTo(
                targetValue = 0f,
                animationSpec = keyframes {
                    durationMillis = 320
                    -4f at 60
                    3f at 150
                    -2f at 230
                    0f at 320
                },
            )
        }
    }

    fun toggleExpand() {
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        expand.toggle(task.id)
    }

    Column(modifier.fillMaxWidth().padding(bottom = 9.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(CardShape)
                .taskSwipe(
                    state = state,
                    scope = scope,
                    onComplete = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        cb.onComplete(node)
                    },
                    onBlocked = {
                        shakeIt()
                        cb.onBlocked()
                    },
                    onHaptic = { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
                    allowComplete = !task.done,
                ),
        ) {
            // 动作区底色（只覆盖卡片让开的那部分）
            ActionBackdrop(state, radius = 19.dp, modifier = Modifier.matchParentSize())

            // 左滑让开后露出的动作：未完成的可编辑（编辑/优先级/分类/子任务）+ 删除；
            // 已完成的不提供编辑入口，只露「删除」（历史完成记录不允许再改）。
            Row(
                modifier = Modifier
                    .matchParentSize()
                    .padding(end = ActionMetrics.Edge),
                horizontalArrangement = Arrangement.spacedBy(ActionMetrics.Gap, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!task.done) {
                    RowScopeAction("编辑", DoitIconKind.Pencil, ActionColors.Edit) {
                        state.reset(); cb.onEdit(node)
                    }
                    RowScopeAction("优先级", DoitIconKind.Flag, ActionColors.Priority) {
                        state.reset(); cb.onPriority(node)
                    }
                    RowScopeAction("分类", DoitIconKind.Folder, ActionColors.Category) {
                        state.reset(); cb.onCategory(node)
                    }
                    RowScopeAction("子任务", DoitIconKind.Sub, ActionColors.Subtask) {
                        state.reset(); cb.onSubtask(node)
                    }
                }
                RowScopeAction("删除", DoitIconKind.Trash, ActionColors.Delete) {
                    state.reset(); cb.onDelete(node)
                }
            }

            // 卡片本体：跟着手指横向平移
            Box(
                Modifier
                    .offset { IntOffset((state.offset + shake.value).roundToInt(), 0) }
                    .fillMaxWidth()
                    .cardBackground(CardShape)
                    // 点卡片不勾选：勾选走左侧圆圈，右滑完成；
                    // 有子任务时点一下是展开/收起（右侧箭头跟着转），已展开的动作区点一下收回
                    .tapFeedback {
                        when {
                            state.open -> state.reset()
                            node.hasChildren -> toggleExpand()
                        }
                    },
            ) {
                if (state.fill > 0.001f) {
                    Box(Modifier.matchParentSize()) {
                        FillLayer(
                            skin = skin,
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(state.fill.coerceIn(0f, 1f)),
                        )
                    }
                }
                // 圆圈 | 文字列 | 右侧（展开箭头 + 优先级），整行垂直居中
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                        .padding(horizontal = PAD_H.dp, vertical = PAD_V.dp)
                        .alpha(if (task.done) 0.5f else 1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 勾选圆圈：点击热区只有圆圈本身
                    Box(
                        Modifier.tapFeedback {
                            if (node.hasChildren) {
                                shakeIt(); cb.onBlocked()
                            } else {
                                cb.onToggle(node)
                            }
                        },
                    ) {
                        TaskCheck(
                            done = task.done,
                            locked = node.locked,
                            p = state.p,
                            ring = skin.ring,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        StrikeText(
                            text = task.title,
                            style = TextStyle(
                                fontSize = TitleSize,
                                lineHeight = 21.sp,
                                color = Doit.Ink,
                                letterSpacing = 0.1.sp,
                                fontWeight = if (node.ready) FontWeight.SemiBold else FontWeight.Normal,
                            ),
                            done = task.done,
                        )
                        Spacer(Modifier.height(3.dp))
                        // 分类 · 时间在左；子任务进度挪到右侧箭头下方
                        MetaRow(task, showCategory = showCategory)
                    }
                    CardTrailing(
                        hasChildren = node.hasChildren,
                        expanded = expanded,
                        prio = prio,
                        doneChildren = node.doneChildren,
                        totalChildren = node.children.size,
                    )
                }
            }
        }

        if (node.hasChildren) {
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(spring(dampingRatio = 0.82f, stiffness = 620f)) + fadeIn(tween(160)),
                exit = shrinkVertically(spring(dampingRatio = 0.9f, stiffness = 720f)) + fadeOut(tween(120)),
            ) {
                SubtaskGroup(node = node, depth = 1, skin = skin, haptics = haptics, cb = cb, expand = expand)
            }
        }
    }
}

/** 一组子任务：只靠缩进看出层级，不再画竖线 */
@Composable
fun SubtaskGroup(
    node: TaskNode,
    depth: Int,
    skin: FlowSkin,
    haptics: HapticFeedback,
    cb: TaskCallbacks,
    expand: ExpandState,
) {
    val indent = if (depth == 1) 20.dp else 14.dp
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 7.dp, start = indent),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        node.children.forEach { child ->
            SubtaskRow(node = child, depth = depth, skin = skin, haptics = haptics, cb = cb, expand = expand)
        }
    }
}

/**
 * 一条子任务：和父任务卡同一套尺寸与排版（圆角、内边距、字号、圆圈都一样大），
 * 只靠外层的缩进和引导线看出层级。
 */
@Composable
private fun SubtaskRow(
    node: TaskNode,
    depth: Int,
    skin: FlowSkin,
    haptics: HapticFeedback,
    cb: TaskCallbacks,
    expand: ExpandState,
) {
    val task = node.task
    val done = task.done
    // 已完成只剩「编辑｜删除」；未完成的是「编辑｜优先级｜子任务｜删除」
    val actionCount = if (done) 1 else 4
    val actionWidth = ActionMetrics.widthOf(actionCount)
    val density = LocalDensity.current
    val actionPx = with(density) { actionWidth.toPx() }
    // 已完成也能左滑删除：enabled 始终为真；右滑完成只在未完成时有效
    val state = remember(task.id, done, node.hasChildren) {
        SwipeState(actionWidthPx = actionPx, blocked = node.hasChildren, enabled = true)
    }
    val scope = rememberCoroutineScope()
    val shake = remember { Animatable(0f) }
    val expanded = expand.isExpanded(task.id)
    val prio = Priority.fromLevel(task.priority)

    fun shakeIt() {
        scope.launch {
            shake.snapTo(0f)
            shake.animateTo(
                targetValue = 0f,
                animationSpec = keyframes {
                    durationMillis = 320
                    -4f at 60
                    3f at 150
                    -2f at 230
                    0f at 320
                },
            )
        }
    }

    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(CardShape)
                .taskSwipe(
                    state = state,
                    scope = scope,
                    onComplete = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        cb.onComplete(node)
                    },
                    onBlocked = {
                        shakeIt(); cb.onBlocked()
                    },
                    onHaptic = { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
                    allowComplete = !done,
                ),
        ) {
            ActionBackdrop(state, radius = 19.dp, modifier = Modifier.matchParentSize())

            Row(
                modifier = Modifier
                    .matchParentSize()
                    .padding(end = ActionMetrics.Edge),
                horizontalArrangement = Arrangement.spacedBy(ActionMetrics.Gap, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 未完成：编辑/优先级/子任务（子任务本就无「分类」动作，分类跟随父任务）+ 删除；
                // 已完成：不提供编辑入口，只露「删除」。
                if (!done) {
                    RowScopeAction(null, DoitIconKind.Pencil, ActionColors.Edit) {
                        state.reset(); cb.onEdit(node)
                    }
                    RowScopeAction(null, DoitIconKind.Flag, ActionColors.Priority) {
                        state.reset(); cb.onPriority(node)
                    }
                    RowScopeAction(null, DoitIconKind.Sub, ActionColors.Subtask) {
                        state.reset(); cb.onSubtask(node)
                    }
                }
                RowScopeAction(null, DoitIconKind.Trash, ActionColors.Delete) {
                    state.reset(); cb.onDelete(node)
                }
            }

            Box(
                Modifier
                    .offset { IntOffset((state.offset + shake.value).roundToInt(), 0) }
                    .fillMaxWidth()
                    .cardBackground(CardShape)
                    // 同主卡：点卡片不勾选；有下一级时点一下展开/收起，否则点一下只收起动作区
                    .tapFeedback {
                        when {
                            state.open -> state.reset()
                            node.hasChildren -> {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                expand.toggle(task.id)
                            }
                        }
                    },
            ) {
                if (state.fill > 0.001f) {
                    Box(Modifier.matchParentSize()) {
                        FillLayer(
                            skin = skin,
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(state.fill.coerceIn(0f, 1f)),
                        )
                    }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                        .padding(horizontal = PAD_H.dp, vertical = PAD_V.dp)
                        .alpha(if (done) 0.5f else 1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.tapFeedback {
                            if (node.hasChildren) {
                                shakeIt(); cb.onBlocked()
                            } else {
                                cb.onToggle(node)
                            }
                        },
                    ) {
                        TaskCheck(
                            done = done,
                            locked = node.locked,
                            p = state.p,
                            ring = skin.ring,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        StrikeText(
                            text = task.title,
                            style = TextStyle(
                                fontSize = TitleSize,
                                lineHeight = 21.sp,
                                color = Doit.Ink,
                                letterSpacing = 0.1.sp,
                                fontWeight = if (node.ready) FontWeight.SemiBold else FontWeight.Normal,
                            ),
                            done = done,
                        )
                        Spacer(Modifier.height(3.dp))
                        // 子任务的分类跟父任务一致，只留时间，免得每行重复一遍分类名
                        MetaRow(task, showCategory = false)
                    }
                    CardTrailing(
                        hasChildren = node.hasChildren,
                        expanded = expanded,
                        prio = prio,
                        doneChildren = node.doneChildren,
                        totalChildren = node.children.size,
                    )
                }
            }
        }

        if (node.hasChildren) {
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(spring(dampingRatio = 0.82f, stiffness = 620f)) + fadeIn(tween(160)),
                exit = shrinkVertically(spring(dampingRatio = 0.9f, stiffness = 720f)) + fadeOut(tween(120)),
            ) {
                SubtaskGroup(
                    node = node,
                    depth = depth + 1,
                    skin = skin,
                    haptics = haptics,
                    cb = cb,
                    expand = expand,
                )
            }
        }
    }
}
