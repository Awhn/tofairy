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
            childThreshold = ChildAgeThreshold.AGE_7,
            dimensions = setOf(RatingDimension.VIOLENCE),
            officialRatingResolver = NoOfficialRatingResolver,
            shieldstral = ShieldstralScreeningEngine { sample, dimension, _ ->
                if (sample.sampleId == "b") error("inference failed")
                DimensionAssessment(dimension, exceedsThreshold = false)
            },
            sampleStore = store,
        )

        assertTrue(job.run().isFailure)
        assertTrue(store.purged.isEmpty())
    }

    @Test
    fun backendEnvelope_hasNoScreenshotOrDimensionField() {
        val fieldNames = EncryptedDigest::class.java.declaredFields.map { it.name }
        assertFalse(fieldNames.any { it.contains("screenshot", ignoreCase = true) })
        assertFalse(fieldNames.any { it.contains("dimension", ignoreCase = true) })
        assertFalse(fieldNames.any { it.contains("sample", ignoreCase = true) })
    }

    private fun successfulJob(store: FakeSampleStore) = DailyScreeningJob(
        childThreshold = ChildAgeThreshold.AGE_7,
        dimensions = setOf(RatingDimension.VIOLENCE),
        officialRatingResolver = NoOfficialRatingResolver,
        shieldstral = ShieldstralScreeningEngine { _, dimension, _ ->
            DimensionAssessment(dimension, exceedsThreshold = false)
        },
        sampleStore = store,
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

        override suspend fun pendingSamples(): List<ScreeningSample> = samples.toList()

        override suspend fun updateState(sampleId: String, state: ScreeningSampleState) {
            states[sampleId] = state
        }

        override suspend fun purgeRawSample(sampleId: String) {
            purged += sampleId
            samples.removeAll { it.sampleId == sampleId }
        }
    }
}
