package app.tofairy.child.apiclient

import app.tofairy.child.sensing.ConsentState
import app.tofairy.child.sensing.SensingGate

/** 서버의 최신 동의 상태 또는 active control relay가 전달한 상태 변경. */
data class ConsentUpdate(
    val state: ConsentState,
    /** 서버가 발급한 단조 증가 ordering 값. 기기 timestamp로 순서를 결정하지 않는다. */
    val revision: Long,
    val updatedAt: Long,
) {
    init {
        require(revision >= 0) { "revision must not be negative" }
        require(updatedAt >= 0) { "updatedAt must not be negative" }
    }
}

fun interface ConsentControlListener {
    fun onConsentUpdate(update: ConsentUpdate)
}

fun interface ConsentControlSubscription {
    fun cancel()
}

/**
 * 부모 동의 철회를 자녀 기기로 가능한 즉시 전달하는 control channel 계약.
 *
 * WebSocket, push + reconnect 등 최종 transport는 아직 정하지 않는다. transport는 digest 본문을
 * 싣지 않는다. 완전히 offline인 기기에는 즉시 철회를 전달할 수 없으므로, 재연결 control event와
 * 앱 재기동 시 [ApiClient.consentStatus] 조회가 함께 필요하다. TTL/signed lease는 필수 계약이 아니다.
 */
fun interface ConsentControlChannel {
    fun subscribe(listener: ConsentControlListener): ConsentControlSubscription
}

/**
 * 시작 시 서버 상태 확인과 실행 중 control event를 하나의 명시적 동의 상태로 합친다.
 * 최신 server revision이 우선하며 동일 revision에서는 REVOKED가 다른 상태보다 우선한다.
 */
class ConsentSynchronizer(
    private val apiClient: ApiClient,
    private val controlChannel: ConsentControlChannel,
    private val applyState: (ConsentState) -> Unit = SensingGate::setConsentState,
) : AutoCloseable {
    private val lock = Any()
    private var subscription: ConsentControlSubscription? = null
    private var latestUpdate: ConsentUpdate? = null

    /**
     * 앱 재기동 시 먼저 UNKNOWN으로 닫고 최신 consent를 조회한다. 네트워크 실패 시 UNKNOWN을
     * 유지하므로 설치 상태나 과거 메모리 값만으로 센싱을 시작하지 않는다.
     */
    suspend fun refreshAtStartup(): Result<ConsentStatus> {
        applyState(ConsentState.UNKNOWN)
        return apiClient.consentStatus().onSuccess { status ->
            applyIfCurrent(
                ConsentUpdate(
                    state = status.state,
                    revision = status.revision,
                    updatedAt = status.updatedAt,
                ),
            )
        }
    }

    /** active relay의 consent 변경을 구독한다. REVOKED 수신은 활성 세션 중에도 즉시 gate를 닫는다. */
    fun connectControlChannel() {
        synchronized(lock) {
            if (subscription != null) return
            subscription = controlChannel.subscribe(ConsentControlListener(::applyIfCurrent))
        }
    }

    private fun applyIfCurrent(update: ConsentUpdate) {
        synchronized(lock) {
            val previous = latestUpdate
            val isNewer = previous == null || update.revision > previous.revision
            val revocationWinsTie =
                previous != null &&
                    update.revision == previous.revision &&
                    update.state == ConsentState.REVOKED &&
                    previous.state != ConsentState.REVOKED

            if (!isNewer && !revocationWinsTie) return
            latestUpdate = update
            applyState(update.state)
        }
    }

    override fun close() {
        synchronized(lock) {
            subscription?.cancel()
            subscription = null
        }
    }
}
