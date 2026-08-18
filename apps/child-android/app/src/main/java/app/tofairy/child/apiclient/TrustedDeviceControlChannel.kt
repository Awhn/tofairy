package app.tofairy.child.apiclient

/** trusted parent device set이 바뀌었음을 알리는 내용 비포함 control signal. */
data class TrustedDeviceSetChanged(
    val pairingId: String,
    val revision: Long,
) {
    init {
        require(pairingId.isNotBlank()) { "pairingId must not be blank" }
        require(revision >= 0) { "revision must not be negative" }
    }
}

fun interface TrustedDeviceControlListener {
    fun onTrustedDeviceSetChanged(event: TrustedDeviceSetChanged)
}

fun interface TrustedDeviceControlSubscription {
    fun cancel()
}

/**
 * recipient set 변경 wake-up 계약. 이벤트 자체의 key를 바로 신뢰하지 않고
 * [ApiClient.fetchTrustedParentDeviceKeys]로 해당 revision 이상의 인증된 set을 다시 조회한다.
 */
fun interface TrustedDeviceControlChannel {
    fun subscribe(listener: TrustedDeviceControlListener): TrustedDeviceControlSubscription
}
