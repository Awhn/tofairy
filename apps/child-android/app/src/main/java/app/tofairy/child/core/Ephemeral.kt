package app.tofairy.child.core

/**
 * 불변식 #1/#2 — 콘텐츠 원문·민감 라벨은 [Ephemeral] 타입으로만 다루고 즉시 폐기한다.
 *
 * 규칙:
 *  - 디스크/로그/네트워크로 가는 경로 0. (직렬화·toString 노출 금지)
 *  - 판단(screening/router)에 소비된 직후 [discard] 로 즉시 폐기한다.
 *  - 이 타입은 절대 @Serializable 이 되어서는 안 된다. localstore/digest 로 흘러들면 안 된다.
 *
 * Claude Code 가드: 이 인터페이스를 구현하는 타입이 Serializable/Parcelable 을 함께 구현하거나,
 * Room/DataStore/파일/Retrofit 바디로 흘러가는 코드는 머지 금지 — 플래그 대상.
 */
sealed interface Ephemeral {
    /** 내부 버퍼를 가능한 한 비우고, 이후 접근을 무효화한다. */
    fun discard()

    /** 폐기되었는지. 폐기 후 접근 시 [check] 로 빠르게 실패시킨다. */
    val isDiscarded: Boolean
}

/**
 * 센싱 단계의 원시 신호(포그라운드 앱, node-tree 텍스트, 전환 이벤트).
 * 메모리 전용. screening 으로 전달 후 즉시 [discard].
 */
class EphemeralSignal(
    /** 포그라운드 앱 패키지명. 민감 라벨이 아니라 라우팅 키이므로 집계 가능하지만 원문 텍스트는 아니다. */
    val packageName: String,
    /** 전환/콘텐츠 이벤트 종류. */
    val kind: SignalKind,
    rawText: String?,
    /** 신호 발생 시각(단조 시계 기준, epoch 가 아님). */
    val atElapsedMillis: Long,
) : Ephemeral {

    private var _rawText: String? = rawText
    override var isDiscarded: Boolean = false
        private set

    /**
     * 화면 텍스트 원문. 오직 screening(A축/B축) 안에서만 읽고, 읽은 즉시 신호를 폐기한다.
     * 절대 로깅/저장/전송하지 말 것.
     */
    val rawText: String?
        get() {
            check(!isDiscarded) { "EphemeralSignal already discarded" }
            return _rawText
        }

    override fun discard() {
        _rawText = null
        isDiscarded = true
    }

    /** 진단 출력에서도 원문이 새지 않도록 한다(불변식 #1/#2). */
    override fun toString(): String =
        "EphemeralSignal(pkg=$packageName, kind=$kind, discarded=$isDiscarded)"

    enum class SignalKind { WINDOW_STATE_CHANGED, WINDOW_CONTENT_CHANGED }
}

/**
 * A축(적절성) 판단에 쓰일 콘텐츠 스냅샷. 멀티모달 입력(텍스트/화면)을 담을 수 있다.
 * 판단 직후 폐기. 출력은 등급 라벨([app.tofairy.child.screening.axisa.AgeRating])뿐이며 그 라벨도 폐기 대상.
 */
class EphemeralContent(
    text: String?,
    /** 화면 비트맵 등 멀티모달 페이로드 핸들(소유권 이전). nullable. */
    frame: AutoCloseable?,
) : Ephemeral {

    private var _text: String? = text
    private var _frame: AutoCloseable? = frame
    override var isDiscarded: Boolean = false
        private set

    val text: String?
        get() {
            check(!isDiscarded) { "EphemeralContent already discarded" }
            return _text
        }

    val frame: AutoCloseable?
        get() {
            check(!isDiscarded) { "EphemeralContent already discarded" }
            return _frame
        }

    override fun discard() {
        _frame?.let { runCatching { it.close() } }
        _frame = null
        _text = null
        isDiscarded = true
    }

    override fun toString(): String = "EphemeralContent(discarded=$isDiscarded)"
}

/**
 * 폐기 보장 헬퍼: [block] 실행 후 항상 [Ephemeral.discard] 한다.
 * 모든 screening/router 진입점은 ephemeral 입력을 이 헬퍼로 감싸 누수를 구조적으로 막는다.
 */
inline fun <E : Ephemeral, R> E.consume(block: (E) -> R): R =
    try {
        block(this)
    } finally {
        discard()
    }
