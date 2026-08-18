package app.tofairy.child.screening.axisa

/** 현재 자녀에게 적용할 하나의 정책 경계. 여러 연령 경계를 모델에 반복 질의하지 않는다. */
enum class ChildAgeThreshold {
    AGE_7,
    AGE_8,
    AGE_9,
}

/** A축이 외부로 내보내는 유일한 현재 연령 경계 판정. */
enum class AgeSuitability {
    WITHIN_AGE_THRESHOLD,
    EXCEEDS_AGE_THRESHOLD,
}

/**
 * 공식 등급 우선 경로.
 *
 * 신뢰할 플랫폼/국가별 source, 앱 전체 등급과 내부 콘텐츠 등급 충돌, UGC 처리 정책은 아직
 * Open Issue다. 구현체는 적용 가능한 공식 등급이 없을 때만 null을 반환한다.
 */
fun interface OfficialRatingResolver {
    fun resolve(
        metadata: ScreeningMetadata,
        childThreshold: ChildAgeThreshold,
    ): AgeSuitability?
}

/** 테스트·골격용: 공식 등급 source가 아직 연결되지 않은 상태. */
object NoOfficialRatingResolver : OfficialRatingResolver {
    override fun resolve(
        metadata: ScreeningMetadata,
        childThreshold: ChildAgeThreshold,
    ): AgeSuitability? = null
}
