package app.tofairy.child.screening.axisa

/** 공식 등급 정책의 초기 dimension 후보. 최종 목록은 국가별 공식 체계 검토 뒤 확정한다. */
enum class RatingDimension {
    VIOLENCE,
    SEXUAL_CONTENT,
    FEAR_OR_THREAT,
    LANGUAGE,
    DANGEROUS_OR_IMITABLE_BEHAVIOR,
    SUBSTANCE,
}

/**
 * 스크린샷별 민감 중간 결과. 직렬화하지 않고 일일 집계 생성 직후 버린다.
 * confidence/logit/probability는 외부 도메인 계약에 포함하지 않는다.
 */
data class DimensionAssessment(
    val dimension: RatingDimension,
    val exceedsThreshold: Boolean,
)

/**
 * Shieldstral 멀티모달 분류 경계.
 *
 * canonical model은 `mistralai/Shieldstral-1.0-3B`이며, 공식 checkpoint에서 검증·hash 고정한
 * artifact만 제품 후보가 된다. llama.cpp Android/GGUF는 benchmark 후보일 뿐 아직 구현되지 않았다.
 * 각 호출은 현재 자녀 경계와 하나의 공식 policy dimension에 대한 첫 yes/no 분류만 반환한다.
 */
fun interface ShieldstralScreeningEngine {
    suspend fun assess(
        sample: ScreeningSample,
        dimension: RatingDimension,
        childThreshold: ChildAgeThreshold,
    ): DimensionAssessment
}
