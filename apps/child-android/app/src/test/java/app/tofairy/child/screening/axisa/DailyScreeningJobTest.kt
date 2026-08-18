package app.tofairy.child.screening.axisa

import app.tofairy.child.digest.EncryptedDigest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyScreeningJobTest {

    @Test
    fun officialRating_bypassesShieldstral() = runBlocking {
        val store = FakeSampleStore(mutableListOf(sample("sample-1")))
        var modelCalls = 0
        val job = DailyScreeningJob(
            batchId = "batch-official",
            childThreshold = ChildAgeThreshold.AGE_7,
            dimensions = setOf(RatingDimension.VIOLENCE),
            officialRatingResolver = OfficialRatingResolver { _, _ ->
                AgeSuitability.WITHIN_AGE_THRESHOLD
            },
            shieldstral = ShieldstralScreeningEngine { _, dimension, _ ->
                modelCalls += 1
                DimensionAssessment(dimension, exceedsThreshold = true)
            },
            sampleStore = store,
            aggregateSink = DailyScreeningAggregateSink { },
        )

        val result = job.run().getOrThrow()

        assertEquals(0, modelCalls)
        assertEquals(1, result.screenedSampleCount)
        assertEquals(0, result.thresholdExceededSampleCount)
        assertEquals(listOf("sample-1"), store.purged)
    }

    @Test
    fun unratedContent_checksEveryDimensionAgainstCurrentThresholdOnly() = runBlocking {
        val store = FakeSampleStore(mutableListOf(sample("sample-2")))
        val calls = mutableListOf<Pair<RatingDimension, ChildAgeThreshold>>()
        val dimensions = linkedSetOf(
            RatingDimension.VIOLENCE,
            RatingDimension.FEAR_OR_THREAT,
            RatingDimension.LANGUAGE,
        )
        val job = DailyScreeningJob(
            batchId = "batch-dimensions",
            childThreshold = ChildAgeThreshold.AGE_8,
            dimensions = dimensions,
            officialRatingResolver = NoOfficialRatingResolver,
            shieldstral = ShieldstralScreeningEngine { _, dimension, threshold ->
                calls += dimension to threshold
                DimensionAssessment(
                    dimension,
                    exceedsThreshold = dimension == RatingDimension.FEAR_OR_THREAT,
                )
            },
            sampleStore = store,
            aggregateSink = DailyScreeningAggregateSink { },
        )

        val result = job.run().getOrThrow()

        assertEquals(dimensions.toList(), calls.map { it.first })
        assertEquals(setOf(ChildAgeThreshold.AGE_8), calls.map { it.second }.toSet())
        assertEquals(1, result.thresholdExceededSampleCount)
        assertTrue(result.interventionNeeded)
        assertEquals(listOf("sample-2"), store.purged)
    }

    @Test
    fun successfulDailyBatch_purgesRawSamplesOnlyAfterAggregation() = runBlocking {
        val store = FakeSampleStore(mutableListOf(sample("a"), sample("b")))
        val job = successfulJob(store)

        assertTrue(job.run().isSuccess)
        assertEquals(listOf("a", "b"), store.purged)
        assertEquals(ScreeningSampleState.AGGREGATED, store.states["a"])
        assertEquals(ScreeningSampleState.AGGREGATED, store.states["b"])
    }

    @Test
    fun failedDailyBatch_doesNotPurgeAnyRawSample() = runBlocking {
        val store = FakeSampleStore(mutableListOf(sample("a"), sample("b")))
        val job = DailyScreeningJob(
            batchId = "batch-inference-failure",
            childThreshold = ChildAgeThreshold.AGE_7,
            dimensions = setOf(RatingDimension.VIOLENCE),
            officialRatingResolver = NoOfficialRatingResolver,
            shieldstral = ShieldstralScreeningEngine { sample, dimension, _ ->
                if (sample.sampleId == "b") error("inference failed")
                DimensionAssessment(dimension, exceedsThreshold = false)
            },
            sampleStore = store,
            aggregateSink = DailyScreeningAggregateSink { },
        )

        assertTrue(job.run().isFailure)
        assertTrue(store.purged.isEmpty())
    }

    @Test
    fun failedAggregateCommit_doesNotPurgeRawSamples() = runBlocking {
        val store = FakeSampleStore(mutableListOf(sample("commit-failure")))
        val job = DailyScreeningJob(
            batchId = "batch-commit-failure",
            childThreshold = ChildAgeThreshold.AGE_7,
            dimensions = setOf(RatingDimension.VIOLENCE),
            officialRatingResolver = NoOfficialRatingResolver,
            shieldstral = ShieldstralScreeningEngine { _, dimension, _ ->
                DimensionAssessment(dimension, exceedsThreshold = false)
            },
            sampleStore = store,
            aggregateSink = DailyScreeningAggregateSink { error("aggregate commit failed") },
        )

        assertTrue(job.run().isFailure)
        assertTrue(store.purged.isEmpty())
    }

    @Test
    fun purgeFailure_retryCleansAggregatedRaw_withoutDuplicateAggregateCommit() = runBlocking {
        val store = FakeSampleStore(mutableListOf(sample("a"), sample("b"))).apply {
            failNextPurgeFor = "b"
        }
        var aggregateCommits = 0
        val job = DailyScreeningJob(
            batchId = "stable-batch-id",
            childThreshold = ChildAgeThreshold.AGE_7,
            dimensions = setOf(RatingDimension.VIOLENCE),
            officialRatingResolver = NoOfficialRatingResolver,
            shieldstral = ShieldstralScreeningEngine { _, dimension, _ ->
                DimensionAssessment(dimension, exceedsThreshold = false)
            },
            sampleStore = store,
            aggregateSink = DailyScreeningAggregateSink { aggregateCommits += 1 },
        )

        assertTrue(job.run().isFailure)
        assertEquals(1, aggregateCommits)
        assertEquals(ScreeningSampleState.AGGREGATED, store.states["b"])

        assertTrue(job.run().isSuccess)
        assertEquals(1, aggregateCommits)
        assertTrue(store.pendingSamples().isEmpty())
    }

    @Test
    fun aggregateStateFailure_retryUsesStableBatchId_andIdempotentCommit() = runBlocking {
        val store = FakeSampleStore(mutableListOf(sample("state-failure"))).apply {
            failNextBatchMark = true
        }
        val sink = RecordingIdempotentAggregateSink()
        val job = DailyScreeningJob(
            batchId = "stable-state-batch",
            childThreshold = ChildAgeThreshold.AGE_7,
            dimensions = setOf(RatingDimension.VIOLENCE),
            officialRatingResolver = NoOfficialRatingResolver,
            shieldstral = ShieldstralScreeningEngine { _, dimension, _ ->
                DimensionAssessment(dimension, exceedsThreshold = false)
            },
            sampleStore = store,
            aggregateSink = sink,
        )

        assertTrue(job.run().isFailure)
        assertEquals(1, sink.uniqueCommits)

        assertTrue(job.run().isSuccess)
        assertEquals(1, sink.uniqueCommits)
        assertTrue(store.pendingSamples().isEmpty())
    }

    @Test
    fun backendEnvelope_hasNoScreenshotOrDimensionField() {
        val fieldNames = EncryptedDigest::class.java.declaredFields.map { it.name }
        assertFalse(fieldNames.any { it.contains("screenshot", ignoreCase = true) })
        assertFalse(fieldNames.any { it.contains("dimension", ignoreCase = true) })
        assertFalse(fieldNames.any { it.contains("sample", ignoreCase = true) })
    }

    private fun successfulJob(store: FakeSampleStore) = DailyScreeningJob(
        batchId = "batch-success",
        childThreshold = ChildAgeThreshold.AGE_7,
        dimensions = setOf(RatingDimension.VIOLENCE),
        officialRatingResolver = NoOfficialRatingResolver,
        shieldstral = ShieldstralScreeningEngine { _, dimension, _ ->
            DimensionAssessment(dimension, exceedsThreshold = false)
        },
        sampleStore = store,
        aggregateSink = DailyScreeningAggregateSink { },
    )

    private fun sample(id: String) = ScreeningSample(
        sampleId = id,
        screenshot = EncryptedLocalImage("encrypted/$id"),
        capturedAt = 100L,
        metadata = ScreeningMetadata(
            sessionId = "session-1",
            sessionDurationMillis = 1_000L,
            samplingReason = SamplingReason.MEANINGFUL_SCREEN_CHANGE,
            transitionContext = ScreenTransitionContext.CONTENT_CHANGED,
        ),
    )

    private class FakeSampleStore(
        private val samples: MutableList<ScreeningSample>,
    ) : ScreeningSampleStore {
        val states = mutableMapOf<String, ScreeningSampleState>()
        val purged = mutableListOf<String>()
        var failNextPurgeFor: String? = null
        var failNextBatchMark: Boolean = false

        override suspend fun pendingSamples(): List<ScreeningSample> = samples.toList()

        override suspend fun updateState(sampleId: String, state: ScreeningSampleState) {
            states[sampleId] = state
            val index = samples.indexOfFirst { it.sampleId == sampleId }
            check(index >= 0)
            samples[index] = samples[index].copy(state = state)
        }

        override suspend fun markBatchAggregated(sampleIds: Set<String>, batchId: String) {
            if (failNextBatchMark) {
                failNextBatchMark = false
                error("simulated atomic batch-state failure")
            }
            check(sampleIds.all { id -> samples.any { it.sampleId == id } })
            samples.indices.forEach { index ->
                val sample = samples[index]
                if (sample.sampleId in sampleIds) {
                    states[sample.sampleId] = ScreeningSampleState.AGGREGATED
                    samples[index] = sample.copy(state = ScreeningSampleState.AGGREGATED)
                }
            }
        }

        override suspend fun purgeRawSample(sampleId: String) {
            if (failNextPurgeFor == sampleId) {
                failNextPurgeFor = null
                error("simulated purge failure")
            }
            purged += sampleId
            samples.removeAll { it.sampleId == sampleId }
        }
    }

    private class RecordingIdempotentAggregateSink : DailyScreeningAggregateSink {
        private val committed = mutableMapOf<String, DailyScreeningAggregate>()
        val uniqueCommits: Int
            get() = committed.size

        override suspend fun commit(aggregate: DailyScreeningAggregate) {
            val previous = committed.putIfAbsent(aggregate.batchId, aggregate)
            check(previous == null || previous == aggregate) {
                "same batchId cannot be committed with different aggregate content"
            }
        }
    }
}
