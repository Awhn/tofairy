package app.tofairy.child.digest

/** 자녀 기기에서 관리하는 다이제스트 전달 상태. PURGED는 레코드가 제거된 상태라 enum에 두지 않는다. */
enum class DeliveryState {
    CREATED,
    SEALED,
    WAITING_FOR_PARENT,
    SENT,
    ACKED,
}

/** recipient 완료 정책이 추적할 source aggregate 기간 식별자. 원시 스크리닝 샘플과는 무관하다. */
data class SourceAggregateRef(
    val periodStart: Long,
    val periodEnd: Long,
)

/** fan-out 정책을 확정하지 않고도 수신 기기별 전달 상태를 구분하는 로컬 키. */
data class DigestDeliveryKey(
    val digestId: String,
    val recipientDeviceId: String,
) {
    init {
        require(digestId.isNotBlank()) { "digestId must not be blank" }
        require(recipientDeviceId.isNotBlank()) { "recipientDeviceId must not be blank" }
    }
}

/**
 * 부모 ACK 전까지 자녀 기기에 유지하는 암호문.
 * 구현 저장소는 반드시 Keystore로 보호된 암호화 저장소여야 하며 백업 대상이 되어서는 안 된다.
 */
data class PendingDigest(
    val digestId: String,
    val createdAt: Long,
    /** 이 암호문을 봉인한 trusted parent device. ACK peer와 일치해야 한다. */
    val recipientDeviceId: String,
    val encryptedDigest: EncryptedDigest,
    val deliveryState: DeliveryState,
    val sourceAggregate: SourceAggregateRef,
) {
    val deliveryKey: DigestDeliveryKey
        get() = DigestDeliveryKey(digestId, recipientDeviceId)

    init {
        require(digestId == encryptedDigest.digestId) { "pending/envelope digestId mismatch" }
        require(recipientDeviceId.isNotBlank()) { "recipientDeviceId must not be blank" }
        require(deliveryState != DeliveryState.CREATED) {
            "CREATED state has no ciphertext and must not be persisted as PendingDigest"
        }
    }

    fun transitionTo(next: DeliveryState): PendingDigest {
        require(next in allowedTransitions.getValue(deliveryState)) {
            "invalid digest delivery transition: $deliveryState -> $next"
        }
        return copy(deliveryState = next)
    }

    private companion object {
        val allowedTransitions = mapOf(
            DeliveryState.SEALED to setOf(DeliveryState.WAITING_FOR_PARENT),
            // 전송 성공 직후 로컬 상태 기록 전 ACK가 도착할 수 있으므로 ACKED도 허용한다.
            DeliveryState.WAITING_FOR_PARENT to setOf(DeliveryState.SENT, DeliveryState.ACKED),
            // ACK 유실 시 같은 암호문 재전송을 허용한다.
            DeliveryState.SENT to setOf(DeliveryState.SENT, DeliveryState.ACKED),
            DeliveryState.ACKED to emptySet(),
            DeliveryState.CREATED to setOf(DeliveryState.SEALED),
        )
    }
}

/**
 * 암호화된 child-local pending 저장소 계약.
 *
 * [purgeAcknowledged]는 ACK tombstone을 남겨 동일 ACK가 다시 와도 성공으로 처리해야 한다.
 * 서버 mailbox나 digest history를 이 인터페이스의 구현으로 사용하면 안 된다.
 */
interface PendingDigestStore {
    suspend fun save(pending: PendingDigest)
    suspend fun find(key: DigestDeliveryKey): PendingDigest?
    /**
     * 현재 상태가 [expectedStates] 중 하나일 때만 [nextState]로 원자적으로 바꾼다.
     * 현재 레코드(전이 성공 결과 또는 경쟁자가 먼저 바꾼 결과)를 반환하고, 없으면 null을 반환한다.
     */
    suspend fun transition(
        key: DigestDeliveryKey,
        expectedStates: Set<DeliveryState>,
        nextState: DeliveryState,
    ): PendingDigest?
    /** ACKED 레코드 삭제와 tombstone 기록을 원자적·멱등으로 수행한다. */
    suspend fun purgeAcknowledged(key: DigestDeliveryKey)
    suspend fun wasAcknowledged(key: DigestDeliveryKey): Boolean
}

/**
 * delivery ACK를 source aggregate 정책에 반영한다. 구현은 멱등이어야 한다.
 * 단일 recipient면 즉시 정리할 수 있고, fan-out을 채택하면 확정된 recipient 완료 조건을 적용한다.
 */
fun interface SourceAggregateAckPolicy {
    suspend fun onDeliveryAcknowledged(
        source: SourceAggregateRef,
        deliveryKey: DigestDeliveryKey,
    )
}

enum class DigestAckResult {
    ACKED_AND_PURGED,
    ALREADY_ACKED,
    UNKNOWN_DIGEST,
}

/**
 * 부모 기기의 암호화 로컬 저장소 계약.
 *
 * 복호화된 digest 저장과 processed digestId 기록을 하나의 원자적 transaction으로 수행해야 한다.
 * 복호화 또는 저장이 실패하기 전에 ID만 예약하면 재전송을 영구적으로 누락할 수 있으므로 금지한다.
 */
fun interface ReceivedDigestStore {
    /** 처음 정상 저장했으면 true, 이미 원자적으로 저장된 digestId이면 false. */
    suspend fun storeIfAbsent(digest: ContextDigest): Boolean
}

class DigestDeduplicator(private val store: ReceivedDigestStore) {
    suspend fun storeOnce(digest: ContextDigest): Boolean = store.storeIfAbsent(digest)
}
