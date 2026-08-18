package app.tofairy.child.sensing

/**
 * 자녀 기기가 알고 있는 부모 동의 상태.
 *
 * [UNKNOWN]은 동의가 아님을 명시한다. 앱 재기동 시에는 최신 서버 상태를 확인할 때까지
 * UNKNOWN에서 시작해 센싱을 fail-closed 한다. TTL/signed lease는 현재 계약에 포함하지 않는다.
 */
enum class ConsentState {
    UNKNOWN,
    CONFIRMED,
    REVOKED,
}
