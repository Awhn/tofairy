package app.tofairy.child.core

/**
 * 라이브 접근성 이벤트·민감 중간 결과를 즉시 소비하는 메모리 전용 타입.
 *
 * 규칙:
 *  - 이 객체 자체가 디스크/로그/네트워크로 가는 경로는 0. (직렬화·toString 노출 금지)
 *  - 판단(screening/router)에 소비된 직후 [discard] 로 즉시 폐기한다.
 *  - 이 타입은 절대 @Serializable 이 되어서는 안 된다. localstore/digest 로 흘러들면 안 된다.
 *
 * Claude Code 가드: 이 인터페이스를 구현하는 타입이 Serializable/Parcelable 을 함께 구현하거나,
 * Room/DataStore/파일/Retrofit 바디로 흘러가는 코드는 머지 금지 — 플래그 대상.
 *
 * 일일 A축용 스크린샷은 이 타입을 저장하는 예외가 아니다. 별도의 최소화된 `ScreeningSample`을
 * 암호화 임시 저장소에 넣고 배치 성공 뒤 삭제하는 목적 제한 생명주기를 사용한다.
 */
sealed interface Ephemeral {
    /** 내부 버퍼를 가능한 한 비우고, 이후 접근을 무효화한다. */
    fun discard()

    /** 폐기되었는지. 폐기 후 접근 시 [check] 로 빠르게 실패시킨다. */
    val isDiscarded: Boolean
}

/**
 * 센싱 단계의 라이브 신호(포그라운드 앱, 전환 이벤트와 선택적인 메모리 전용 텍스트).
 * checkpoint sampler 또는 사용패턴 규칙이 소비한 뒤 즉시 [discard]한다. 일일 screenshot/metadata는
 * 이 객체 자체를 저장하지 않고 별도의 최소 `ScreeningSample`로 만든다.
 */
class EphemeralSignal(
    packageName: String,
    /** 전환/콘텐츠 이벤트 종류. */
    val kind: SignalKind,
    rawText: String?,
    /** 신호 발생 시각(단조 시계 기준, epoch 가 아님). */
    val atElapsedMillis: Long,
) : Ephemeral {

    private var _packageName: String? = packageName
    private var _rawText: String? = rawText
    override var isDiscarded: Boolean = false
        private set

    /** 포그라운드 앱 식별자도 원시 사용 metadata이므로 폐기 뒤 접근할 수 없다. */
    val packageName: String
        get() {
            check(!isDiscarded) { "EphemeralSignal already discarded" }
            return _packageName.orEmpty()
        }

    /**
     * 선택적인 화면 텍스트 원문. 현재 AccessibilityService는 이를 수집하지 않는다.
     * 향후 정당한 메모리 전용 소비자가 생겨도 읽은 즉시 신호를 폐기하고 저장하지 않는다.
     * 절대 로깅/저장/전송하지 말 것.
     */
    val rawText: String?
        get() {
            check(!isDiscarded) { "EphemeralSignal already discarded" }
            return _rawText
        }

    override fun discard() {
        _packageName = null
        _rawText = null
        isDiscarded = true
    }

    /** 진단 출력에서 패키지명과 원문을 모두 숨긴다. */
    override fun toString(): String =
        "EphemeralSignal(kind=$kind, discarded=$isDiscarded)"

    enum class SignalKind { WINDOW_STATE_CHANGED, WINDOW_CONTENT_CHANGED }
}

/**
 * 저장하지 않는 즉시 처리 경로용 콘텐츠 핸들. 일일 A축의 암호화 `ScreeningSample`과는 별개다.
 * 이 객체를 사용한 즉시 판단이 있다면 호출 종료와 함께 폐기한다.
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
 * 라이브 ephemeral 신호 소비 진입점은 이 헬퍼로 감싸 누수를 구조적으로 막는다.
 * 일일 batch의 encrypted `ScreeningSample` lifecycle에는 이 타입을 사용하지 않는다.
 */
inline fun <E : Ephemeral, R> E.consume(block: (E) -> R): R =
    try {
        block(this)
    } finally {
        discard()
    }
