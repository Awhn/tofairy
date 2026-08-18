package app.tofairy.child.digest

import app.tofairy.child.apiclient.MockApiClient
import app.tofairy.child.apiclient.DigestAck
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
            recipient = recipient(),
            highlights = listOf(DigestHighlight.LONG_SESSION_PATTERN),
        ).getOrThrow()

        assertEquals(DeliveryState.WAITING_FOR_PARENT, queued.deliveryState)
        assertTrue(fixture.store.find(key("digest-1")) != null)
        assertTrue(fixture.acknowledger.purged.isEmpty())

        fixture.pipeline.sendPending(key("digest-1")).getOrThrow()
        assertEquals(DeliveryState.SENT, fixture.store.find(key("digest-1"))?.deliveryState)
        assertEquals(1, fixture.api.relayedDigests.size)

        assertEquals(
            DigestAckResult.ACKED_AND_PURGED,
            fixture.pipeline.acknowledge(ack("digest-1")).getOrThrow(),
        )
        assertNull(fixture.store.find(key("digest-1")))
        assertEquals(listOf(SourceAggregateRef(100L, 200L)), fixture.acknowledger.purged)
    }

    @Test
    fun ackIsIdempotent() = runBlocking {
        val fixture = fixture()
        fixture.pipeline.buildSealAndQueue(
            relationshipState(),
            "digest-2",
            200L,
            recipient(),
        ).getOrThrow()
        fixture.pipeline.sendPending(key("digest-2")).getOrThrow()

        assertEquals(
            DigestAckResult.ACKED_AND_PURGED,
            fixture.pipeline.acknowledge(ack("digest-2")).getOrThrow(),
        )
        assertEquals(
            DigestAckResult.ALREADY_ACKED,
            fixture.pipeline.acknowledge(ack("digest-2")).getOrThrow(),
        )
        assertEquals(1, fixture.acknowledger.purged.size)
    }

    @Test
    fun unavailableRelay_keepsDigestPendingForRetry() = runBlocking {
        val fixture = fixture()
        fixture.pipeline.buildSealAndQueue(
            relationshipState(),
            "digest-retry",
            200L,
            recipient(),
        ).getOrThrow()
        fixture.api.relayAvailable = false

        assertTrue(fixture.pipeline.sendPending(key("digest-retry")).isFailure)
        assertEquals(
            DeliveryState.WAITING_FOR_PARENT,
            fixture.store.find(key("digest-retry"))?.deliveryState,
        )
        assertTrue(fixture.api.relayedDigests.isEmpty())
        assertTrue(fixture.acknowledger.purged.isEmpty())
    }

    @Test
    fun duplicateDigestId_isProcessedOnlyOnceByParentRegistry() = runBlocking {
        val stored = mutableMapOf<String, ContextDigest>()
        val deduplicator = DigestDeduplicator { digest ->
            stored.putIfAbsent(digest.digestId, digest) == null
        }
        val digest = contextDigest("digest-3")

        assertTrue(deduplicator.storeOnce(digest))
        assertFalse(deduplicator.storeOnce(digest))
        assertEquals(1, stored.size)
    }

    @Test
    fun ackFromDifferentParentDevice_cannotPurgePendingDigest() = runBlocking {
        val fixture = fixture()
        fixture.pipeline.buildSealAndQueue(
            relationshipState(),
            "digest-peer-check",
            200L,
            recipient(),
        ).getOrThrow()
        fixture.pipeline.sendPending(key("digest-peer-check")).getOrThrow()

        val result = fixture.pipeline.acknowledge(
            DigestAck("digest-peer-check", "different-parent-device"),
        )

        assertEquals(DigestAckResult.UNKNOWN_DIGEST, result.getOrThrow())
        assertEquals(
            DeliveryState.SENT,
            fixture.store.find(key("digest-peer-check"))?.deliveryState,
        )
        assertTrue(fixture.acknowledger.purged.isEmpty())
    }

    @Test
    fun pendingStore_canRepresentSameDigestForDifferentRecipients_withoutChoosingFanoutPolicy() =
        runBlocking {
            val fixture = fixture()
            fixture.pipeline.buildSealAndQueue(
                relationshipState(),
                "digest-multi-device",
                200L,
                recipient("parent-device-1"),
            ).getOrThrow()
            fixture.pipeline.buildSealAndQueue(
                relationshipState(),
                "digest-multi-device",
                200L,
                recipient("parent-device-2"),
            ).getOrThrow()

            assertTrue(
                fixture.store.find(key("digest-multi-device", "parent-device-1")) != null,
            )
            assertTrue(
                fixture.store.find(key("digest-multi-device", "parent-device-2")) != null,
            )
        }

    @Test
    fun ackBeforeSentStateWrite_doesNotResurrectPurgedPendingDigest() = runBlocking {
        val fixture = fixture()
        fixture.pipeline.buildSealAndQueue(
            relationshipState(),
            "digest-race",
            200L,
            recipient(),
        ).getOrThrow()
        fixture.api.beforeRelaySuccess = {
            assertEquals(
                DigestAckResult.ACKED_AND_PURGED,
                fixture.pipeline.acknowledge(ack("digest-race")).getOrThrow(),
            )
        }

        assertTrue(fixture.pipeline.sendPending(key("digest-race")).isSuccess)
        assertNull(fixture.store.find(key("digest-race")))
        assertTrue(fixture.store.wasAcknowledged(key("digest-race")))
        assertEquals(1, fixture.acknowledger.purged.size)
    }

    private fun fixture(): Fixture {
        val store = FakePendingDigestStore()
        val api = MockApiClient()
        val acknowledger = RecordingAcknowledger()
        val sealer = DigestSealer { digest, recipient ->
            EncryptedDigest(
                digestId = digest.digestId,
                routingToken = "routing:${recipient.parentDeviceId}",
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

    private fun recipient(parentDeviceId: String = PARENT_DEVICE_ID) = DigestRecipient(
        parentDeviceId = parentDeviceId,
        publicKeyset = byteArrayOf(1, 2, 3),
        trustedSetRevision = 7,
    )

    private fun ack(digestId: String) = DigestAck(digestId, PARENT_DEVICE_ID)

    private fun key(
        digestId: String,
        parentDeviceId: String = PARENT_DEVICE_ID,
    ) = DigestDeliveryKey(digestId, parentDeviceId)

    private fun contextDigest(digestId: String) = ContextDigest(
        digestId = digestId,
        periodStart = 100L,
        periodEnd = 200L,
        aggregate = PeriodAggregate(1, 1, 1),
        relationship = RelationshipSnapshot(1, 1),
    )

    private data class Fixture(
        val pipeline: DigestPipeline,
        val store: FakePendingDigestStore,
        val api: MockApiClient,
        val acknowledger: RecordingAcknowledger,
    )

    private class FakePendingDigestStore : PendingDigestStore {
        private val pending = mutableMapOf<DigestDeliveryKey, PendingDigest>()
        private val acknowledged = mutableSetOf<DigestDeliveryKey>()

        override suspend fun save(pending: PendingDigest) {
            check(this.pending.putIfAbsent(pending.deliveryKey, pending) == null)
        }

        override suspend fun find(key: DigestDeliveryKey): PendingDigest? = pending[key]

        override suspend fun transition(
            key: DigestDeliveryKey,
            expectedStates: Set<DeliveryState>,
            nextState: DeliveryState,
        ): PendingDigest? = synchronized(this) {
            val current = pending[key] ?: return@synchronized null
            if (current.deliveryState !in expectedStates) return@synchronized current
            current.transitionTo(nextState).also { pending[key] = it }
        }

        override suspend fun purgeAcknowledged(key: DigestDeliveryKey) {
            synchronized(this) {
                if (key in acknowledged && pending[key] == null) return@synchronized
                check(pending[key]?.deliveryState == DeliveryState.ACKED)
                pending.remove(key)
                acknowledged += key
            }
        }

        override suspend fun wasAcknowledged(key: DigestDeliveryKey): Boolean =
            synchronized(this) { key in acknowledged }
    }

    private class RecordingAcknowledger : SourceAggregateAckPolicy {
        val purged = mutableListOf<SourceAggregateRef>()

        override suspend fun onDeliveryAcknowledged(
            source: SourceAggregateRef,
            deliveryKey: DigestDeliveryKey,
        ) {
            if (source !in purged) purged += source
        }
    }

    private companion object {
        const val PARENT_DEVICE_ID = "parent-device-1"
    }
}
