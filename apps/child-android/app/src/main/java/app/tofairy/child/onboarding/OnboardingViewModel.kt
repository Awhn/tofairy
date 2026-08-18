package app.tofairy.child.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tofairy.child.apiclient.ConsentSynchronizer
import app.tofairy.child.core.FairyIntent
import app.tofairy.child.fairy.FairyMood
import app.tofairy.child.localstore.RelationshipStore
import app.tofairy.child.responsebank.AudioPlayer
import app.tofairy.child.responsebank.FairyLine
import app.tofairy.child.responsebank.ResponseBank
import app.tofairy.child.sensing.ConsentState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 온보딩 각성 의식 (CLAUDE.md §3, §12).
 *
 * 흐름: ARRIVAL(각성) → NAMING(이름짓기) → FIRST_BOND(유대) → CONSENT(동의 게이트, #6) → DONE.
 *
 * 불변식:
 *  - #3: 각 단계 대사는 더미 intent([FairyIntent.AwakeningStep])로 responsebank 에서만 선택한다.
 *  - #6: 로컬 버튼을 동의로 취급하지 않는다. 서버가 [ConsentState.CONFIRMED]를 반환해야 완료된다.
 */
class OnboardingViewModel(
    private val responseBank: ResponseBank,
    private val audioPlayer: AudioPlayer,
    private val store: RelationshipStore,
    private val consentSynchronizer: ConsentSynchronizer,
) : ViewModel() {

    data class UiState(
        val step: Step = Step.ARRIVAL,
        val mood: FairyMood = FairyMood.AWAKENING,
        val line: FairyLine? = null,
        val fairyName: String = "",
        val consentState: ConsentState = ConsentState.UNKNOWN,
        val checkingConsent: Boolean = false,
        val consentMessage: String? = null,
        val finished: Boolean = false,
    )

    enum class Step { ARRIVAL, NAMING, FIRST_BOND, CONSENT, DONE }

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    init {
        speakFor(FairyIntent.Awakening.ARRIVAL, FairyMood.AWAKENING)
    }

    /** ARRIVAL 화면에서 다음으로. */
    fun onArrivalContinue() {
        _ui.value = _ui.value.copy(step = Step.NAMING)
        speakFor(FairyIntent.Awakening.NAMING, FairyMood.AWAKENING)
    }

    fun onNameChanged(name: String) {
        _ui.value = _ui.value.copy(fairyName = name)
    }

    /** 이름을 메모리 UI 상태에서 확정하고 유대 단계로. 동의 확인 전에는 영속화하지 않는다. */
    fun onNameConfirmed() {
        val name = _ui.value.fairyName.trim()
        if (name.isEmpty()) return
        _ui.value = _ui.value.copy(fairyName = name)
        _ui.value = _ui.value.copy(step = Step.FIRST_BOND)
        speakFor(FairyIntent.Awakening.FIRST_BOND, FairyMood.HAPPY)
    }

    fun onBondContinue() {
        _ui.value = _ui.value.copy(step = Step.CONSENT)
    }

    /**
     * 보호자가 별도 채널에서 완료한 동의를 서버에서 다시 확인한다.
     * 이 화면의 클릭 자체로 CONFIRMED를 만들지 않으며 조회 실패/UNKNOWN/REVOKED는 fail-closed다.
     */
    fun onCheckParentConsent() {
        if (_ui.value.checkingConsent) return
        _ui.value = _ui.value.copy(checkingConsent = true, consentMessage = null)
        viewModelScope.launch {
            val status = consentSynchronizer.refreshAtStartup().getOrNull()
            if (status?.state == ConsentState.CONFIRMED) {
                val confirmedFairyName = _ui.value.fairyName
                store.update {
                    it.copy(
                        fairyName = confirmedFairyName,
                        awakened = true,
                        bondLevel = it.bondLevel + 1,
                    )
                }
                _ui.value = _ui.value.copy(
                    consentState = ConsentState.CONFIRMED,
                    checkingConsent = false,
                    step = Step.DONE,
                    finished = true,
                )
            } else {
                _ui.value = _ui.value.copy(
                    consentState = status?.state ?: ConsentState.UNKNOWN,
                    checkingConsent = false,
                    consentMessage = "보호자 동의가 아직 확인되지 않았어요. 확인 전에는 센싱을 시작하지 않습니다.",
                )
            }
        }
    }

    /** 동의를 만들거나 onboarding을 완료하지 않고 현재 fail-closed 상태에 머문다. */
    fun onDeferConsent() {
        _ui.value = _ui.value.copy(
            consentMessage = "나중에 보호자 동의를 확인할 수 있어요. 그전에는 센싱하지 않습니다.",
        )
    }

    private fun speakFor(awakening: FairyIntent.Awakening, mood: FairyMood) {
        val line = responseBank.select(FairyIntent.AwakeningStep(awakening))
        _ui.value = _ui.value.copy(line = line, mood = mood)
        audioPlayer.play(line?.audioClip)
    }

    override fun onCleared() {
        audioPlayer.stop()
        super.onCleared()
    }
}
