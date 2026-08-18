package app.tofairy.child.core

/**
 * 불변식 #3 — Router는 화자가 아니다.
 *
 * 현재 규칙 Router와 향후 별도 평가할 ML Router의 출력은 오직 이 [FairyIntent]다.
 * A축 Shieldstral의 binary 분류 타입은 별도 screening package에 있고 이 계약과 섞지 않는다.
 * 어떤 모델의 자유 텍스트도 child-facing 경로에 닿지 않는다.
 * 아이가 듣는 문장은 전량 responsebank 에서 선택된다.
 *
 * 따라서 이 타입에는 자유 텍스트 필드가 없다. (모델이 채울 수 있는 message/utterance 필드 금지)
 * intent 이름과 enum/수치 슬롯만 가진다 — function-call 스키마와 1:1 대응.
 */
sealed interface FairyIntent {
    /** responsebank 조회 키. 안정적인 식별자여야 한다(자산 JSON 의 intent id 와 일치). */
    val id: String

    /** 휴식 권유(B축 사용패턴 신호). */
    data class SuggestBreak(val reason: BreakReason) : FairyIntent {
        override val id = "suggest_break"
    }

    /** 약속한 사용 시간 초과 임박/도달 안내. */
    data class PromiseCheckIn(val state: PromiseState) : FairyIntent {
        override val id = "promise_check_in"
    }

    /** 일일 A축 집계 뒤 현재 연령 경계 초과가 있었을 때의 부드러운 회고(실시간 차단 아님). */
    data object GentleContentPrompt : FairyIntent {
        override val id = "gentle_content_prompt"
    }

    /** 일상적 관계 인사/안부(트리거 없는 기본 상호작용). */
    data class Greeting(val moment: DayMoment) : FairyIntent {
        override val id = "greeting"
    }

    /** 약속 달성 축하/격려. */
    data class Encourage(val occasion: EncourageOccasion) : FairyIntent {
        override val id = "encourage"
    }

    /** 온보딩: 각성 의식 단계 안내. */
    data class AwakeningStep(val step: Awakening) : FairyIntent {
        override val id = "awakening_step"
    }

    /** 라우터가 개입 불필요로 판단(요정은 조용히 곁에 있음). */
    data object Idle : FairyIntent {
        override val id = "idle"
    }

    enum class BreakReason { CONTINUOUS_USE, LATE_HOUR, RAPID_SWITCHING }
    enum class PromiseState { APPROACHING_LIMIT, AT_LIMIT, OVER_LIMIT, KEPT }
    enum class DayMoment { MORNING, AFTERNOON, EVENING }
    enum class EncourageOccasion { PROMISE_KEPT, STREAK, SELF_STOPPED }
    enum class Awakening { ARRIVAL, NAMING, FIRST_BOND }
}
