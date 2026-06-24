package app.tofairy.child.sensing

import app.tofairy.child.core.EphemeralSignal
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 센싱 시작 게이트 (불변식 #4, #6).
 *
 * 게이트는 다음이 모두 참일 때만 열린다:
 *  - [consentConfirmed]  : 부모 동의 확인됨 (#6)
 *  - [sessionActive]     : 요정 모드 세션 활성(케이스 B 경계, #4)
 *  - 접근성 서비스 연결됨
 *
 * 게이트가 닫히면 [SensingAccessibilityService] 는 신호를 만들지도 흘리지도 않는다.
 * 세션 종료/동의 철회 시 즉시 닫는다.
 */
object SensingGate {
    private val consentConfirmed = AtomicBoolean(false)
    private val sessionActive = AtomicBoolean(false)
    private val serviceConnected = AtomicBoolean(false)

    /** 신호 싱크. 게이트가 열려 있을 때만 설정/사용된다. */
    @Volatile
    var sink: SignalSink? = null
        private set

    val isOpen: Boolean
        get() = consentConfirmed.get() && sessionActive.get() && serviceConnected.get()

    /** #6 동의 게이트. 부모 동의 확인/철회. */
    fun setConsent(confirmed: Boolean) {
        consentConfirmed.set(confirmed)
        if (!confirmed) sink = null
    }

    /** #4 세션 경계. 세션 시작 시 싱크를 연결, 종료 시 해제. */
    fun openSession(sink: SignalSink) {
        this.sink = sink
        sessionActive.set(true)
    }

    fun closeSession() {
        sessionActive.set(false)
        sink = null
    }

    internal fun onServiceConnected() {
        serviceConnected.set(true)
    }

    internal fun onServiceDisconnected() {
        serviceConnected.set(false)
        sink = null
    }
}

/**
 * 신호 소비자. 구현체는 신호를 screening 으로 넘기고 반드시 폐기한다(core.consume).
 * 게이트 안쪽에서만 호출된다.
 */
fun interface SignalSink {
    fun onSignal(signal: EphemeralSignal)
}
