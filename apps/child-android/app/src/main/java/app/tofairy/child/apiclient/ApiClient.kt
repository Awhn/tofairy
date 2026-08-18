package app.tofairy.child.apiclient

import app.tofairy.child.digest.EncryptedDigest
import app.tofairy.child.sensing.ConsentState

/**
 * 백엔드 계약 클라이언트 (CLAUDE.md §8, `docs/40` 계약 소비).
 *
 * 자녀 앱이 의존하는 요청/relay 계약만 노출한다. 부모 전용 인증과 기기 승인 API는 부모 앱
 * 책임이다. digest mailbox/history 조회 API는 존재하지 않는다.
 *
 * 불변식:
 *  - #1: 송신은 digest 모듈을 통해서만. apiclient 는 [EncryptedDigest]만 활성 relay로 전달한다.
 *        서버에 암호문 mailbox를 만들지 않으며 평문 다이제스트를 받지 않는다.
 *  - #6: [consentStatus] 는 센싱 게이트의 권위 있는 소스(`docs/40` §2 `GET /v1/consent/status`).
 * 백엔드 미구현 동안 [MockApiClient] 로 앱을 선개발한다.
 */
interface ApiClient {

    /**
     * `docs/40` §2 — `GET /v1/consent/status`.
     * 앱 재기동 시 최신 법정대리인 동의 상태를 확인한다. 조회 실패/UNKNOWN은 sensing 허용이
     * 아니며 [ConsentSynchronizer][app.tofairy.child.apiclient.ConsentSynchronizer]가 fail-closed 한다.
     */
    suspend fun consentStatus(): Result<ConsentStatus>

    /**
     * `docs/40` §3 — `POST /v1/pairing/claim`.
     * 부모가 만든 단기 코드로 페어링에 참여하고 자녀 기기 스코프 토큰(📱)을 발급받는다.
     */
    suspend fun claimPairing(pairingCode: String): Result<DeviceToken>

    /**
     * `docs/40` §3 — `POST /v1/pairing/keys`.
     * 검증된 E2EE primitive가 내보낸 자녀 기기 공개 keyset을 등록한다. 특정 curve/template은
     * Tink Android 지원을 확인한 뒤 확정하며, 개인 keyset은 기기 secure storage 밖으로 내보내지 않는다.
     */
    suspend fun registerChildEncryptionPublicKey(
        pairingId: String,
        publicKeyset: ByteArray,
    ): Result<Unit>

    /**
     * 현재 pairing의 승인된 부모 기기별 공개키 집합을 조회한다. 각 부모 private key는 해당
     * 기기의 secure storage에만 있고 export/import하지 않는다. fan-out 정책은 아직 Open Issue다.
     */
    suspend fun fetchTrustedParentDeviceKeys(pairingId: String): Result<TrustedParentDeviceKeys>

    /**
     * 활성 child-parent relay tunnel로 E2EE envelope를 전달한다. 서버는 digest를 mailbox/history로
     * 저장하지 않는다. 부모가 offline이면 실패하며 child pending store가 재전송을 맡는다.
     */
    suspend fun relayDigest(digest: EncryptedDigest): Result<Unit>
}

/** `GET /v1/consent/status` 응답. */
data class ConsentStatus(
    val state: ConsentState,
    /** 상태 변경마다 서버가 단조 증가시키는 ordering 값. 클라이언트 시계가 아니다. */
    val revision: Long,
    val updatedAt: Long,
) {
    init {
        require(revision >= 0) { "revision must not be negative" }
        require(updatedAt >= 0) { "updatedAt must not be negative" }
    }
}

/** `POST /v1/pairing/claim` 으로 발급된 자녀 기기 스코프 토큰(📱). */
data class DeviceToken(
    val token: String,
    val pairingId: String,
)

/** 승인된 부모 기기 하나의 공개키. 특정 암호 primitive를 앱 계약에서 직접 가정하지 않는다. */
class TrustedParentDeviceKey(
    val parentDeviceId: String,
    val publicKeyset: ByteArray,
    /** 화면 대조용 짧은 지문(예: 6단어/숫자). 진본성 근거는 이 대조다(서버 아님). */
    val fingerprint: String,
) {
    init {
        require(parentDeviceId.isNotBlank()) { "parentDeviceId must not be blank" }
        require(publicKeyset.isNotEmpty()) { "publicKeyset must not be empty" }
        require(fingerprint.isNotBlank()) { "fingerprint must not be blank" }
    }
}

/** pairing에 현재 승인되어 있고 revoke되지 않은 부모 기기의 수신 공개키 집합. */
data class TrustedParentDeviceKeys(
    val pairingId: String,
    val version: Long,
    val devices: List<TrustedParentDeviceKey>,
) {
    init {
        require(pairingId.isNotBlank()) { "pairingId must not be blank" }
        require(version >= 0) { "version must not be negative" }
        require(devices.map { it.parentDeviceId }.distinct().size == devices.size) {
            "parent device ids must be unique"
        }
    }
}
