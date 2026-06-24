package app.tofairy.child.responsebank

import android.content.Context
import app.tofairy.child.core.FairyIntent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 응답뱅크 (CLAUDE.md §7) — intent → 대사 선택의 유일한 출처.
 *
 * 불변식 #3: 아이가 듣는 문장은 전량 여기서 선택된다. 모델 자유 텍스트가 [FairyLine] 을 만들 수 없다.
 * 자산은 앱 번들에 내장(`assets/responsebank/dialogue.json`)되며 OTA 없음.
 */
class ResponseBank private constructor(
    private val asset: DialogueAsset,
) {
    /**
     * [intent] 에 대응하는 한 줄의 child-facing 대사를 선택한다.
     * 슬롯(enum) 별 후보 중 [pick] 전략으로 하나를 고른다. 후보가 없으면 null(요정은 침묵).
     */
    fun select(
        intent: FairyIntent,
        pick: (List<FairyLine>) -> FairyLine? = { it.randomOrNull() },
    ): FairyLine? {
        val slot = slotKey(intent) ?: return null
        val candidates = asset.lines[intent.id]?.get(slot).orEmpty()
        return pick(candidates)
    }

    /** intent 의 enum 슬롯을 자산 JSON 의 슬롯 키 문자열로 매핑한다. */
    private fun slotKey(intent: FairyIntent): String? = when (intent) {
        is FairyIntent.SuggestBreak -> intent.reason.name
        is FairyIntent.PromiseCheckIn -> intent.state.name
        is FairyIntent.GentleContentPrompt -> intent.severity.name
        is FairyIntent.Greeting -> intent.moment.name
        is FairyIntent.Encourage -> intent.occasion.name
        is FairyIntent.AwakeningStep -> intent.step.name
        FairyIntent.Idle -> null
    }

    companion object {
        private const val ASSET_PATH = "responsebank/dialogue.json"
        private val json = Json { ignoreUnknownKeys = true }

        /** 번들 자산에서 응답뱅크를 로드한다. 앱 시작 시 1회. */
        fun load(context: Context): ResponseBank {
            val raw = context.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
            return ResponseBank(json.decodeFromString(DialogueAsset.serializer(), raw))
        }

        /** 테스트/프리뷰용 인메모리 생성. */
        fun fromAsset(asset: DialogueAsset): ResponseBank = ResponseBank(asset)
    }
}

/** 선택된 한 줄. 아이에게 노출되는 텍스트와 재생할 오디오 클립 키. */
@Serializable
data class FairyLine(
    val id: String,
    val text: String,
    val audioClip: String? = null,
)

/** `dialogue.json` 의 직렬화 스키마. intentId → slotKey → 후보 라인 목록. */
@Serializable
data class DialogueAsset(
    @SerialName("schemaVersion") val schemaVersion: String = "0",
    val locale: String = "ko",
    val lines: Map<String, Map<String, List<FairyLine>>> = emptyMap(),
)
