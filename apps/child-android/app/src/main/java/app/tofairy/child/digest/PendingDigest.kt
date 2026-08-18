package app.tofairy.child.digest

/** 자녀 기기에서 관리하는 다이제스트 전달 상태. PURGED는 레코드가 제거된 상태라 enum에 두지 않는다. */
enum class DeliveryState {
    CREATED,
    SEALED,
    WAITING_FOR_PARENT,
    SENT,
    ACKED,
}

/** ACK 뒤 정리해야 할 source aggregate의 기간 식별자. 원시 스크리닝 샘플과는 무관하다. */
data class SourceAggregateRef(
    val periodStart: Long,
    val periodEnd: Long,
)

/**
 * 부모 ACK 전까지 자녀 기기에 유지하는 암호문.
 * 구현 저장소는 반드시 Keystore로 보호된 암호화 저장소여야 하며 백업 대상이 되어서는 안 된다.
 */
data class PendingDigest(
    val digestId: String,
    val createdAt: Long,
    val encryptedDigest: EncryptedDigest,
    val deliveryState: DeliveryState,
    val sourceAggregate: SourceAggregateRef,
) {
    init {
        require(digestId == encryptedDigest.digestId) { "pending/envelope digestId mismatch" }
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
    suspend fun find(digestId: String): PendingDigest?
    suspend fun replace(pending: PendingDigest)
    suspend fun purgeAcknowledged(digestId: String)
    suspend fun wasAcknowledged(digestId: String): Boolean
}

/** ACK 뒤 해당 digest가 사용한 기간 집계만 정리한다. 구현은 멱등이어야 한다. */
fun interface SourceAggregateAcknowledger {
    suspend fun purge(source: SourceAggregateRef)
}

enum class DigestAckResult {
    ACKED_AND_PURGED,
    ALREADY_ACKED,
    UNKNOWN_DIGEST,
}

/** 부모 기기의 중복 저장 방지용 최소 계약. 구현은 부모의 암호화 로컬 저장소를 사용한다. */
fun interface ReceivedDigestRegistry {
    /** 처음 본 digestId이면 true, 이미 처리한 ID이면 false. */
    suspend fun recordIfAbsent(digestId: String): Boolean
}

class DigestDeduplicator(private val registry: ReceivedDigestRegistry) {
    suspend fun shouldProcess(digestId: String): Boolean {
        require(digestId.isNotBlank()) { "digestId must not be blank" }
        return registry.recordIfAbsent(digestId)
    }
}
