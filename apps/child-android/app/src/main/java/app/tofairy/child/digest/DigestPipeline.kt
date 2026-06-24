package app.tofairy.child.digest

import app.tofairy.child.apiclient.ApiClient
import app.tofairy.child.localstore.RelationshipState

/**
 * 다이제스트 생성·E2EE 암호화·송신 — 유일 송신 게이트 (CLAUDE.md §3, §8).
 *
 * 흐름: localstore 집계 → [ContextDigest] (평문, 기기 내) → [DigestSealer] E2EE → [EncryptedDigest]
 *       → [ApiClient.uploadDigest] (암호문 blob 만 전송).
 * 평문 다이제스트는 절대 apiclient 로 넘어가지 않는다.
 */
class DigestPipeline(
    private val sealer: DigestSealer,
    private val apiClient: ApiClient,
) {
    suspend fun buildSealAndSend(
        state: RelationshipState,
        periodStart: Long,
        periodEnd: Long,
        highlights: List<String> = emptyList(),
    ): Result<Unit> {
        val digest = ContextDigest(
            periodStart = periodStart,
            periodEnd = periodEnd,
            promiseKeptDays = state.promiseStreakDays,
            breaksAccepted = state.breaksAccepted,
            bondLevel = state.bondLevel,
            highlights = highlights,
        )
        val sealed = sealer.seal(digest)
        return apiClient.uploadDigest(sealed)
    }
}

/**
 * E2EE 봉인기. 페어링(키교환)으로 얻은 부모 공개키로 [ContextDigest] 를 암호화한다.
 * 최종 구현은 libsodium crypto_box / sealed box (../../docs/50 §1). 골격 단계는 인터페이스만.
 */
interface DigestSealer {
    fun seal(digest: ContextDigest): EncryptedDigest
}
