package app.tofairy.child.digest

import app.tofairy.child.apiclient.ApiClient
import app.tofairy.child.localstore.RelationshipState

/**
 * 다이제스트 생성·봉인·자녀측 pending 보존·relay 전송을 조정한다.
 *
 * 서버 mailbox는 사용하지 않는다. 부모가 reachable하지 않으면 [PendingDigestStore]에 남기고,
 * 부모 ACK 뒤에만 암호문과 해당 source aggregate를 정리한다. 원본 스크린샷은 부모 전달 대상이
 * 아니므로 이 생명주기와 연결하지 않고 일일 A축 스크리닝 완료 직후 별도로 삭제한다.
 */
class DigestPipeline(
    private val sealer: DigestSealer,
    private val apiClient: ApiClient,
    private val pendingStore: PendingDigestStore,
    private val aggregateAcknowledger: SourceAggregateAcknowledger,
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
) {
    /** 평문을 봉인해 암호화 로컬 pending 저장소에 WAITING_FOR_PARENT 상태로 넣는다. */
    suspend fun buildSealAndQueue(
        state: RelationshipState,
        digestId: String,
        periodEnd: Long,
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
        val envelope = sealer.seal(digest)
        require(envelope.digestId == digest.digestId) { "sealer changed digestId" }

        val sealed = PendingDigest(
            digestId = digest.digestId,
            createdAt = nowMillis(),
            encryptedDigest = envelope,
            deliveryState = DeliveryState.SEALED,
            sourceAggregate = source,
        )
        pendingStore.save(sealed)
        val waiting = sealed.transitionTo(DeliveryState.WAITING_FOR_PARENT)
        pendingStore.replace(waiting)
        waiting
    }

    /** 활성 relay tunnel로 전송을 시도한다. 실패하면 pending을 보존해 이후 재전송한다. */
    suspend fun sendPending(digestId: String): Result<Unit> {
        val pending = pendingStore.find(digestId)
            ?: return Result.failure(IllegalArgumentException("unknown pending digest: $digestId"))
        if (pending.deliveryState !in setOf(DeliveryState.WAITING_FOR_PARENT, DeliveryState.SENT)) {
            return Result.failure(
                IllegalStateException("digest cannot be relayed from ${pending.deliveryState}"),
            )
        }

        val relayResult = apiClient.relayDigest(pending.encryptedDigest)
        if (relayResult.isFailure) return relayResult

        pendingStore.replace(pending.transitionTo(DeliveryState.SENT))
        return Result.success(Unit)
    }

    /** 부모 ACK를 멱등 처리하고 ACK 이후에만 pending 및 source aggregate를 정리한다. */
    suspend fun acknowledge(digestId: String): Result<DigestAckResult> = runCatching {
        val pending = pendingStore.find(digestId)
        if (pending == null) {
            return@runCatching if (pendingStore.wasAcknowledged(digestId)) {
                DigestAckResult.ALREADY_ACKED
            } else {
                DigestAckResult.UNKNOWN_DIGEST
            }
        }

        val acknowledged = when (pending.deliveryState) {
            DeliveryState.WAITING_FOR_PARENT,
            DeliveryState.SENT,
            -> pending.transitionTo(DeliveryState.ACKED)
            DeliveryState.ACKED -> pending
            else -> error("digest cannot be ACKed from ${pending.deliveryState}")
        }
        pendingStore.replace(acknowledged)

        // 두 작업 모두 멱등 계약이다. 중간 실패 시 ACKED 레코드를 남겨 다음 호출이 정리를 재시도한다.
        aggregateAcknowledger.purge(acknowledged.sourceAggregate)
        pendingStore.purgeAcknowledged(digestId)
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
    fun seal(digest: ContextDigest): EncryptedDigest
}
