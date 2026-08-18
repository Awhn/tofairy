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
 * 실제 push/relay transport가 배선되기 전의 fail-closed 골격.
 *
 * 이벤트를 만들어 내지 않으며, 앱 시작 시 [ConsentSynchronizer.refreshAtStartup]의 서버 조회만
 * 수행된다. production에서 이 구현을 그대로 사용하면 실행 중 철회 역전파를 제공할 수 없으므로
 * 완료 구현으로 간주하지 않는다.
 */
object NoopConsentControlChannel : ConsentControlChannel {
    override fun subscribe(listener: ConsentControlListener): ConsentControlSubscription =
        ConsentControlSubscription { }
}

/**
 * 시작 시 서버 상태 확인과 실행 중 control event를 하나의 명시적 동의 상태로 합친다.
 * 최신 server revision이 우선하며 동일 revision에서는 REVOKED가 다른 상태보다 우선한다.
 */
class ConsentSynchronizer(
    private val apiClient: ApiClient,
    private val controlChannel: ConsentControlChannel,
    private val applyState: (ConsentState) -> Unit = SensingGate::applyAuthoritativeConsentState,
) : AutoCloseable {
    private val lock = Any()
    private var subscription: ConsentControlSubscription? = null
    private var latestUpdate: ConsentUpdate? = null
    /** 시작 또는 마지막 연결 단절 뒤 authoritative 응답/event를 하나라도 받았는지. */
    private var hasFreshConnectionState: Boolean = false

    /**
     * 앱 재기동 시 authoritative control update가 아직 없다면 UNKNOWN으로 닫고 최신 consent를
     * 조회한다. 네트워크 실패 시 UNKNOWN을 유지해 설치 상태나 과거 값만으로 센싱을 시작하지 않는다.
     */
    suspend fun refreshAtStartup(): Result<ConsentStatus> {
        synchronized(lock) {
            // 구독/재연결 직후 이미 도착한 authoritative event를 UNKNOWN으로 되돌리지 않는다.
            if (!hasFreshConnectionState) applyState(ConsentState.UNKNOWN)
        }
        return apiClient.consentStatus().mapCatching { status ->
            val effective = checkNotNull(
                applyIfCurrent(
                    ConsentUpdate(
                        state = status.state,
                        revision = status.revision,
                        updatedAt = status.updatedAt,
                    ),
                ),
            ) { "consent response is stale or conflicts at the same revision" }
            // API 응답보다 최신 control event가 이미 있으면 오래된 응답이 UI/다른 소비자를
            // 통과시키지 않도록 effective snapshot을 반환한다.
            ConsentStatus(
                state = effective.state,
                revision = effective.revision,
                updatedAt = effective.updatedAt,
            )
        }
    }

    /** relay/network 재연결 시에도 cold-start와 같은 authoritative refresh를 수행한다. */
    suspend fun refreshAfterReconnect(): Result<ConsentStatus> = refreshAtStartup()

    /**
     * control/relay 연결이 끊기는 즉시 이전 CONFIRMED를 새 연결의 근거로 재사용하지 않는다.
     * transport는 reconnect를 시작하기 전에 이 메서드를 호출하고, 성공 뒤 [refreshAfterReconnect]를
     * 호출하거나 authenticated control event를 전달해야 한다.
     */
    fun onControlChannelDisconnected() {
        synchronized(lock) {
            hasFreshConnectionState = false
            applyState(ConsentState.UNKNOWN)
        }
    }

    /** active relay의 consent 변경을 구독한다. REVOKED 수신은 활성 세션 중에도 즉시 gate를 닫는다. */
    fun connectControlChannel() {
        synchronized(lock) {
            if (subscription != null) return
            subscription = controlChannel.subscribe(ConsentControlListener { update ->
                applyIfCurrent(update)
            })
        }
    }

    private fun applyIfCurrent(update: ConsentUpdate): ConsentUpdate? =
        synchronized(lock) {
            val previous = latestUpdate
            val isNewer = previous == null || update.revision > previous.revision
            val revocationWinsTie =
                previous != null &&
                    update.revision == previous.revision &&
                    update.state == ConsentState.REVOKED &&
                    previous.state != ConsentState.REVOKED

            val repeatsEffectiveState =
                previous != null &&
                    update.revision == previous.revision &&
                    update.state == previous.state
            val previousRevocationWinsTie =
                previous != null &&
                    update.revision == previous.revision &&
                    previous.state == ConsentState.REVOKED

            val effective = when {
                isNewer || revocationWinsTie -> update
                repeatsEffectiveState || previousRevocationWinsTie -> previous
                // 이미 현재 연결에서 authoritative state를 받은 뒤 도착한 stale event는 무시한다.
                hasFreshConnectionState && previous != null && update.revision < previous.revision -> previous
                else -> null
            }
            if (effective != null) {
                latestUpdate = effective
                // disconnect가 UNKNOWN으로 닫은 뒤 동일 revision을 다시 받은 경우에도 재적용한다.
                applyState(effective.state)
                hasFreshConnectionState = true
            }
            effective
        }

    override fun close() {
        synchronized(lock) {
            subscription?.cancel()
            subscription = null
            hasFreshConnectionState = false
            applyState(ConsentState.UNKNOWN)
        }
    }
}
