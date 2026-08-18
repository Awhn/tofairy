package app.tofairy.child.apiclient

import app.tofairy.child.digest.EncryptedDigest
import app.tofairy.child.sensing.ConsentState
import java.util.Collections

/**
 * 백엔드 미구현 동안 앱을 선개발하기 위한 페이크 구현 (CLAUDE.md §8, §10-6).
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

    @Volatile
    var registeredChildPublicKeyset: ByteArray? = null

    @Volatile
    var trustedParentDeviceKeys: TrustedParentDeviceKeys = TrustedParentDeviceKeys(
        pairingId = "mock-pairing",
        version = 1,
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

    override suspend fun registerChildEncryptionPublicKey(
        pairingId: String,
        publicKeyset: ByteArray,
    ): Result<Unit> = runCatching {
        require(publicKeyset.isNotEmpty()) { "publicKeyset must not be empty" }
        registeredChildPublicKeyset = publicKeyset.copyOf()
    }

    override suspend fun fetchTrustedParentDeviceKeys(
        pairingId: String,
    ): Result<TrustedParentDeviceKeys> = Result.success(trustedParentDeviceKeys)

    override suspend fun relayDigest(digest: EncryptedDigest): Result<Unit> {
        if (!relayAvailable) {
            return Result.failure(
                IllegalStateException("parent relay unavailable; digest must remain child-pending"),
            )
        }
        relayedDigests += digest
        return Result.success(Unit)
    }
}
