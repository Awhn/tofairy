package app.tofairy.child.router

import app.tofairy.child.core.FairyIntent
import app.tofairy.child.screening.axisa.AgeSuitability
import app.tofairy.child.screening.axisb.UsagePatternSignal

/**
 * 결정적 규칙 라우터 (CLAUDE.md §10-4 "router(규칙 버전)").
 *
 * 우선순위(높음 → 낮음):
 *  1) A축 부적절 콘텐츠 → 부드러운 한 번 묻기(차단 아님, 불변식 #7)
 *  2) B축 사용패턴 신호 → 휴식 권유
 *  3) 약속 진행 상태 → 체크인
 *  4) 그 외 한가하면 가벼운 인사(과도한 개입 억제)
 *
 * 향후 별도 ML 라우터를 평가할 수 있으나, 본 구현은 저사양 폴백으로 유지된다.
 */
class RuleBasedRouter(
    /** 동일 개입 반복 억제: 직전과 같은 intent 면 침묵으로 디바운스. */
    private val debounceSameIntent: Boolean = true,
    /** 인사 등 선제 상호작용 최소 간격(분). */
    private val minQuietMinutes: Int = 45,
) : Router {

    override fun route(context: RouterContext): FairyIntent {
        val intent = decide(context)
        if (debounceSameIntent && intent.id == context.lastIntentId && intent != FairyIntent.Idle) {
            return FairyIntent.Idle
        }
        return intent
    }

    private fun decide(ctx: RouterContext): FairyIntent {
        // 1) A축
        ctx.ageSuitability?.let { suitability ->
            when (suitability) {
                AgeSuitability.EXCEEDS_AGE_THRESHOLD ->
                    return FairyIntent.GentleContentPrompt
                AgeSuitability.WITHIN_AGE_THRESHOLD -> Unit
            }
        }

        // 2) B축 — 가장 강한 신호 하나를 휴식 권유로 변환
        strongestBreak(ctx.usageSignals)?.let { return it }

        // 3) 약속 진행
        ctx.promiseProgress?.let { p ->
            return when {
                p >= 1.0f -> FairyIntent.PromiseCheckIn(FairyIntent.PromiseState.AT_LIMIT)
                p >= 0.85f -> FairyIntent.PromiseCheckIn(FairyIntent.PromiseState.APPROACHING_LIMIT)
                else -> idleOrGreet(ctx)
            }
        }

        // 4) 기본
        return idleOrGreet(ctx)
    }

    private fun strongestBreak(signals: List<UsagePatternSignal>): FairyIntent? {
        if (signals.isEmpty()) return null
        val reason = when {
            signals.any { it == UsagePatternSignal.LATE_HOUR } ->
                FairyIntent.BreakReason.LATE_HOUR
            signals.any { it == UsagePatternSignal.CONTINUOUS_USE } ->
                FairyIntent.BreakReason.CONTINUOUS_USE
            signals.any { it == UsagePatternSignal.RAPID_APP_SWITCHING } ->
                FairyIntent.BreakReason.RAPID_SWITCHING
            else -> return null
        }
        return FairyIntent.SuggestBreak(reason)
    }

    private fun idleOrGreet(ctx: RouterContext): FairyIntent {
        if (ctx.minutesSinceLastInteraction < minQuietMinutes) return FairyIntent.Idle
        val moment = when (ctx.dayMoment) {
            RouterContext.DayMoment.MORNING -> FairyIntent.DayMoment.MORNING
            RouterContext.DayMoment.AFTERNOON -> FairyIntent.DayMoment.AFTERNOON
            RouterContext.DayMoment.EVENING -> FairyIntent.DayMoment.EVENING
            // 늦은 밤엔 인사 대신 침묵(자극 최소화).
            RouterContext.DayMoment.LATE_NIGHT -> return FairyIntent.Idle
        }
        return FairyIntent.Greeting(moment)
    }
}
