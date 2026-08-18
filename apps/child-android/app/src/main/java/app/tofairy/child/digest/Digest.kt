package app.tofairy.child.digest

import kotlinx.serialization.Serializable

/**
 * 부모에게 전달할 기간 집계. 현재 누적 관계 상태와 의미를 분리한다.
 * 원시 화면·URL·검색어·앱 내부 콘텐츠명·A축 dimension 결과를 표현할 필드가 없다.
 */
@Serializable
data class PeriodAggregate(
    val breakSuggestions: Int,
    val breaksAccepted: Int,
    val interventions: Int,
)

/** 부모에게 보여 줄 수 있도록 명시적으로 허용된 현재 관계 상태 projection. */
@Serializable
data class RelationshipSnapshot(
    val promiseStreakDays: Int,
    val bondLevel: Int,
)

/**
 * 검수된 부모용 추상화 코드. 임의 문자열이나 콘텐츠별 민감 분류 결과를 허용하지 않는다.
 * 목록 확장은 프라이버시 검토와 부모용 카피 매핑을 함께 거쳐야 한다.
 */
@Serializable
enum class DigestHighlight {
    LATE_NIGHT_USE,
    LONG_SESSION_PATTERN,
    BREAK_ACCEPTANCE_IMPROVED,
    PROMISE_STREAK,
}

/**
 * 부모에게 내보내는 유일한 행동 데이터 산출물.
 *
 * 평문은 자녀 기기 안에서만 만든다. 기기 간 전송 경로에서는 즉시 [DigestSealer]로 봉인하며,
 * 공유 기기 안의 부모 영역에는 PIN 인증을 거친 Local Data Gate를 통해서만 전달할 수 있다.
 * E2EE의 목표는 이 내용의 기밀성이다. 서비스 운영용 device/pairing/routing/connection metadata까지
 * 숨기거나 서버 unlinkability·트래픽 분석 방지·강한 forward secrecy를 제공한다고 주장하지 않는다.
 */
@Serializable
data class ContextDigest(
    val digestId: String,
    val periodStart: Long,
    val periodEnd: Long,
    val aggregate: PeriodAggregate,
    val relationship: RelationshipSnapshot,
    val highlights: List<DigestHighlight> = emptyList(),
    val schemaVersion: Int = SCHEMA_VERSION,
) {
    init {
        require(digestId.isNotBlank()) { "digestId must not be blank" }
        require(periodEnd >= periodStart) { "periodEnd must be at or after periodStart" }
    }

    companion object {
        const val SCHEMA_VERSION = 1
    }
}

/**
 * Tink Hybrid Encryption/HPKE 적용을 전제로 한 relay wire envelope.
 *
 * 암호 라이브러리가 encapsulated key·nonce·tag framing을 ciphertext 안에서 관리하므로 이를
 * 애플리케이션 필드로 직접 재구현하지 않는다. `digestId`, routing token, crypto version은
 * 서버가 처리 가능한 metadata이며 다이제스트 내용이 아니다. 실제 template과 keyset 저장
 * 방식은 `docs/50_데이터_프라이버시_구현.md` 검증 뒤 확정한다.
 */
class EncryptedDigest(
    val digestId: String,
    val routingToken: String,
    val cryptoVersion: Int,
    val ciphertext: ByteArray,
) {
    init {
        require(digestId.isNotBlank()) { "digestId must not be blank" }
        require(routingToken.isNotBlank()) { "routingToken must not be blank" }
        require(cryptoVersion > 0) { "cryptoVersion must be positive" }
        require(ciphertext.isNotEmpty()) { "ciphertext must not be empty" }
    }
}

/**
 * 봉인 시 선택한 trusted parent device의 recipient material.
 * public keyset은 해당 봉인 호출 동안만 사용하고 pending/network DTO에 복사하지 않는다.
 */
class DigestRecipient(
    val parentDeviceId: String,
    val publicKeyset: ByteArray,
    val trustedSetRevision: Long,
) {
    init {
        require(parentDeviceId.isNotBlank()) { "parentDeviceId must not be blank" }
        require(publicKeyset.isNotEmpty()) { "publicKeyset must not be empty" }
        require(trustedSetRevision >= 0) { "trustedSetRevision must not be negative" }
    }
}
