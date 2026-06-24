package app.tofairy.child.localstore

import kotlinx.serialization.Serializable

/**
 * 관계·기억·집계 상태 (CLAUDE.md §5).
 *
 * 불변식 #1/#2: 여기에는 원시 콘텐츠·민감 라벨이 절대 들어오지 않는다.
 * A축 라벨은 분류 직후 폐기되며, 이 상태에는 집계 지표·관계 상태만 남는다.
 */
@Serializable
data class RelationshipState(
    /** 요정 이름(온보딩 네이밍). child-facing 식별자. */
    val fairyName: String? = null,
    /** 각성 의식 완료 여부. */
    val awakened: Boolean = false,
    /** 유대 점수(관계 진행, 집계). */
    val bondLevel: Int = 0,
    /** 연속 약속 달성일수(집계). */
    val promiseStreakDays: Int = 0,
    /** 누적 휴식 권유 수락 횟수(집계, 효과 측정용). */
    val breaksAccepted: Int = 0,
    /** 마지막 상호작용 시각(epoch millis, 집계). */
    val lastInteractionAt: Long = 0L,
) {
    /** 디스크/로그/네트워크 어디에도 평문 라벨이 없는지 보장하기 위한 표식(이 타입엔 라벨 필드 자체가 없다). */
    companion object {
        const val SCHEMA_VERSION = 1
    }
}
