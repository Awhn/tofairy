package app.tofairy.child.screening.axisb

/**
 * B축(중독성) — 순수 규칙 엔진 (CLAUDE.md §6: "LLM 불필요, 가볍고 결정적").
 *
 * 입력은 집계된 사용 지표(콘텐츠 원문 아님)이므로 ephemeral 이 아니다.
 * 출력은 [UsagePatternSignal] 의 집합 — 라우터가 휴식 권유 등으로 변환한다.
 */
class UsagePatternEngine(
    private val config: Config = Config(),
) {
    data class Config(
        /** 연속 사용 임계(분). */
        val continuousUseMinutes: Int = 40,
        /** 늦은 시각 시작(24h). */
        val lateHourStart: Int = 21,
        /** 늦은 시각 끝(다음날 새벽). */
        val lateHourEnd: Int = 5,
        /** 급격한 앱 전환 임계(최근 5분 내 전환 횟수). */
        val rapidSwitchCountIn5Min: Int = 12,
    )

    /** 라우터로 넘길 집계 스냅샷. 원시 신호가 아니라 누적/파생 지표. */
    data class UsageSnapshot(
        val continuousForegroundMinutes: Int,
        val localHourOfDay: Int,
        val appSwitchesLast5Min: Int,
    )

    fun evaluate(snapshot: UsageSnapshot): Set<UsagePatternSignal> = buildSet {
        if (snapshot.continuousForegroundMinutes >= config.continuousUseMinutes) {
            add(UsagePatternSignal.CONTINUOUS_USE)
        }
        if (isLateHour(snapshot.localHourOfDay)) {
            add(UsagePatternSignal.LATE_HOUR)
        }
        if (snapshot.appSwitchesLast5Min >= config.rapidSwitchCountIn5Min) {
            add(UsagePatternSignal.RAPID_APP_SWITCHING)
        }
    }

    private fun isLateHour(hour: Int): Boolean =
        hour >= config.lateHourStart || hour < config.lateHourEnd
}
