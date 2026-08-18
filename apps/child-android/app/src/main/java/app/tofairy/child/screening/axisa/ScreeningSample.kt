package app.tofairy.child.screening.axisa

/**
 * 암호화 임시 저장소의 이미지 레코드를 가리키는 불투명 핸들.
 * 이미지 bytes를 도메인·digest·API 타입에 직접 노출하지 않는다.
 */
@JvmInline
value class EncryptedLocalImage(val storageKey: String) {
    init {
        require(storageKey.isNotBlank()) { "storageKey must not be blank" }
    }

    override fun toString(): String = "EncryptedLocalImage(redacted)"
}

enum class SamplingReason {
    SESSION_STARTED,
    NEW_APP_OR_CONTENT,
    MEANINGFUL_SCREEN_CHANGE,
    SUSTAINED_CONTENT,
    LONG_SESSION_CHECKPOINT,
}

enum class ScreenTransitionContext {
    SESSION_ENTRY,
    APP_CHANGED,
    CONTENT_CHANGED,
    PERIODIC_CHECKPOINT,
}

/** 콘텐츠명 대신 사용할 수 있는 거친 분류. source가 없으면 UNKNOWN을 사용한다. */
enum class AppCategory {
    VIDEO,
    SOCIAL,
    GAME,
    BROWSER,
    EDUCATION,
    OTHER,
    UNKNOWN,
}

/**
 * 일일 A축에 필요한 최소 metadata. URL·검색어·화면 전체 텍스트·콘텐츠 제목 필드는 금지한다.
 * foregroundPackage는 필요성이 검증된 sampler에서만 채우며 기본값은 null이다.
 */
data class ScreeningMetadata(
    val sessionId: String,
    val sessionDurationMillis: Long,
    val samplingReason: SamplingReason,
    val transitionContext: ScreenTransitionContext,
    val appCategory: AppCategory = AppCategory.UNKNOWN,
    val foregroundPackage: String? = null,
) {
    init {
        require(sessionId.isNotBlank()) { "sessionId must not be blank" }
        require(sessionDurationMillis >= 0) { "session duration must not be negative" }
    }
}

enum class ScreeningSampleState {
    ENCRYPTED_LOCAL,
    SCREENING,
    SCREENED,
    AGGREGATED,
}

/**
 * 자녀 기기에서만 존재하는 목적 제한적 임시 샘플.
 * screenshot과 metadata는 암호화 저장하고 일일 분석 성공 후 삭제한다.
 */
data class ScreeningSample(
    val sampleId: String,
    val screenshot: EncryptedLocalImage,
    val capturedAt: Long,
    val metadata: ScreeningMetadata,
    val state: ScreeningSampleState = ScreeningSampleState.ENCRYPTED_LOCAL,
) {
    init {
        require(sampleId.isNotBlank()) { "sampleId must not be blank" }
    }
}

/**
 * Keystore 보호 암호화 임시 저장소 계약. backend/parent 구현으로 대체하면 안 된다.
 * 정확한 실패 재시도 보존 기간은 Open Issue다.
 */
interface ScreeningSampleStore {
    suspend fun pendingSamples(): List<ScreeningSample>
    suspend fun updateState(sampleId: String, state: ScreeningSampleState)
    suspend fun purgeRawSample(sampleId: String)
}
