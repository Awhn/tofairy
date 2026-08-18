package app.tofairy.child.screening.axisa

/** 장기 보존 가능한 최소 일일 집계. dimension별 상세 결과는 포함하지 않는다. */
data class DailyScreeningAggregate(
    val screenedSampleCount: Int,
    val thresholdExceededSampleCount: Int,
    val interventionNeeded: Boolean,
)

/**
 * 공식 등급 우선 → 미등급만 Shieldstral dimension 판정 → 최소 집계 → raw sample 삭제.
 * 배치 하나라도 실패하면 [Result.failure]를 반환하며 해당 배치 원본을 purge하지 않는다.
 */
class DailyScreeningJob(
    private val childThreshold: ChildAgeThreshold,
    private val dimensions: Set<RatingDimension>,
    private val officialRatingResolver: OfficialRatingResolver,
    private val shieldstral: ShieldstralScreeningEngine,
    private val sampleStore: ScreeningSampleStore,
) {
    init {
        require(dimensions.isNotEmpty()) { "at least one rating dimension is required" }
    }

    suspend fun run(): Result<DailyScreeningAggregate> = runCatching {
        val samples = sampleStore.pendingSamples()
        val outcomes = mutableListOf<AgeSuitability>()

        for (sample in samples) {
            sampleStore.updateState(sample.sampleId, ScreeningSampleState.SCREENING)
            val official = officialRatingResolver.resolve(sample.metadata, childThreshold)
            val suitability = official ?: assessUnrated(sample)
            outcomes += suitability
            sampleStore.updateState(sample.sampleId, ScreeningSampleState.SCREENED)
        }

        val exceededCount = outcomes.count { it == AgeSuitability.EXCEEDS_AGE_THRESHOLD }
        val aggregate = DailyScreeningAggregate(
            screenedSampleCount = outcomes.size,
            thresholdExceededSampleCount = exceededCount,
            interventionNeeded = exceededCount > 0,
        )

        // 모든 샘플의 판단과 집계가 성공한 뒤에만 원본을 삭제한다.
        for (sample in samples) {
            sampleStore.updateState(sample.sampleId, ScreeningSampleState.AGGREGATED)
            sampleStore.purgeRawSample(sample.sampleId)
        }
        aggregate
    }

    private suspend fun assessUnrated(sample: ScreeningSample): AgeSuitability {
        var anyDimensionExceeds = false
        for (dimension in dimensions) {
            val assessment = shieldstral.assess(sample, dimension, childThreshold)
            require(assessment.dimension == dimension) { "engine returned a different dimension" }
            if (assessment.exceedsThreshold) anyDimensionExceeds = true
        }
        return if (anyDimensionExceeds) {
            AgeSuitability.EXCEEDS_AGE_THRESHOLD
        } else {
            AgeSuitability.WITHIN_AGE_THRESHOLD
        }
    }
}
