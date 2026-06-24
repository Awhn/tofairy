package app.tofairy.child.responsebank

import app.tofairy.child.core.FairyIntent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ResponseBankTest {

    private val asset = DialogueAsset(
        schemaVersion = "test",
        locale = "ko",
        lines = mapOf(
            "greeting" to mapOf(
                "MORNING" to listOf(FairyLine("g1", "좋은 아침!", "g1")),
            ),
            "suggest_break" to mapOf(
                "CONTINUOUS_USE" to listOf(FairyLine("b1", "잠깐 쉴까?", null)),
            ),
        ),
    )
    private val bank = ResponseBank.fromAsset(asset)

    @Test
    fun select_returnsLineForMatchingSlot() {
        val line = bank.select(FairyIntent.Greeting(FairyIntent.DayMoment.MORNING))
        assertEquals("좋은 아침!", line?.text)
    }

    @Test
    fun select_returnsNull_whenSlotMissing() {
        val line = bank.select(FairyIntent.Greeting(FairyIntent.DayMoment.EVENING))
        assertNull(line)
    }

    @Test
    fun select_idleHasNoLine() {
        assertNull(bank.select(FairyIntent.Idle))
    }

    @Test
    fun select_breakIntentMapsToSlot() {
        val line = bank.select(FairyIntent.SuggestBreak(FairyIntent.BreakReason.CONTINUOUS_USE))
        assertNotNull(line)
        assertEquals("b1", line?.id)
    }
}
