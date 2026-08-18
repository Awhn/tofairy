package app.tofairy.child.screening.axisa

/** checkpoint sampler 입력. 정확한 시간·일일 개수는 구현/실험 구성으로 두고 여기서 고정하지 않는다. */
data class SamplingContext(
    val sessionId: String,
    val reason: SamplingReason,
    val elapsedSinceLastSampleMillis: Long?,
    val sessionDurationMillis: Long,
    val transitionContext: ScreenTransitionContext,
)

/** 모든 화면 연속 캡처를 금지하고 의미 있는 checkpoint만 선택하는 정책 경계. */
fun interface ScreeningSampler {
    fun shouldCapture(context: SamplingContext): Boolean
}
