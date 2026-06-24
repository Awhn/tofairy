package app.tofairy.child.session

import app.tofairy.child.sensing.SensingGate
import app.tofairy.child.sensing.SignalSink

/**
 * 케이스 B 요정 모드 세션 경계 (CLAUDE.md §3 모듈, 불변식 #4).
 *
 * 세션이 살아 있는 동안에만 센싱 파이프라인이 동작한다. 세션 밖에서는 시작조차 안 된다.
 * 세션 시작/종료가 [SensingGate] 의 세션 플래그를 제어하는 유일한 지점이다.
 */
class FairySession private constructor(
    private val sink: SignalSink,
) {
    @Volatile
    var isActive: Boolean = false
        private set

    fun start() {
        if (isActive) return
        SensingGate.openSession(sink)
        isActive = true
    }

    fun end() {
        if (!isActive) return
        SensingGate.closeSession()
        isActive = false
    }

    companion object {
        /**
         * 세션을 생성한다. 동의 미확인이면 게이트가 닫혀 있으므로 start 해도 신호는 흐르지 않는다(#6).
         */
        fun create(sink: SignalSink): FairySession = FairySession(sink)
    }
}
