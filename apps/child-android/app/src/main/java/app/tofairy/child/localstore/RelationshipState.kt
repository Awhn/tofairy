package app.tofairy.child.localstore

import kotlinx.serialization.Serializable

/**
 * 관계·기억·기간 집계 상태 (CLAUDE.md §7).
 *
 * 불변식 #1/#2: 여기에는 원시 콘텐츠·민감 라벨이 절대 들어오지 않는다.
 * A축 샘플·dimension 판정은 별도 암호화 임시 저장소에서 일일 배치가 끝난 뒤 폐기되며,
 * 이 상태에는 부모에게 전달 가능한 기간 집계와 관계 상태만 남는다.
 */
@Serializable
data class RelationshipState(
    /** 암호화 로컬 저장 형식 버전. codec이 이전 형식을 명시적으로 migration한다. */
    val schemaVersion: Int = SCHEMA_VERSION,
    /** 요정 이름(온보딩 네이밍). child-facing 식별자. */
    val fairyName: String? = null,
    /** 각성 의식 완료 여부. */
    val awakened: Boolean = false,
    /** 유대 점수(관계 진행, 집계). */
    val bondLevel: Int = 0,
    /** 연속 약속 달성일수(집계). */
    val promiseStreakDays: Int = 0,
    /** 아직 ACK 되지 않은 현재 기간의 전달 가능 집계. */
    val periodAggregate: PeriodAggregateState = PeriodAggregateState(),
    /** 마지막 상호작용 시각(epoch millis, 집계). */
    val lastInteractionAt: Long = 0L,
) {
    init {
        require(schemaVersion == SCHEMA_VERSION) { "unsupported relationship schema: $schemaVersion" }
    }

    /** 디스크/로그/네트워크 어디에도 평문 라벨이 없는지 보장하기 위한 표식(이 타입엔 라벨 필드 자체가 없다). */
    companion object {
        const val SCHEMA_VERSION = 2
    }
}

/**
 * 특정 기간에만 귀속되는 집계. 누적 관계 상태와 섞지 않는다.
 *
 * 원시 화면·앱 내부 콘텐츠명·URL·검색어·A축 dimension 결과는 이 타입에 들어갈 수 없다.
 * 확정된 recipient 완료 정책이 충족되면 source aggregate 정책 구현이 이 값을 초기화한다.
 */
@Serializable
data class PeriodAggregateState(
    val periodStart: Long = 0L,
    val breakSuggestions: Int = 0,
    val breaksAccepted: Int = 0,
    val interventions: Int = 0,
)
