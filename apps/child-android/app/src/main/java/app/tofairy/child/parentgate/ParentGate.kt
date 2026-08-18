package app.tofairy.child.parentgate

import app.tofairy.child.digest.ContextDigest
import java.util.concurrent.atomic.AtomicLong

enum class ParentAuthState {
    LOCKED,
    AUTHENTICATED,
}

/**
 * 부모 PIN의 안전한 로컬 검증 계약.
 *
 * 구현은 PIN 평문이나 되돌릴 수 있는 값을 저장/반환하지 않고, salt를 둔 느린 verifier를 Android
 * Keystore로 보호되는 저장소에 보관해야 한다. 이 인터페이스는 저장 값을 읽는 API를 의도적으로
 * 제공하지 않는다. biometric은 이 계약을 대체할 수 있는 향후 Open Issue이며 현재 구현하지 않는다.
 */
fun interface ParentPinCredentialStore {
    /** 전달된 배열은 호출 동안만 유효하며 [ParentGate]가 반환 전에 0으로 덮어쓴다. */
    fun verify(pin: CharArray): Boolean
}

/**
 * Local Data Gate가 읽을 수 있는 유일한 부모용 projection source.
 * EphemeralSignal, screenshot, screening/dimension state, RelationshipState 전체 또는 child raw store를
 * 반환하는 일반화된 타입 파라미터를 두지 않는다.
 */
fun interface ParentDigestSource {
    fun readDigests(): List<ContextDigest>
}

sealed interface ParentAuthenticationResult {
    data class Authenticated(val session: ParentModeSession) : ParentAuthenticationResult
    data object Rejected : ParentAuthenticationResult
}

sealed interface ParentDataAccess {
    data class Granted(val digests: List<ContextDigest>) : ParentDataAccess
    data object Denied : ParentDataAccess
}

/**
 * PIN 인증 후에만 얻을 수 있는 부모 모드 capability. child mode → parent mode 단순 전환만으로는
 * 이 객체를 얻을 수 없다. [close] 또는 [ParentGate.lock] 이후 기존 capability는 즉시 무효화된다.
 */
class ParentModeSession internal constructor(
    private val token: Long,
    private val gate: ParentGate,
) : AutoCloseable {
    val isActive: Boolean
        get() = gate.isSessionActive(token)

    fun readDigests(): ParentDataAccess = gate.readDigests(token)

    override fun close() {
        gate.lock(token)
    }
}

/**
 * 공유 기기의 Local Data Gate + Parent Authentication Gate.
 *
 * 부모 UI에는 이 gate가 발급한 [ParentModeSession]만 전달한다. 자녀 raw 저장소 또는 screening
 * 중간 상태는 생성자와 반환 타입 어디에도 노출하지 않는다.
 */
class ParentGate(
    private val credentialStore: ParentPinCredentialStore,
    digestSource: ParentDigestSource,
) {
    private val lock = Any()
    private val localDataGate = LocalDataGate(digestSource)
    private var activeToken: Long? = null

    @Volatile
    var authState: ParentAuthState = ParentAuthState.LOCKED
        private set

    /**
     * PIN이 맞을 때만 부모 모드 capability를 발급한다. 입력 배열과 내부 사본 모두 검증 직후 지워
     * 불필요한 PIN 수명을 줄인다. 실패하면 기존 부모 모드 세션도 잠근 상태로 둔다.
     */
    fun authenticate(pin: CharArray): ParentAuthenticationResult {
        val candidate = pin.copyOf()
        pin.fill(CLEARED_CHAR)

        return try {
            synchronized(lock) {
                activeToken = null
                authState = ParentAuthState.LOCKED

                if (!credentialStore.verify(candidate)) {
                    ParentAuthenticationResult.Rejected
                } else {
                    val token = nextToken.incrementAndGet()
                    activeToken = token
                    authState = ParentAuthState.AUTHENTICATED
                    ParentAuthenticationResult.Authenticated(ParentModeSession(token, this))
                }
            }
        } finally {
            candidate.fill(CLEARED_CHAR)
        }
    }

    /** child mode 진입, 화면 잠금 또는 부모 모드 종료 시 호출한다. */
    fun lock() {
        synchronized(lock) {
            activeToken = null
            authState = ParentAuthState.LOCKED
        }
    }

    internal fun lock(token: Long) {
        synchronized(lock) {
            if (activeToken == token) {
                activeToken = null
                authState = ParentAuthState.LOCKED
            }
        }
    }

    internal fun isSessionActive(token: Long): Boolean = synchronized(lock) {
        authState == ParentAuthState.AUTHENTICATED && activeToken == token
    }

    internal fun readDigests(token: Long): ParentDataAccess = synchronized(lock) {
        if (authState != ParentAuthState.AUTHENTICATED || activeToken != token) {
            return@synchronized ParentDataAccess.Denied
        }
        ParentDataAccess.Granted(localDataGate.readDigests())
    }

    private companion object {
        const val CLEARED_CHAR: Char = '\u0000'
        val nextToken = AtomicLong(0)
    }
}

/** child local-store를 직접 노출하지 않고 허용된 digest projection만 복사해 건넨다. */
private class LocalDataGate(
    private val digestSource: ParentDigestSource,
) {
    fun readDigests(): List<ContextDigest> = digestSource.readDigests().toList()
}
