package app.tofairy.child.digest

import kotlinx.serialization.Serializable

/**
 * 맥락 다이제스트 (CLAUDE.md §0, §3, §8).
 *
 * 부모에게 내보내는 유일한 산출물. 평문 [ContextDigest] 는 기기 안에서 만들어지고
 * 즉시 E2EE 로 암호화되어 [EncryptedDigest] (암호문 blob)가 된 뒤에만 apiclient 로 넘어간다.
 *
 * 불변식 #1/#2: 다이제스트는 집계/관계 수준의 맥락만 담는다. 원시 콘텐츠·민감 라벨 금지.
 */
@Serializable
data class ContextDigest(
    /** 다이제스트가 대표하는 기간(epoch millis). */
    val periodStart: Long,
    val periodEnd: Long,
    /** 집계 지표만. */
    val promiseKeptDays: Int,
    val breaksAccepted: Int,
    val bondLevel: Int,
    /** 부드러운 맥락 코드 목록(자유 텍스트 아님, 사전 정의 enum 코드 문자열). */
    val highlights: List<String> = emptyList(),
) {
    companion object {
        const val SCHEMA_VERSION = 1
    }
}

/**
 * E2EE 로 암호화된 다이제스트. apiclient 는 오직 이 타입만 송신하며 평문을 보지 않는다.
 *
 * 와이어 형태는 `../../../../../../docs/40` §4 `POST /v1/digests` 계약과 1:1 대응한다:
 * `{routing_token, ephemeral_pubkey, nonce, ciphertext, aad}`.
 * 암호 스킴은 `docs/50` §1 — X25519 ECDH + HKDF + XChaCha20-Poly1305, **ephemeral 송신키**.
 * 부모 개인키로만 복호 가능하며 서버(digest-relay)는 복호 불가.
 */
class EncryptedDigest(
    /** 서버가 부모 수신함으로 라우팅하는 데 쓰는 불투명 토큰(콘텐츠 아님). */
    val routingToken: ByteArray,
    /** 이번 봉인에만 쓰는 1회용 X25519 송신 공개키. 발신자 추적 방지. */
    val ephemeralPubkey: ByteArray,
    /** XChaCha20-Poly1305 논스(24바이트). */
    val nonce: ByteArray,
    /** 봉인된 다이제스트 본문. */
    val ciphertext: ByteArray,
    /** 인증된 추가 데이터(스키마 버전·라우팅 메타 등 평문 메타, 콘텐츠 아님). */
    val aad: ByteArray,
)
