package app.tofairy.child.router

import app.tofairy.child.screening.axisa.AgeAppropriateness
import app.tofairy.child.screening.axisb.UsagePatternSignal

/**
 * 라우터 입력 컨텍스트 (판단 결과 · 시간대 · 최근 상호작용).
 *
 * 주의(불변식 #1/#2): 여기에는 콘텐츠 원문/민감 라벨이 들어오지 않는다.
 * screening 단계에서 ephemeral 입력을 소비하고 폐기한 뒤, '판단 결과'(enum/집계)만 전달된다.
 */
data class RouterContext(
    val dayMoment: DayMoment,
    /** B축 사용패턴 규칙 엔진의 신호(없으면 비어 있음). */
    val usageSignals: List<UsagePatternSignal> = emptyList(),
    /** A축 적절성 판단 결과(없으면 null = 관련 콘텐츠 없음). */
    val appropriateness: AgeAppropriateness? = null,
    /** 약속 진행 상태(0..1, null = 약속 미설정). */
    val promiseProgress: Float? = null,
    /** 직전 intent id — 같은 개입을 연달아 반복하지 않기 위한 디바운스 힌트. */
    val lastIntentId: String? = null,
    /** 마지막 상호작용 이후 경과(분). 너무 잦은 개입 억제용. */
    val minutesSinceLastInteraction: Int = Int.MAX_VALUE,
) {
    enum class DayMoment { MORNING, AFTERNOON, EVENING, LATE_NIGHT }
}
