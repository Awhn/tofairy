package app.tofairy.child.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tofairy.child.core.FairyIntent
import app.tofairy.child.fairy.FairyMood
import app.tofairy.child.localstore.RelationshipStore
import app.tofairy.child.responsebank.AudioPlayer
import app.tofairy.child.responsebank.FairyLine
import app.tofairy.child.responsebank.ResponseBank
import app.tofairy.child.sensing.SensingGate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 온보딩 각성 의식 (CLAUDE.md §3 onboarding, §10-1).
 *
 * 흐름: ARRIVAL(각성) → NAMING(이름짓기) → FIRST_BOND(유대) → CONSENT(동의 게이트, #6) → DONE.
 *
 * 불변식:
 *  - #3: 각 단계 대사는 더미 intent([FairyIntent.AwakeningStep])로 responsebank 에서만 선택한다.
 *  - #6: 동의 게이트 통과 전에는 센싱이 활성화되지 않는다([SensingGate.setConsent]).
 */
class OnboardingViewModel(
    private val responseBank: ResponseBank,
    private val audioPlayer: AudioPlayer,
    private val store: RelationshipStore,
) : ViewModel() {

    data class UiState(
        val step: Step = Step.ARRIVAL,
        val mood: FairyMood = FairyMood.AWAKENING,
        val line: FairyLine? = null,
        val fairyName: String = "",
        val consentGranted: Boolean = false,
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

    /** 이름을 확정하고 유대 단계로. */
    fun onNameConfirmed() {
        val name = _ui.value.fairyName.trim()
        if (name.isEmpty()) return
        viewModelScope.launch { store.update { it.copy(fairyName = name) } }
        _ui.value = _ui.value.copy(step = Step.FIRST_BOND)
        speakFor(FairyIntent.Awakening.FIRST_BOND, FairyMood.HAPPY)
    }

    fun onBondContinue() {
        _ui.value = _ui.value.copy(step = Step.CONSENT)
    }

    /**
     * 부모 동의 처리(#6). 실제로는 부모 인증 흐름(별도 화면/PIN)과 연결된다.
     * 동의 시에만 센싱 게이트의 동의 플래그를 연다. 세션(#4)은 별도로 시작되어야 흐른다.
     */
    fun onConsentDecision(granted: Boolean) {
        SensingGate.setConsent(granted)
        viewModelScope.launch {
            store.update { it.copy(awakened = true, bondLevel = it.bondLevel + 1) }
        }
        _ui.value = _ui.value.copy(consentGranted = granted, step = Step.DONE, finished = true)
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
