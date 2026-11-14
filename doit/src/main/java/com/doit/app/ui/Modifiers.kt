package com.doit.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.ripple
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import com.doit.app.ui.theme.Doit

/**
 * 统一的点击反馈：按下时整块轻微压暗 + 形状内水波纹。
 *
 * 水波纹取主题色（[Doit.Accent]），压暗取墨色——两者都来自调色板，
 * 换主题时点击反馈的颜色自动跟随，不用逐处改。
 *
 * 压暗用 `drawWithContent` 画在内容之上（不是 background），
 * 这样不管这个修饰符拼在 `clip/background` 之后多久，都能罩住整个元素；
 * 也因此不会出现"只有文字在缩放、底板不动"的割裂感。
 *
 * @param dim 按下时的压暗强度，0 表示只要波纹
 * @param rippleColor null 表示不要水波纹（整屏遮罩这类地方用）
 */
fun Modifier.tapFeedback(
    enabled: Boolean = true,
    dim: Float = 0.11f,
    rippleColor: Color? = Doit.Accent,
    onClick: () -> Unit,
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val overlay by animateFloatAsState(
        targetValue = if (pressed && enabled) 1f else 0f,
        animationSpec = tween(90),
        label = "pressOverlay",
    )
    this
        .clickable(
            interactionSource = interaction,
            indication = rippleColor
                ?.let { ripple(bounded = true, color = it) }
                ?: ripple(bounded = true),
            enabled = enabled,
            onClick = onClick,
        )
        .drawWithContent {
            drawContent()
            if (overlay > 0.01f && dim > 0f) {
                drawRect(color = Doit.Ink, alpha = dim * overlay)
            }
        }
}
