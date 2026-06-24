package app.tofairy.child.screening.axisb

/** B축 규칙 엔진이 내보내는 사용패턴 신호. 라우터가 intent 로 변환한다. */
enum class UsagePatternSignal {
    /** 한 자리에서 너무 오래 연속 사용. */
    CONTINUOUS_USE,

    /** 늦은 시각 사용. */
    LATE_HOUR,

    /** 짧은 시간 내 잦은 앱 전환(주의 분산/도파민 루프 신호). */
    RAPID_APP_SWITCHING,
}
