package app.tofairy.child.fairy

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * 요정 렌더링·애니메이션 (CLAUDE.md §3 fairy 모듈).
 *
 * "creepy 하지 않은 살아있는 요정"이 핵심 데모 자산(§10). 부드러운 호흡/부유/반짝임으로
 * 살아있는 느낌을 주되 과한 자극은 피한다. 실제 아트는 추후 일러스트/스파인 교체 가능.
 *
 * 이 컴포저블은 무드(상태)만 받아 표현한다. 아이에게 향하는 '문구'는 여기서 만들지 않는다(불변식 #3).
 */
@Composable
fun FairyView(
    mood: FairyMood,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "fairy")

    // 호흡(크기 맥동)
    val breath by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = mood.breathPeriodMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "breath",
    )

    // 부유(상하 이동)
    val floatPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "float",
    )

    // 반짝임(글로우 회전)
    val shimmer by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer",
    )

    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f + sin(floatPhase) * 14.dp.toPx())
        val baseRadius = size.minDimension * 0.18f
        val pulse = 1f + 0.06f * sin(breath)
        val radius = baseRadius * pulse

        drawAura(center, radius, mood, shimmer)
        drawSparkles(center, radius, mood, shimmer)
        drawBody(center, radius, mood)
    }
}

private fun DrawScope.drawAura(center: Offset, radius: Float, mood: FairyMood, shimmer: Float) {
    val auraColor = mood.glowColor.copy(alpha = 0.35f + 0.1f * (0.5f + 0.5f * sin(shimmer)))
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(auraColor, Color.Transparent),
            center = center,
            radius = radius * 2.6f,
        ),
        radius = radius * 2.6f,
        center = center,
    )
}

private fun DrawScope.drawBody(center: Offset, radius: Float, mood: FairyMood) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color.White, mood.coreColor),
            center = Offset(center.x - radius * 0.3f, center.y - radius * 0.3f),
            radius = radius * 1.4f,
        ),
        radius = radius,
        center = center,
    )
    // 가벼운 외곽 링
    drawCircle(
        color = mood.glowColor.copy(alpha = 0.5f),
        radius = radius,
        center = center,
        style = Stroke(width = 2.dp.toPx()),
    )
}

private fun DrawScope.drawSparkles(center: Offset, radius: Float, mood: FairyMood, shimmer: Float) {
    val count = mood.sparkleCount
    repeat(count) { i ->
        val angle = shimmer + i * (2f * Math.PI.toFloat() / count)
        val dist = radius * (1.7f + 0.25f * sin(shimmer * 1.3f + i))
        val p = Offset(center.x + cos(angle) * dist, center.y + sin(angle) * dist)
        val r = (radius * 0.06f) * (0.6f + 0.4f * sin(shimmer * 2f + i))
        drawCircle(color = mood.glowColor.copy(alpha = 0.8f), radius = r, center = p)
    }
}
