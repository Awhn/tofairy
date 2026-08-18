package app.tofairy.child.fairy

import androidx.compose.animation.core.InfiniteTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.State
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

private const val TWO_PI = (2.0 * Math.PI).toFloat()

/**
 * 요정 렌더링·애니메이션 (CLAUDE.md §3 fairy 모듈).
 *
 * "creepy 하지 않은 살아있는 요정"이 핵심 데모 자산(§10). 부드러운 날갯짓·호흡·부유·반짝임으로
 * 살아있는 느낌을 주되 과한 자극은 피한다. 얼굴은 일부러 그리지 않아 uncanny(불쾌한 골짜기)를 피한다.
 * 실제 아트는 추후 일러스트/스파인 교체 가능.
 *
 * 이 컴포저블은 무드(상태)만 받아 표현한다. 아이에게 향하는 '문구'는 여기서 만들지 않는다(불변식 #3).
 */
@Composable
fun FairyView(
    mood: FairyMood,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "fairy")
    val breath by transition.phase(mood.breathPeriodMs, label = "breath")
    val floatPhase by transition.phase(periodMs = 3800, label = "float")
    val shimmer by transition.phase(periodMs = 2600, label = "shimmer")

    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f + sin(floatPhase) * 14.dp.toPx())
        val baseRadius = size.minDimension * 0.18f
        val pulse = 1f + 0.06f * sin(breath)
        val radius = baseRadius * pulse
        // 날개 펼침: 호흡에 맞춰 0.85~1.0 사이로 부드럽게 열고 닫는다(기계적이지 않게).
        val wingOpenness = 0.85f + 0.15f * (0.5f + 0.5f * sin(breath))

        drawAura(center, radius, mood, shimmer)
        drawWings(center, radius, mood, wingOpenness)
        drawSparkles(center, radius, mood, shimmer)
        drawBody(center, radius, mood)
    }
}

/** 0→2π 로 선형 순환하는 무한 위상 애니메이션. 재시작 시 sin/cos 가 연속이라 끊김이 없다. */
@Composable
private fun InfiniteTransition.phase(periodMs: Int, label: String): State<Float> =
    animateFloat(
        initialValue = 0f,
        targetValue = TWO_PI,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = periodMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = label,
    )

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

/** 좌우 한 쌍의 투명한 날개. 호흡(openness)에 따라 살짝 각도가 벌어지며 살아있는 느낌을 준다. */
private fun DrawScope.drawWings(center: Offset, radius: Float, mood: FairyMood, openness: Float) {
    val wingWidth = radius * 1.7f
    val wingHeight = radius * 1.05f
    // 닫힐수록(=openness 작을수록) 바깥으로 더 들리는 부드러운 날갯짓(최대 ~7°).
    val flap = (1f - openness) * 46f
    val wingBrush = Brush.linearGradient(
        colors = listOf(mood.glowColor.copy(alpha = 0.30f), mood.coreColor.copy(alpha = 0.10f)),
    )
    // 오른쪽 날개 (위로 살짝 들린 형태 → 반시계 방향)
    rotate(degrees = -(20f + flap), pivot = center) {
        drawOval(
            brush = wingBrush,
            topLeft = Offset(center.x + radius * 0.2f, center.y - wingHeight / 2f),
            size = Size(wingWidth, wingHeight),
        )
    }
    // 왼쪽 날개 (대칭 → 시계 방향)
    rotate(degrees = 20f + flap, pivot = center) {
        drawOval(
            brush = wingBrush,
            topLeft = Offset(center.x - radius * 0.2f - wingWidth, center.y - wingHeight / 2f),
            size = Size(wingWidth, wingHeight),
        )
    }
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
        val angle = shimmer + i * (TWO_PI / count)
        val dist = radius * (1.7f + 0.25f * sin(shimmer * 1.3f + i))
        val p = Offset(center.x + cos(angle) * dist, center.y + sin(angle) * dist)
        val r = (radius * 0.06f) * (0.6f + 0.4f * sin(shimmer * 2f + i))
        drawCircle(color = mood.glowColor.copy(alpha = 0.8f), radius = r, center = p)
    }
}
