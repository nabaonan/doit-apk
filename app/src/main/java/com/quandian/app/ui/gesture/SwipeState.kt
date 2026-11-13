package com.quandian.app.ui.gesture

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min

/** 视觉稿里的动效曲线 --ease: cubic-bezier(.16,1,.3,1) */
val EaseOutExpo = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

/**
 * 一条待办的滑动状态。
 * - offset：卡片横向位移（左滑为负）
 * - fill：进度层宽度占比 0..1
 * - p：「上膛」进度 0..1，驱动勾选圈外的进度环与对勾的描出
 */
class SwipeState(
    val actionWidthPx: Float,
    val blocked: Boolean,
    val enabled: Boolean,
) {
    var offset by mutableFloatStateOf(0f)
    var fill by mutableFloatStateOf(0f)
    var p by mutableFloatStateOf(0f)
    var open by mutableStateOf(false)
    var dragging by mutableStateOf(false)
    var armed by mutableStateOf(false)
    internal var job: Job? = null

    fun reset() {
        job?.cancel()
        offset = 0f; fill = 0f; p = 0f; open = false; armed = false; dragging = false
    }
}

/**
 * 双向滑动：右滑跟手完成（过半上膛），左滑让开露出动作区。
 * 与视觉稿 index.html 的判定完全一致：p = d / (w * 0.55)。
 */
fun Modifier.taskSwipe(
    state: SwipeState,
    scope: CoroutineScope,
    onComplete: () -> Unit,
    onBlocked: () -> Unit,
    onHaptic: () -> Unit,
    allowComplete: Boolean = true,
): Modifier = pointerInput(state) {
    if (!state.enabled) return@pointerInput
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        state.job?.cancel()
        var dx = 0f
        var dy = 0f
        var decided = 0 // 0 未定 / 1 左滑动作 / 2 右滑完成 / -1 纵向滚动
        val startOffset = state.offset
        val width = size.width.toFloat()
        val aw = state.actionWidthPx
        var armedFired = false

        // 手势方向一旦定了就不再改；抽成函数是因为抬手事件也要走一遍结算
        fun decideIfNeeded() {
            if (decided != 0) return
            val slop = viewConfiguration.touchSlop
            if (abs(dx) <= slop && abs(dy) <= slop) return
            decided = when {
                abs(dy) > abs(dx) -> -1
                startOffset < 0f || dx < 0f -> 1
                allowComplete -> 2
                else -> -1
            }
            if (decided > 0) state.dragging = true
        }

        fun applyDrag() {
            when (decided) {
                1 -> {
                    var off = startOffset + dx
                    if (off < -aw) off = -aw - (-aw - off) * 0.2f
                    state.offset = off.coerceIn(-aw - 26.dp.toPx(), 0f)
                }

                2 -> {
                    val raw = dx.coerceAtLeast(0f)
                    val d = if (state.blocked) min(raw, 24.dp.toPx()) else min(raw, width)
                    state.fill = if (width > 0f) d / width else 0f
                    state.p = if (state.blocked || width <= 0f) 0f else (d / (width * 0.55f)).coerceAtMost(1f)
                    if (!armedFired && state.p >= 1f) {
                        armedFired = true
                        state.armed = true
                        onHaptic()
                    }
                }

                -1 -> Unit // 纵向手势交还给列表滚动
            }
        }

        while (true) {
            val event = awaitPointerEvent()
            val change: PointerInputChange = event.changes.firstOrNull { it.id == down.id } ?: break
            // 先累加再判抬起：快速滑动时 MOVE 常与 UP 合批成一个事件，
            // 若先 break 会把这最后一段位移（往往是大半程）整段丢掉，快滑就白滑了
            dx += change.positionChange().x
            dy += change.positionChange().y
            decideIfNeeded()
            applyDrag()
            if (decided > 0) change.consume()
            if (!change.pressed) break
            if (decided == -1) break
        }

        state.dragging = false
        state.armed = false


        when (decided) {
            1 -> {
                val target = if (state.offset <= -aw * 0.5f) -aw else 0f
                state.job = scope.launch {
                    animate(state.offset, target, animationSpec = tween(250, easing = EaseOutExpo)) { v, _ ->
                        state.offset = v
                    }
                    state.open = target != 0f
                }
            }

            2 -> {
                if (state.blocked) {
                    state.job = scope.launch {
                        animate(state.fill, 0f, animationSpec = tween(200)) { v, _ -> state.fill = v }
                        onBlocked()
                    }
                } else if (state.p >= 1f) {
                    state.job = scope.launch {
                        animate(state.fill, 1f, animationSpec = tween(170)) { v, _ -> state.fill = v }
                        onComplete()
                        delay(420)
                        state.fill = 0f
                        state.p = 0f
                    }
                } else {
                    // 松手没到阈值：填充层和进度环一起退回初始状态，
                    // 环跟着卡片同速缩回，而不是等卡片收完再缩
                    state.job = scope.launch {
                        launch {
                            animate(state.fill, 0f, animationSpec = tween(300, easing = EaseOutExpo)) { v, _ -> state.fill = v }
                        }
                        launch {
                            animate(state.p, 0f, animationSpec = tween(300, easing = EaseOutExpo)) { v, _ -> state.p = v }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun rememberSwipeState(actionWidthPx: Float, blocked: Boolean, enabled: Boolean): SwipeState =
    remember(actionWidthPx, blocked, enabled) { SwipeState(actionWidthPx, blocked, enabled) }

@Composable
fun rememberSwipeScope(): CoroutineScope = rememberCoroutineScope()
