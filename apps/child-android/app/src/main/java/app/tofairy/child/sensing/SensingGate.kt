package app.tofairy.child.sensing

import app.tofairy.child.core.EphemeralSignal
import app.tofairy.child.session.FairySessionIdentity

/**
 * 센싱 시작 게이트 (불변식 #4, #6).
 *
 * 게이트는 다음이 모두 참일 때만 열린다:
 *  - [ConsentState.CONFIRMED]: 부모 동의 확인됨
 *  - 식별 가능한 Fairy Session 활성: 자녀 전용폰(케이스 A)과 공유폰(케이스 B) 모두 필수
 *  - 접근성 서비스 연결됨
 *
 * 게이트가 닫히면 [SensingAccessibilityService] 는 신호를 만들지도 흘리지도 않는다.
 * 세션 종료/동의 철회 시 즉시 닫는다.
 */
object SensingGate {
    private val lock = Any()

    @Volatile
    private var currentConsentState: ConsentState = ConsentState.UNKNOWN

    @Volatile
    private var serviceConnected: Boolean = false

    private var activeSession: SessionBinding? = null

    private data class SessionBinding(
        val identity: FairySessionIdentity,
        val sink: SignalSink,
    )

    val consentState: ConsentState
        get() = currentConsentState

    val activeSessionIdentity: FairySessionIdentity?
        get() = synchronized(lock) { activeSession?.identity }

    /** 게이트가 열린 순간에만 접근 가능한 신호 싱크. 닫힌 상태에서는 항상 null이다. */
    val sink: SignalSink?
        get() = synchronized(lock) {
            activeSession?.sink.takeIf { isOpenLocked() }
        }

    val isOpen: Boolean
        get() = synchronized(lock) { isOpenLocked() }

    /**
     * 최신 동의 상태를 적용한다. REVOKED/UNKNOWN을 수신하면 활성 세션 중에도 즉시 닫힌다.
     * 세션 바인딩 자체는 유지하므로, 적법한 재동의가 확인되면 같은 활성 세션에서 다시 열 수 있다.
     */
    fun setConsentState(state: ConsentState) {
        synchronized(lock) {
            currentConsentState = state
        }
    }

    /**
     * 구형 온보딩 호출부의 일시적 컴파일 호환용이다.
     * 신규 코드는 반드시 [setConsentState]와 명시적 [ConsentState]를 사용해야 한다.
     */
    @Deprecated(
        message = "Use setConsentState(ConsentState) so UNKNOWN and REVOKED remain distinct",
        replaceWith = ReplaceWith("setConsentState(if (confirmed) ConsentState.CONFIRMED else ConsentState.REVOKED)"),
    )
    fun setConsent(confirmed: Boolean) {
        setConsentState(if (confirmed) ConsentState.CONFIRMED else ConsentState.REVOKED)
    }

    /** 세션 생성 경로 외부에서 호출하지 않는다. 동일 프로세스에서 활성 세션은 하나뿐이다. */
    internal fun openSession(identity: FairySessionIdentity, sink: SignalSink) {
        synchronized(lock) {
            check(activeSession == null) {
                "A Fairy Session is already active: ${activeSession?.identity?.sessionId}"
            }
            activeSession = SessionBinding(identity, sink)
        }
    }

    /** 오래된 세션 객체가 새 세션을 닫지 못하도록 identity가 일치할 때만 닫는다. */
    internal fun closeSession(identity: FairySessionIdentity): Boolean = synchronized(lock) {
        if (activeSession?.identity != identity) return@synchronized false
        activeSession = null
        true
    }

    internal fun onServiceConnected() {
        synchronized(lock) {
            serviceConnected = true
        }
    }

    internal fun onServiceDisconnected() {
        synchronized(lock) {
            serviceConnected = false
        }
    }

    private fun isOpenLocked(): Boolean =
        currentConsentState == ConsentState.CONFIRMED &&
            activeSession != null &&
            serviceConnected

    /** 전역 Android 서비스 게이트를 격리해 검사하기 위한 JVM 테스트 전용 초기화 지점. */
    internal fun resetForTests() {
        synchronized(lock) {
            currentConsentState = ConsentState.UNKNOWN
            serviceConnected = false
            activeSession = null
        }
    }
}

/**
 * 신호 소비자. 구현체는 신호를 screening 으로 넘기고 반드시 폐기한다(core.consume).
 * 게이트 안쪽에서만 호출된다.
 */
fun interface SignalSink {
    fun onSignal(signal: EphemeralSignal)
}
