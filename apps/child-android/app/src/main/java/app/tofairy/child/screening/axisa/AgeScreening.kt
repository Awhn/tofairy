package app.tofairy.child.screening.axisa

import app.tofairy.child.core.EphemeralContent
import app.tofairy.child.core.consume

/**
 * A축(적절성) — 콘텐츠가 아이 연령(만 7~9세)에 맞는지 판단 (CLAUDE.md §3, §6).
 *
 * 두 단계:
 *  1) 등급 매핑: 콘텐츠에 공식 연령가가 있으면 그대로 매핑(LLM 불필요).
 *  2) 온디바이스 추정: 등급 없는 콘텐츠는 '등가 연령가'를 멀티모달로 추정.
 *
 * 불변식 #1/#2/#3:
 *  - 입력은 [EphemeralContent]. 판단 직후 [AgeRating] 라벨도 폐기 대상이며,
 *    localstore 에는 라벨이 아니라 라우터가 만든 집계/관계만 남는다.
 *  - 모델 출력은 등급 라벨(분류 결과)뿐. 자유 텍스트 child-facing 경로 없음.
 */
class AgeScreener(
    private val childAgeYears: Int,
    /** 등급 없는 콘텐츠의 온디바이스 등가 연령가 추정기(플러그러블). */
    private val estimator: AgeRatingEstimator = AgeRatingEstimator.Unavailable,
) {
    /**
     * 콘텐츠를 소비하고 적절성 판단을 반환한다. 입력은 호출 종료 시 항상 폐기된다.
     * [knownRating] 이 있으면 추정을 건너뛴다(등급 매핑 경로).
     */
    fun screen(content: EphemeralContent, knownRating: AgeRating? = null): AgeAppropriateness =
        content.consume { c ->
            val rating = knownRating ?: estimator.estimate(c)
            classify(rating)
            // 주의: rating 라벨은 여기서 소비되고 반환값(집계 가능한 enum)만 남는다.
        }

    private fun classify(rating: AgeRating?): AgeAppropriateness = when {
        rating == null -> AgeAppropriateness.WITHIN_AGE // 판단 불가 시 개입하지 않음(과개입 금지)
        rating.minAge <= childAgeYears -> AgeAppropriateness.WITHIN_AGE
        rating.minAge - childAgeYears >= 4 -> AgeAppropriateness.ABOVE_AGE_NOTABLE
        else -> AgeAppropriateness.ABOVE_AGE_MILD
    }
}

/**
 * 온디바이스 등가 연령가 추정기 (Gemma 4 E2B 멀티모달 후보, CLAUDE.md §6).
 * 출력은 [AgeRating] 라벨뿐(폐기 대상). 자유 텍스트 미사용.
 */
fun interface AgeRatingEstimator {
    /** ephemeral 콘텐츠로부터 등가 연령가를 추정. 판단 불가면 null. */
    fun estimate(content: EphemeralContent): AgeRating?

    companion object {
        /** ML 런타임 비활성/저사양 폴백: 추정하지 않음(등급 매핑만 사용). */
        val Unavailable = AgeRatingEstimator { null }
    }
}

/** 콘텐츠 연령가 라벨. 민감 라벨 — 판단 직후 폐기, 저장 금지(불변식 #1/#2). */
@JvmInline
value class AgeRating(val minAge: Int)

/** A축 판단 결과(집계 가능, 라우터 입력). 원시 라벨이 아닌 파생 enum. */
enum class AgeAppropriateness {
    WITHIN_AGE,
    ABOVE_AGE_MILD,
    ABOVE_AGE_NOTABLE,
}
