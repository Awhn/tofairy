package app.tofairy.child.apiclient

import app.tofairy.child.digest.EncryptedDigest
import app.tofairy.child.sensing.ConsentState
import java.util.Collections
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 백엔드 미구현 동안 앱을 선개발하기 위한 페이크 구현 (CLAUDE.md §10, §12).
 * 네트워크로 나가지 않으며, 동의·페어링 상태를 인메모리로 흉내 낸다.
 */
class MockApiClient(
    @Volatile var currentConsentState: ConsentState = ConsentState.UNKNOWN,
    @Volatile var consentRevision: Long = 0,
    @Volatile var consentUpdatedAt: Long = System.currentTimeMillis(),
) : ApiClient {

    /** 테스트가 relay 시도를 관찰하기 위한 메모리 기록일 뿐 production mailbox 계약이 아니다. */
    val relayedDigests: MutableList<EncryptedDigest> =
        Collections.synchronizedList(mutableListOf())

    @Volatile
    var relayAvailable: Boolean = true

    @Volatile
    var consentStatusAvailable: Boolean = true

    /** 테스트에서 relay 성공과 SENT 기록 사이의 ACK race를 재현하기 위한 hook. */
    var beforeRelaySuccess: (suspend (EncryptedDigest) -> Unit)? = null

    @Volatile
    var trustedParentDeviceKeys: TrustedParentDeviceKeys = TrustedParentDeviceKeys(
        pairingId = "mock-pairing",
        revision = 1,
        devices = listOf(
            TrustedParentDeviceKey(
                parentDeviceId = "mock-parent-device",
                publicKeyset = ByteArray(32) { 0x42 },
                fingerprint = "mock-1234-5678",
            ),
        ),
    )

    override suspend fun consentStatus(): Result<ConsentStatus> =
        if (consentStatusAvailable) {
            Result.success(
                ConsentStatus(
                    state = currentConsentState,
                    revision = consentRevision,
                    updatedAt = consentUpdatedAt,
                ),
            )
        } else {
            Result.failure(IllegalStateException("consent status unavailable"))
        }

    override suspend fun claimPairing(pairingCode: String): Result<DeviceToken> =
        Result.success(DeviceToken(token = "mock-device-token", pairingId = "mock-pairing"))

    override suspend fun fetchTrustedParentDeviceKeys(
        pairingId: String,
    ): Result<TrustedParentDeviceKeys> = Result.success(trustedParentDeviceKeys)

    override suspend fun relayDigest(digest: EncryptedDigest): Result<Unit> {
        if (!relayAvailable) {
            return Result.failure(
                IllegalStateException("parent relay unavailable; digest must remain child-pending"),
            )
        }
        beforeRelaySuccess?.invoke(digest)
        relayedDigests += digest
        return Result.success(Unit)
    }

    private val digestAckListeners = CopyOnWriteArrayList<DigestAckListener>()

    override fun subscribeDigestAcks(listener: DigestAckListener): DigestAckSubscription {
        digestAckListeners.add(listener)
        return DigestAckSubscription { digestAckListeners.remove(listener) }
    }

    /** 테스트 전용. production transport의 peer 인증을 흉내 내는 논리 ACK 주입 지점이다. */
    suspend fun emitAuthenticatedDigestAck(ack: DigestAck) {
        digestAckListeners.forEach { it.onDigestAck(ack) }
    }
}
