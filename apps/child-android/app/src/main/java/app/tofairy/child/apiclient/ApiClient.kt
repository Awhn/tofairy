package app.tofairy.child.apiclient

import app.tofairy.child.digest.EncryptedDigest

/**
 * 백엔드 계약 클라이언트 (CLAUDE.md §8, `docs/40` 계약 소비).
 *
 * 자녀 앱이 의존하는 엔드포인트만 노출한다. 부모 전용(`auth/*`, `consent/verify`,
 * `pairing/initiate`, `digests` 조회/ack)은 부모 앱 책임이라 여기 두지 않는다.
 *
 * 불변식:
 *  - #1: 송신은 digest 모듈을 통해서만. apiclient 는 [EncryptedDigest] (암호문 blob)만 전송한다.
 *        평문 다이제스트를 받지 않는다. (`docs/40` §5 — 콘텐츠/로그 업로드 엔드포인트 부재)
 *  - #6: [consentStatus] 는 센싱 게이트의 권위 있는 소스(`docs/40` §2 `GET /v1/consent/status`).
 * 백엔드 미구현 동안 [MockApiClient] 로 앱을 선개발한다.
 */
interface ApiClient {

    /**
     * `docs/40` §2 — `GET /v1/consent/status`.
     * 센싱 활성화 가능 여부(법정대리인 동의 유효). 자녀 앱은 센싱 시작 전 반드시 확인(#6).
     */
    suspend fun consentStatus(): Result<ConsentStatus>

    /**
     * `docs/40` §3 — `POST /v1/pairing/claim`.
     * 부모가 만든 단기 코드로 페어링에 참여하고 자녀 기기 스코프 토큰(📱)을 발급받는다.
     */
    suspend fun claimPairing(pairingCode: String): Result<DeviceToken>

    /**
     * `docs/40` §3 — `POST /v1/pairing/keys`.
     * 자녀 기기의 X25519 공개키를 등록한다. 개인키는 절대 전송하지 않는다(Keystore 내부).
     */
    suspend fun registerPublicKey(pairingId: String, x25519PublicKey: ByteArray): Result<Unit>

    /**
     * `docs/40` §3 — `GET /v1/pairing/{id}/keys`.
     * 상대(부모) 공개키 + 지문(화면 대조용)을 조회한다. 진본성은 기기 간 지문 대조로 확보.
     */
    suspend fun fetchPeerKeys(pairingId: String): Result<PeerKeys>

    /**
     * `docs/40` §4 — `POST /v1/digests`.
     * E2EE 암호문 업로드. 평문은 인자로 받지 않는다.
     * 케이스 B(동일 기기)에서는 이 경로를 건너뛰고 로컬 전달한다(`docs/50` §1.8).
     */
    suspend fun uploadDigest(digest: EncryptedDigest): Result<Unit>
}

/** `GET /v1/consent/status` 응답. */
data class ConsentStatus(
    val sensingConsented: Boolean,
    val updatedAt: Long,
)

/** `POST /v1/pairing/claim` 으로 발급된 자녀 기기 스코프 토큰(📱). */
data class DeviceToken(
    val token: String,
    val pairingId: String,
)

/** `GET /v1/pairing/{id}/keys` 응답 — 부모 공개키 + 지문(대조용). */
class PeerKeys(
    val parentX25519PublicKey: ByteArray,
    /** 화면 대조용 짧은 지문(예: 6단어/숫자). 진본성 근거는 이 대조다(서버 아님). */
    val fingerprint: String,
)
