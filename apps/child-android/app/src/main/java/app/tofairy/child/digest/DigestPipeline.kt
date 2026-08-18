package app.tofairy.child.digest

import app.tofairy.child.apiclient.ApiClient
import app.tofairy.child.apiclient.DigestAck
import app.tofairy.child.localstore.RelationshipState

/**
 * 다이제스트 생성·봉인·자녀측 pending 보존·relay 전송을 조정한다.
 *
 * 서버 mailbox는 사용하지 않는다. 부모가 reachable하지 않으면 [PendingDigestStore]에 남기고,
 * 인증된 recipient ACK 뒤에만 해당 pending delivery를 정리하고 source aggregate ACK 정책을
 * 갱신한다. 원본 스크린샷은 부모 전달 대상이 아니므로 이 생명주기와 연결하지 않고 일일 A축
 * 스크리닝 완료 직후 별도로 삭제한다.
 */
class DigestPipeline(
    private val sealer: DigestSealer,
    private val apiClient: ApiClient,
    private val pendingStore: PendingDigestStore,
    private val aggregateAckPolicy: SourceAggregateAckPolicy,
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
) {
    /** 평문을 봉인해 암호화 로컬 pending 저장소에 WAITING_FOR_PARENT 상태로 넣는다. */
    suspend fun buildSealAndQueue(
        state: RelationshipState,
        digestId: String,
        periodEnd: Long,
        recipient: DigestRecipient,
        highlights: List<DigestHighlight> = emptyList(),
    ): Result<PendingDigest> = runCatching {
        val source = SourceAggregateRef(
            periodStart = state.periodAggregate.periodStart,
            periodEnd = periodEnd,
        )
        val digest = ContextDigest(
            digestId = digestId,
            periodStart = source.periodStart,
            periodEnd = source.periodEnd,
            aggregate = PeriodAggregate(
                breakSuggestions = state.periodAggregate.breakSuggestions,
                breaksAccepted = state.periodAggregate.breaksAccepted,
                interventions = state.periodAggregate.interventions,
            ),
            relationship = RelationshipSnapshot(
                promiseStreakDays = state.promiseStreakDays,
                bondLevel = state.bondLevel,
            ),
            highlights = highlights.distinct(),
        )
        val envelope = sealer.seal(digest, recipient)
        require(envelope.digestId == digest.digestId) { "sealer changed digestId" }

        val sealed = PendingDigest(
            digestId = digest.digestId,
            createdAt = nowMillis(),
            recipientDeviceId = recipient.parentDeviceId,
            encryptedDigest = envelope,
            deliveryState = DeliveryState.SEALED,
            sourceAggregate = source,
        )
        pendingStore.save(sealed)
        val waiting = checkNotNull(
            pendingStore.transition(
                key = sealed.deliveryKey,
                expectedStates = setOf(DeliveryState.SEALED),
                nextState = DeliveryState.WAITING_FOR_PARENT,
            ),
        )
        check(waiting.deliveryState == DeliveryState.WAITING_FOR_PARENT)
        waiting
    }

    /** 활성 relay tunnel로 전송을 시도한다. 실패하면 pending을 보존해 이후 재전송한다. */
    suspend fun sendPending(key: DigestDeliveryKey): Result<Unit> {
        val pending = pendingStore.find(key)
            ?: return Result.failure(IllegalArgumentException("unknown pending digest delivery: $key"))
        if (pending.deliveryState !in setOf(DeliveryState.WAITING_FOR_PARENT, DeliveryState.SENT)) {
            return Result.failure(
                IllegalStateException("digest cannot be relayed from ${pending.deliveryState}"),
            )
        }

        val relayResult = apiClient.relayDigest(pending.encryptedDigest)
        if (relayResult.isFailure) return relayResult

        val afterRelay = pendingStore.transition(
            key = key,
            expectedStates = setOf(DeliveryState.WAITING_FOR_PARENT, DeliveryState.SENT),
            nextState = DeliveryState.SENT,
        )
        if (afterRelay == null) {
            check(pendingStore.wasAcknowledged(key)) {
                "pending digest disappeared without an ACK tombstone"
            }
        } else {
            check(afterRelay.deliveryState in setOf(DeliveryState.SENT, DeliveryState.ACKED)) {
                "unexpected state after relay: ${afterRelay.deliveryState}"
            }
        }
        return Result.success(Unit)
    }

    /** 부모 ACK를 멱등 처리하고 해당 pending 정리 및 source aggregate 정책 갱신을 수행한다. */
    suspend fun acknowledge(ack: DigestAck): Result<DigestAckResult> = runCatching {
        val key = DigestDeliveryKey(ack.digestId, ack.parentDeviceId)
        val pending = pendingStore.find(key)
        if (pending == null) {
            return@runCatching if (pendingStore.wasAcknowledged(key)) {
                DigestAckResult.ALREADY_ACKED
            } else {
                DigestAckResult.UNKNOWN_DIGEST
            }
        }
        val acknowledged = pendingStore.transition(
            key = key,
            expectedStates = setOf(DeliveryState.WAITING_FOR_PARENT, DeliveryState.SENT),
            nextState = DeliveryState.ACKED,
        ) ?: return@runCatching if (pendingStore.wasAcknowledged(key)) {
            DigestAckResult.ALREADY_ACKED
        } else {
            DigestAckResult.UNKNOWN_DIGEST
        }
        check(acknowledged.deliveryState == DeliveryState.ACKED) {
            "digest cannot be ACKed from ${acknowledged.deliveryState}"
        }

        // 두 작업 모두 멱등 계약이다. 중간 실패 시 ACKED 레코드를 남겨 다음 호출이 정리를 재시도한다.
        aggregateAckPolicy.onDeliveryAcknowledged(acknowledged.sourceAggregate, key)
        pendingStore.purgeAcknowledged(key)
        DigestAckResult.ACKED_AND_PURGED
    }
}

/**
 * ContextDigest content-confidentiality 봉인기.
 *
 * 기본 구현 후보는 Tink Android의 Hybrid Encryption/HPKE이다. 검증된 template과 Android
 * keyset/Keystore 결합 방식을 확정하기 전에는 존재하지 않는 API나 커스텀 X25519/HKDF/nonce
 * framing을 구현하지 않는다. Hybrid Encryption 자체는 sender authenticity를 제공하지 않는다.
 */
fun interface DigestSealer {
    fun seal(digest: ContextDigest, recipient: DigestRecipient): EncryptedDigest
}
