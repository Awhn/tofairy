package app.tofairy.child.fairy

import app.tofairy.child.core.FairyIntent

/**
 * intent → 시각 무드 매핑. 표현 계층 전용이며 child-facing 문구를 생성하지 않는다(불변식 #3).
 * 아이가 듣는 문장은 responsebank 가, 보는 분위기는 [FairyMood] 가 담당한다.
 */
fun FairyIntent.toMood(): FairyMood = when (this) {
    is FairyIntent.Greeting -> FairyMood.HAPPY
    is FairyIntent.Encourage -> FairyMood.CELEBRATE
    is FairyIntent.SuggestBreak -> FairyMood.GENTLE_ALERT
    is FairyIntent.GentleContentPrompt -> FairyMood.GENTLE_ALERT
    is FairyIntent.PromiseCheckIn -> when (state) {
        FairyIntent.PromiseState.KEPT, FairyIntent.PromiseState.AT_LIMIT -> FairyMood.CELEBRATE
        else -> FairyMood.GENTLE_ALERT
    }
    is FairyIntent.AwakeningStep -> FairyMood.AWAKENING
    FairyIntent.Idle -> FairyMood.CALM
}
