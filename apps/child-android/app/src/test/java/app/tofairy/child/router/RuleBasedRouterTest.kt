package app.tofairy.child.router

import app.tofairy.child.core.FairyIntent
import app.tofairy.child.screening.axisa.AgeAppropriateness
import app.tofairy.child.screening.axisb.UsagePatternSignal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleBasedRouterTest {

    private val router = RuleBasedRouter()

    @Test
    fun aboveAgeContent_takesPriority_overUsageSignals() {
        val intent = router.route(
            RouterContext(
                dayMoment = RouterContext.DayMoment.AFTERNOON,
                usageSignals = listOf(UsagePatternSignal.CONTINUOUS_USE),
                appropriateness = AgeAppropriateness.ABOVE_AGE_NOTABLE,
            ),
        )
        assertTrue(intent is FairyIntent.GentleContentPrompt)
        assertEquals(
            FairyIntent.ContentSeverity.NOTABLE,
            (intent as FairyIntent.GentleContentPrompt).severity,
        )
    }

    @Test
    fun lateHour_winsOverContinuousUse_forBreak() {
        val intent = router.route(
            RouterContext(
                dayMoment = RouterContext.DayMoment.LATE_NIGHT,
                usageSignals = listOf(
                    UsagePatternSignal.CONTINUOUS_USE,
                    UsagePatternSignal.LATE_HOUR,
                ),
            ),
        )
        assertEquals(FairyIntent.BreakReason.LATE_HOUR, (intent as FairyIntent.SuggestBreak).reason)
    }

    @Test
    fun promiseAtLimit_producesCheckIn() {
        val intent = router.route(
            RouterContext(dayMoment = RouterContext.DayMoment.AFTERNOON, promiseProgress = 1.0f),
        )
        assertEquals(
            FairyIntent.PromiseState.AT_LIMIT,
            (intent as FairyIntent.PromiseCheckIn).state,
        )
    }

    @Test
    fun debounce_sameIntent_returnsIdle() {
        val ctx = RouterContext(
            dayMoment = RouterContext.DayMoment.AFTERNOON,
            usageSignals = listOf(UsagePatternSignal.CONTINUOUS_USE),
            lastIntentId = "suggest_break",
        )
        assertEquals(FairyIntent.Idle, router.route(ctx))
    }

    @Test
    fun quietRecentInteraction_staysIdle() {
        val intent = router.route(
            RouterContext(
                dayMoment = RouterContext.DayMoment.MORNING,
                minutesSinceLastInteraction = 5,
            ),
        )
        assertEquals(FairyIntent.Idle, intent)
    }

    @Test
    fun morningWithNoSignals_andQuietElapsed_greets() {
        val intent = router.route(
            RouterContext(
                dayMoment = RouterContext.DayMoment.MORNING,
                minutesSinceLastInteraction = 999,
            ),
        )
        assertEquals(FairyIntent.DayMoment.MORNING, (intent as FairyIntent.Greeting).moment)
    }
}
