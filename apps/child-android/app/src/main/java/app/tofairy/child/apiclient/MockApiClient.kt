package app.tofairy.child.apiclient

import app.tofairy.child.digest.EncryptedDigest

/**
 * 백엔드 미구현 동안 앱을 선개발하기 위한 페이크 구현 (CLAUDE.md §8, §10-6).
 * 네트워크로 나가지 않으며, 동의·페어링 상태를 인메모리로 흉내 낸다.
 */
class MockApiClient(
    @Volatile var sensingConsented: Boolean = false,
) : ApiClient {

    /** 업로드된 암호문 blob 만 보관(평문 없음). 테스트 검증용. */
    val uploadedDigests = mutableListOf<EncryptedDigest>()

    override suspend fun consentStatus(): Result<ConsentStatus> =
        Result.success(ConsentStatus(sensingConsented, System.currentTimeMillis()))

    override suspend fun claimPairing(pairingCode: String): Result<DeviceToken> =
        Result.success(DeviceToken(token = "mock-device-token", pairingId = "mock-pairing"))

    override suspend fun registerPublicKey(pairingId: String, x25519PublicKey: ByteArray): Result<Unit> =
        Result.success(Unit)

    override suspend fun fetchPeerKeys(pairingId: String): Result<PeerKeys> =
        Result.success(
            PeerKeys(
                parentX25519PublicKey = ByteArray(32) { 0x42 },
                fingerprint = "mock-1234-5678",
            ),
        )

    override suspend fun uploadDigest(digest: EncryptedDigest): Result<Unit> {
        // 암호문 blob 만 보관(평문 없음). 실제 송신 없음.
        uploadedDigests += digest
        return Result.success(Unit)
    }
}
