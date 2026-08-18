package app.tofairy.child.router

import app.tofairy.child.screening.axisa.AgeSuitability
import app.tofairy.child.screening.axisb.UsagePatternSignal

/**
 * 라우터 입력 컨텍스트 (판단 결과 · 시간대 · 최근 상호작용).
 *
 * 여기에는 screenshot·콘텐츠 원문·dimension별 민감 판정이 들어오지 않는다. 일일 A축은
 * 암호화 임시 샘플을 집계한 뒤 원본과 중간 판정을 폐기하고 허용된 enum/집계만 전달한다.
 */
data class RouterContext(
    val dayMoment: DayMoment,
    /** B축 사용패턴 규칙 엔진의 신호(없으면 비어 있음). */
    val usageSignals: List<UsagePatternSignal> = emptyList(),
    /** 일일 A축 집계가 만든 현재 연령 경계 판정(없으면 null = 관련 개입 없음). */
    val ageSuitability: AgeSuitability? = null,
    /** 약속 진행 상태(0..1, null = 약속 미설정). */
    val promiseProgress: Float? = null,
    /** 직전 intent id — 같은 개입을 연달아 반복하지 않기 위한 디바운스 힌트. */
    val lastIntentId: String? = null,
    /** 마지막 상호작용 이후 경과(분). 너무 잦은 개입 억제용. */
    val minutesSinceLastInteraction: Int = Int.MAX_VALUE,
) {
    enum class DayMoment { MORNING, AFTERNOON, EVENING, LATE_NIGHT }
}
