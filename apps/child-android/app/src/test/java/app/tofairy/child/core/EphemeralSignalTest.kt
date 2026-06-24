package app.tofairy.child.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 불변식 #1/#2: ephemeral 신호는 폐기 후 원문 접근이 막히고, toString 으로도 새지 않는다. */
class EphemeralSignalTest {

    @Test
    fun discard_clearsRawTextAndBlocksAccess() {
        val signal = EphemeralSignal(
            packageName = "com.example",
            kind = EphemeralSignal.SignalKind.WINDOW_CONTENT_CHANGED,
            rawText = "secret content",
            atElapsedMillis = 0L,
        )
        assertEquals("secret content", signal.rawText)

        signal.discard()
        assertTrue(signal.isDiscarded)

        val ex = runCatching { signal.rawText }.exceptionOrNull()
        assertTrue(ex is IllegalStateException)
    }

    @Test
    fun toString_doesNotLeakRawText() {
        val signal = EphemeralSignal(
            packageName = "com.example",
            kind = EphemeralSignal.SignalKind.WINDOW_STATE_CHANGED,
            rawText = "TOP_SECRET_TEXT",
            atElapsedMillis = 0L,
        )
        assertFalse(signal.toString().contains("TOP_SECRET_TEXT"))
    }

    @Test
    fun consume_alwaysDiscards() {
        val signal = EphemeralSignal("p", EphemeralSignal.SignalKind.WINDOW_STATE_CHANGED, "x", 0L)
        val result = signal.consume { it.packageName }
        assertEquals("p", result)
        assertTrue(signal.isDiscarded)
    }

    @Test
    fun ephemeralContent_discardClearsText() {
        val content = EphemeralContent(text = "screen text", frame = null)
        assertEquals("screen text", content.text)
        content.discard()
        assertTrue(content.isDiscarded)
        assertTrue(runCatching { content.text }.exceptionOrNull() is IllegalStateException)
    }
}
