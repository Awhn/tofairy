package app.tofairy.child.sensing

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import app.tofairy.child.core.EphemeralSignal

/**
 * 센싱 입력 (CLAUDE.md §4) — 포그라운드 앱·전환 이벤트 수신.
 *
 * 불변식:
 *  - 라이브 이벤트 원문은 [EphemeralSignal] 로도 수집하지 않는다. 일일 A축은 별도 sampler가
 *    선택한 screenshot+최소 metadata만 암호화 임시 저장한다.
 *  - #4: 케이스 B 요정 모드 세션 밖에서는 파이프라인이 시작조차 안 된다.
 *  - #6: 동의 미확인 시 센싱 비활성(시작 게이트).
 * 이 서비스는 신호를 만들기만 하고, [SensingGate] 가 닫혀 있으면 즉시 폐기하고 흘리지 않는다.
 */
class SensingAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        SensingGate.onServiceConnected()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        // 게이트가 닫혀 있으면(세션 밖 또는 동의 미확인) 신호를 만들지도, 흘리지도 않는다.
        if (!SensingGate.isOpen) return

        val kind = when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ->
                EphemeralSignal.SignalKind.WINDOW_STATE_CHANGED
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ->
                EphemeralSignal.SignalKind.WINDOW_CONTENT_CHANGED
            else -> return
        }

        val signal = EphemeralSignal(
            packageName = event.packageName?.toString().orEmpty(),
            kind = kind,
            // A축은 node text 기반 실시간 판정이 아니다. 이벤트는 checkpoint 후보 신호로만 쓴다.
            rawText = null,
            atElapsedMillis = SystemClock.elapsedRealtime(),
        )

        // 싱크가 신호를 소비하고 폐기할 책임을 진다(core.consume). 싱크가 없으면 즉시 폐기.
        val sink = SensingGate.sink
        if (sink == null) {
            signal.discard()
        } else {
            sink.onSignal(signal)
        }
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        SensingGate.onServiceDisconnected()
        return super.onUnbind(intent)
    }
}
