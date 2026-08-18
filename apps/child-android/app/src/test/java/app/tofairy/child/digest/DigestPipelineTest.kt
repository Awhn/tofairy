package app.tofairy.child.digest

import app.tofairy.child.apiclient.MockApiClient
import app.tofairy.child.localstore.PeriodAggregateState
import app.tofairy.child.localstore.RelationshipState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DigestPipelineTest {

    @Test
    fun digestSchema_separatesPeriodRelationship_andHasNoRawContentFields() {
        val fieldNames = ContextDigest::class.java.declaredFields.map { it.name }.toSet()

        assertTrue("aggregate" in fieldNames)
        assertTrue("relationship" in fieldNames)
        assertTrue("highlights" in fieldNames)
        assertFalse(fieldNames.any { it.contains("screenshot", ignoreCase = true) })
        assertFalse(fieldNames.any { it.contains("content", ignoreCase = true) })
        assertFalse(fieldNames.any { it.contains("dimension", ignoreCase = true) })

        // 컴파일되는 유일한 highlight 입력은 사전 정의 enum이다.
        val highlights: List<DigestHighlight> = listOf(DigestHighlight.PROMISE_STREAK)
        assertEquals(DigestHighlight.PROMISE_STREAK, highlights.single())
    }

    @Test
    fun pendingIsKeptUntilAck_thenPendingAndSourceAggregateArePurged() = runBlocking {
        val fixture = fixture()

        val queued = fixture.pipeline.buildSealAndQueue(
            state = relationshipState(),
            digestId = "digest-1",
            periodEnd = 200L,
            highlights = listOf(DigestHighlight.LONG_SESSION_PATTERN),
        ).getOrThrow()

        assertEquals(DeliveryState.WAITING_FOR_PARENT, queued.deliveryState)
        assertTrue(fixture.store.find("digest-1") != null)
        assertTrue(fixture.acknowledger.purged.isEmpty())

        fixture.pipeline.sendPending("digest-1").getOrThrow()
        assertEquals(DeliveryState.SENT, fixture.store.find("digest-1")?.deliveryState)
        assertEquals(1, fixture.api.relayedDigests.size)

        assertEquals(
            DigestAckResult.ACKED_AND_PURGED,
            fixture.pipeline.acknowledge("digest-1").getOrThrow(),
        )
        assertNull(fixture.store.find("digest-1"))
        assertEquals(listOf(SourceAggregateRef(100L, 200L)), fixture.acknowledger.purged)
    }

    @Test
    fun ackIsIdempotent() = runBlocking {
        val fixture = fixture()
        fixture.pipeline.buildSealAndQueue(relationshipState(), "digest-2", 200L).getOrThrow()
        fixture.pipeline.sendPending("digest-2").getOrThrow()

        assertEquals(
            DigestAckResult.ACKED_AND_PURGED,
            fixture.pipeline.acknowledge("digest-2").getOrThrow(),
        )
        assertEquals(
            DigestAckResult.ALREADY_ACKED,
            fixture.pipeline.acknowledge("digest-2").getOrThrow(),
        )
        assertEquals(1, fixture.acknowledger.purged.size)
    }

    @Test
    fun duplicateDigestId_isProcessedOnlyOnceByParentRegistry() = runBlocking {
        val seen = mutableSetOf<String>()
        val deduplicator = DigestDeduplicator { id -> seen.add(id) }

        assertTrue(deduplicator.shouldProcess("digest-3"))
        assertFalse(deduplicator.shouldProcess("digest-3"))
    }

    private fun fixture(): Fixture {
        val store = FakePendingDigestStore()
        val api = MockApiClient()
        val acknowledger = RecordingAcknowledger()
        val sealer = DigestSealer { digest ->
            EncryptedDigest(
                digestId = digest.digestId,
                routingToken = "routing",
                cryptoVersion = 1,
                ciphertext = "sealed:${digest.digestId}".encodeToByteArray(),
            )
        }
        return Fixture(
            pipeline = DigestPipeline(sealer, api, store, acknowledger) { 123L },
            store = store,
            api = api,
            acknowledger = acknowledger,
        )
    }

    private fun relationshipState() = RelationshipState(
        bondLevel = 3,
        promiseStreakDays = 4,
        periodAggregate = PeriodAggregateState(
            periodStart = 100L,
            breakSuggestions = 5,
            breaksAccepted = 2,
            interventions = 6,
        ),
    )

    private data class Fixture(
        val pipeline: DigestPipeline,
        val store: FakePendingDigestStore,
        val api: MockApiClient,
        val acknowledger: RecordingAcknowledger,
    )

    private class FakePendingDigestStore : PendingDigestStore {
        private val pending = mutableMapOf<String, PendingDigest>()
        private val acknowledged = mutableSetOf<String>()

        override suspend fun save(pending: PendingDigest) {
            check(this.pending.putIfAbsent(pending.digestId, pending) == null)
        }

        override suspend fun find(digestId: String): PendingDigest? = pending[digestId]

        override suspend fun replace(pending: PendingDigest) {
            check(this.pending.containsKey(pending.digestId))
            this.pending[pending.digestId] = pending
        }

        override suspend fun purgeAcknowledged(digestId: String) {
            check(pending[digestId]?.deliveryState == DeliveryState.ACKED)
            pending.remove(digestId)
            acknowledged += digestId
        }

        override suspend fun wasAcknowledged(digestId: String): Boolean = digestId in acknowledged
    }

    private class RecordingAcknowledger : SourceAggregateAcknowledger {
        val purged = mutableListOf<SourceAggregateRef>()

        override suspend fun purge(source: SourceAggregateRef) {
            if (source !in purged) purged += source
        }
    }
}
