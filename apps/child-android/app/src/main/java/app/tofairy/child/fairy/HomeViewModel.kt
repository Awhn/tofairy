package app.tofairy.child.fairy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tofairy.child.core.FairyIntent
import app.tofairy.child.localstore.RelationshipStore
import app.tofairy.child.responsebank.AudioPlayer
import app.tofairy.child.responsebank.FairyLine
import app.tofairy.child.responsebank.ResponseBank
import app.tofairy.child.router.Router
import app.tofairy.child.router.RouterContext
import app.tofairy.child.screening.axisa.AgeSuitability
import app.tofairy.child.screening.axisb.UsagePatternSignal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * 홈(요정 데모 화면) 상태 (CLAUDE.md §12).
 *
 * 데모 흐름(모델 없이): 상황 컨텍스트 → [Router] (규칙) → [FairyIntent] → [ResponseBank] 선택 → 요정이 '말함'.
 * 불변식 #3: 화면에 뜨는 문장은 전량 responsebank 에서 온다. 라우터는 intent 만 만든다.
 */
class HomeViewModel(
    private val router: Router,
    private val responseBank: ResponseBank,
    private val audioPlayer: AudioPlayer,
    private val store: RelationshipStore,
) : ViewModel() {

    data class UiState(
        val fairyName: String = "요정",
        val mood: FairyMood = FairyMood.CALM,
        val line: FairyLine? = null,
        val lastIntentId: String? = null,
    )

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            store.state.collect { s ->
                _ui.value = _ui.value.copy(fairyName = s.fairyName ?: "요정")
            }
        }
        // 진입 시 가벼운 인사.
        runRouter(baseContext())
    }

    /** 요정을 톡 건드림 → 충분히 시간이 흘렀다고 보고(침묵간격 충족) 라우터가 가벼운 인사를 고르게 한다. */
    fun onFairyTapped() {
        runRouter(baseContext(minutesSinceLastInteraction = 999))
    }

    /** 데모: B축 신호 주입(연속 사용) → 휴식 권유 intent 유도. */
    fun simulateContinuousUse() {
        runRouter(baseContext().copy(usageSignals = listOf(UsagePatternSignal.CONTINUOUS_USE)))
    }

    /** 데모: 일일 A축 집계에서 현재 연령 경계 초과 → 부드러운 묻기 intent 유도. */
    fun simulateAboveAgeContent() {
        runRouter(baseContext().copy(ageSuitability = AgeSuitability.EXCEEDS_AGE_THRESHOLD))
    }

    /** 데모: 약속 달성 → 격려 intent. */
    fun simulatePromiseKept() {
        viewModelScope.launch { store.update { it.copy(promiseStreakDays = it.promiseStreakDays + 1) } }
        present(FairyIntent.Encourage(FairyIntent.EncourageOccasion.PROMISE_KEPT))
    }

    private fun runRouter(context: RouterContext) {
        val intent = router.route(context)
        present(intent)
    }

    private fun present(intent: FairyIntent) {
        if (intent == FairyIntent.Idle) {
            _ui.value = _ui.value.copy(mood = FairyMood.CALM, lastIntentId = intent.id)
            return
        }
        val line = responseBank.select(intent)
        _ui.value = _ui.value.copy(
            mood = intent.toMood(),
            line = line,
            lastIntentId = intent.id,
        )
        audioPlayer.play(line?.audioClip)
        viewModelScope.launch {
            store.update { it.copy(lastInteractionAt = System.currentTimeMillis()) }
        }
    }

    private fun baseContext(minutesSinceLastInteraction: Int = 999): RouterContext =
        RouterContext(
            dayMoment = currentMoment(),
            lastIntentId = _ui.value.lastIntentId,
            minutesSinceLastInteraction = minutesSinceLastInteraction,
        )

    private fun currentMoment(): RouterContext.DayMoment {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 5..11 -> RouterContext.DayMoment.MORNING
            in 12..17 -> RouterContext.DayMoment.AFTERNOON
            in 18..20 -> RouterContext.DayMoment.EVENING
            else -> RouterContext.DayMoment.LATE_NIGHT
        }
    }

    override fun onCleared() {
        audioPlayer.stop()
        super.onCleared()
    }
}
