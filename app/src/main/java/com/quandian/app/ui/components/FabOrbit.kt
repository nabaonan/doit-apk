package com.quandian.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.quandian.app.ui.QIcon
import com.quandian.app.ui.QIconKind
import com.quandian.app.ui.tapFeedback
import com.quandian.app.ui.theme.QD
import kotlinx.coroutines.launch

/** 加号上半弧的四颗小圆：添加分类 / 语音输入 / 热力图 / 设置（只画图标，说明文字走无障碍语义） */
enum class Satellite(val label: String, val icon: QIconKind, val x: Dp, val y: Dp) {
    Category("添加分类", QIconKind.FolderPlus, (-104).dp, (-56).dp),
    Voice("语音输入", QIconKind.Mic, (-44).dp, (-112).dp),
    Stats("热力图", QIconKind.Heat, 44.dp, (-112).dp),
    Settings("设置", QIconKind.Gear, 104.dp, (-56).dp),
}

/**
 * 轻点 ＋ 记一条，长按 ＋ 绽开四颗卫星。
 * 底栏高度：FAB 底边距屏幕 34dp，直径 66dp，卫星沿半径约 118dp 的弧排开。
 */
@Composable
fun FabOrbit(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onTap: () -> Unit,
    onSatellite: (Satellite) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val progress by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 420f),
        label = "satelliteProgress",
    )

    Box(modifier.fillMaxSize()) {
        if (expanded) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(QD.Ink.copy(alpha = 0.06f))
                    // 整屏遮罩只要按压回弹，不要一颗铺满全屏的水波纹
                    .tapFeedback(rippleColor = null) { onExpandedChange(false) },
            )
        }

        Satellite.entries.forEach { sat ->
            val alpha = progress
            val scrimSize = 48.dp
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(
                        x = sat.x * progress,
                        y = (-43).dp + sat.y * progress,
                    )
                    .size(scrimSize)
                    .graphicsLayer {
                        scaleX = 0.4f + 0.6f * progress
                        scaleY = 0.4f + 0.6f * progress
                        this.alpha = alpha
                    },
            ) {
                // 圆形按钮本体：只有图标，没有文字
                Box(
                    Modifier
                        .fillMaxSize()
                        .shadow(12.dp, CircleShape)
                        .clip(CircleShape)
                        .background(QD.Surface)
                        .border(1.dp, QD.Line, CircleShape)
                        .semantics { contentDescription = sat.label }
                        .tapFeedback(enabled = expanded) { onSatellite(sat) },
                    contentAlignment = Alignment.Center,
                ) {
                    QIcon(
                        kind = sat.icon,
                        size = 22.dp,
                        tint = QD.Ink,
                        stroke = 1.7.dp,
                    )
                }
            }
        }

        // 加号本体
        val scale = remember { Animatable(1f) }
        val rotation by animateFloatAsState(
            targetValue = if (expanded) 45f else 0f,
            animationSpec = tween(340),
            label = "fabRotation",
        )
        val scope = rememberCoroutineScope()
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = (-34).dp)
                .size(66.dp)
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                }
                .shadow(18.dp, CircleShape)
                .clip(CircleShape)
                .background(QD.AccentFill)
                .pointerInput(expanded) {
                    detectTapGestures(
                        onPress = {
                            scope.launch { scale.animateTo(0.9f, tween(120)) }
                            val released = tryAwaitRelease()
                            scope.launch {
                                scale.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 500f))
                            }
                            if (!released) Unit
                        },
                        onLongPress = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onExpandedChange(true)
                        },
                        onTap = {
                            if (expanded) onExpandedChange(false) else onTap()
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.graphicsLayer { rotationZ = rotation }) {
                QIcon(kind = QIconKind.Plus, size = 26.dp, tint = QD.OnAccent, stroke = 2.dp)
            }
        }
    }
}
