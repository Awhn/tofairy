package app.tofairy.child.session

import app.tofairy.child.sensing.SensingGate
import app.tofairy.child.sensing.SignalSink

/**
 * 자녀 전용폰(케이스 A)과 부모/자녀 공유폰(케이스 B)에 공통인 명시적 센싱 경계.
 *
 * 전용 기기 세션은 장시간 유지할 수 있지만 설치 또는 AccessibilityService 연결만으로 자동
 * 시작하지 않는다. 공유 기기에서는 이 경계가 부모 사용을 센싱 파이프라인에서 제외한다.
 * 세션 시작/종료가 [SensingGate] 의 세션 플래그를 제어하는 유일한 지점이다.
 */
class FairySession private constructor(
    val identity: FairySessionIdentity,
    private val sink: SignalSink,
) {
    @Volatile
    var state: FairySessionState = FairySessionState.INACTIVE
        private set

    val isActive: Boolean
        get() = state == FairySessionState.ACTIVE

    @Synchronized
    fun start() {
        if (state == FairySessionState.ACTIVE) return
        SensingGate.openSession(identity, sink)
        state = FairySessionState.ACTIVE
    }

    @Synchronized
    fun end() {
        if (state == FairySessionState.INACTIVE) return
        SensingGate.closeSession(identity)
        state = FairySessionState.INACTIVE
    }

    companion object {
        /**
         * identity가 없는 암묵적 세션 생성 경로는 제공하지 않는다. 동의 미확인이면 start 후에도
         * 게이트가 닫혀 있으며, 두 기기 모드 모두 동일한 세션 조건을 거친다.
         */
        fun create(identity: FairySessionIdentity, sink: SignalSink): FairySession =
            FairySession(identity, sink)
    }
}

/** 명시 세션이 적용되는 제품 사용 형태. 어느 값도 세션 요건을 우회하지 않는다. */
enum class FairyDeviceMode {
    DEDICATED_CHILD_DEVICE,
    SHARED_PARENT_CHILD_DEVICE,
}

/** 세션 인스턴스를 식별하고 전용폰/공유폰 경계를 함께 기록한다. */
data class FairySessionIdentity(
    val sessionId: String,
    val deviceMode: FairyDeviceMode,
) {
    init {
        require(sessionId.isNotBlank()) { "sessionId must not be blank" }
    }
}

enum class FairySessionState {
    INACTIVE,
    ACTIVE,
}
