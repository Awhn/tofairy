package app.tofairy.child.screening.axisb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UsagePatternEngineTest {

    private val engine = UsagePatternEngine()

    @Test
    fun continuousUse_overThreshold_emitsSignal() {
        val signals = engine.evaluate(
            UsagePatternEngine.UsageSnapshot(
                continuousForegroundMinutes = 41,
                localHourOfDay = 15,
                appSwitchesLast5Min = 0,
            ),
        )
        assertTrue(UsagePatternSignal.CONTINUOUS_USE in signals)
        assertTrue(UsagePatternSignal.LATE_HOUR !in signals)
    }

    @Test
    fun lateHour_wrapsAroundMidnight() {
        val night = engine.evaluate(
            UsagePatternEngine.UsageSnapshot(0, localHourOfDay = 23, appSwitchesLast5Min = 0),
        )
        val earlyMorning = engine.evaluate(
            UsagePatternEngine.UsageSnapshot(0, localHourOfDay = 3, appSwitchesLast5Min = 0),
        )
        assertTrue(UsagePatternSignal.LATE_HOUR in night)
        assertTrue(UsagePatternSignal.LATE_HOUR in earlyMorning)
    }

    @Test
    fun calmMidday_emitsNothing() {
        val signals = engine.evaluate(
            UsagePatternEngine.UsageSnapshot(10, localHourOfDay = 14, appSwitchesLast5Min = 2),
        )
        assertEquals(emptySet<UsagePatternSignal>(), signals)
    }

    @Test
    fun rapidSwitching_emitsSignal() {
        val signals = engine.evaluate(
            UsagePatternEngine.UsageSnapshot(5, localHourOfDay = 14, appSwitchesLast5Min = 15),
        )
        assertTrue(UsagePatternSignal.RAPID_APP_SWITCHING in signals)
    }
}
