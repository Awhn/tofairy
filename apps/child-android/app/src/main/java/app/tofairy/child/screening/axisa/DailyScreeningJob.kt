package app.tofairy.child.screening.axisa

/** 장기 보존 가능한 최소 일일 집계. dimension별 상세 결과는 포함하지 않는다. */
data class DailyScreeningAggregate(
    /** 재시도 시 같은 commit을 식별하는 비콘텐츠 불투명 ID. */
    val batchId: String,
    val screenedSampleCount: Int,
    val thresholdExceededSampleCount: Int,
    val interventionNeeded: Boolean,
)

/**
 * raw sample 삭제 전에 최소 일일 집계를 제품 로직/암호화 로컬 상태에 확정하는 경계.
 * 같은 batchId와 동일 aggregate의 재호출은 성공으로 처리하고 중복 반영하지 않아야 한다.
 */
fun interface DailyScreeningAggregateSink {
    suspend fun commit(aggregate: DailyScreeningAggregate)
}

/**
 * 공식 등급 우선 → 미등급만 Shieldstral dimension 판정 → 최소 집계 → raw sample 삭제.
 * screening·집계 commit·원자적 상태 확정 중 하나라도 실패하면 원본 purge를 시작하지 않는다.
 * purge 도중 실패하면 이미 지운 원본을 되살릴 수 없으므로, 남은 AGGREGATED 원본만 재시도한다.
 */
class DailyScreeningJob(
    private val batchId: String,
    private val childThreshold: ChildAgeThreshold,
    private val dimensions: Set<RatingDimension>,
    private val officialRatingResolver: OfficialRatingResolver,
    private val shieldstral: ShieldstralScreeningEngine,
    private val sampleStore: ScreeningSampleStore,
    private val aggregateSink: DailyScreeningAggregateSink,
) {
    init {
        require(batchId.isNotBlank()) { "batchId must not be blank" }
        require(dimensions.isNotEmpty()) { "at least one rating dimension is required" }
    }

    suspend fun run(): Result<DailyScreeningAggregate> = runCatching {
        // 이전 실행이 aggregate commit 뒤 raw purge에서 중단됐다면 재집계하지 않고 정리만 재시도한다.
        sampleStore.pendingSamples()
            .filter { it.state == ScreeningSampleState.AGGREGATED }
            .forEach { sampleStore.purgeRawSample(it.sampleId) }

        val samples = sampleStore.pendingSamples()
            .filter { it.state != ScreeningSampleState.AGGREGATED }
        if (samples.isEmpty()) {
            return@runCatching DailyScreeningAggregate(
                batchId = batchId,
                screenedSampleCount = 0,
                thresholdExceededSampleCount = 0,
                interventionNeeded = false,
            )
        }
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
            batchId = batchId,
            screenedSampleCount = outcomes.size,
            thresholdExceededSampleCount = exceededCount,
            interventionNeeded = exceededCount > 0,
        )

        // 집계 확정 실패를 정상 완료로 오인해 원본을 먼저 지우지 않는다.
        aggregateSink.commit(aggregate)

        // commit 후 모든 레코드를 한 번에 AGGREGATED로 확정해야 부분 purge 재시도가 재집계되지 않는다.
        sampleStore.markBatchAggregated(samples.mapTo(mutableSetOf()) { it.sampleId }, batchId)

        // 모든 샘플의 판단·집계 commit·상태 확정이 성공한 뒤에만 원본을 삭제한다.
        for (sample in samples) {
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
