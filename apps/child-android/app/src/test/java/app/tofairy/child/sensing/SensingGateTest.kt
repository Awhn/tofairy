package app.tofairy.child.sensing

import app.tofairy.child.session.FairyDeviceMode
import app.tofairy.child.session.FairySession
import app.tofairy.child.session.FairySessionIdentity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SensingGateTest {

    private val sink = SignalSink { signal -> signal.discard() }

    @Before
    fun setUp() {
        SensingGate.resetForTests()
    }

    @After
    fun tearDown() {
        SensingGate.resetForTests()
    }

    @Test
    fun consentUnknown_deniesSensing() {
        val session = session(FairyDeviceMode.DEDICATED_CHILD_DEVICE)
        session.start()
        SensingGate.onServiceConnected()

        assertEquals(ConsentState.UNKNOWN, SensingGate.consentState)
        assertFalse(SensingGate.isOpen)
        assertNull(SensingGate.sink)
    }

    @Test
    fun consentConfirmed_butSessionInactive_deniesSensing() {
        SensingGate.setConsentState(ConsentState.CONFIRMED)
        SensingGate.onServiceConnected()

        assertFalse(SensingGate.isOpen)
        assertNull(SensingGate.activeSessionIdentity)
    }

    @Test
    fun confirmedConsent_activeSession_connectedService_allowsSensing() {
        val session = session(FairyDeviceMode.SHARED_PARENT_CHILD_DEVICE)
        SensingGate.setConsentState(ConsentState.CONFIRMED)
        session.start()
        SensingGate.onServiceConnected()

        assertTrue(SensingGate.isOpen)
        assertEquals(session.identity, SensingGate.activeSessionIdentity)
        assertTrue(SensingGate.sink === sink)
    }

    @Test
    fun consentRevoked_duringActiveSession_immediatelyClosesGate() {
        val session = session(FairyDeviceMode.SHARED_PARENT_CHILD_DEVICE)
        SensingGate.setConsentState(ConsentState.CONFIRMED)
        session.start()
        SensingGate.onServiceConnected()
        assertTrue(SensingGate.isOpen)

        SensingGate.setConsentState(ConsentState.REVOKED)

        assertEquals(ConsentState.REVOKED, SensingGate.consentState)
        assertFalse(SensingGate.isOpen)
        assertNull(SensingGate.sink)
        assertTrue("revocation closes sensing, not the explicit session", session.isActive)
    }

    @Test
    fun bothDeviceModes_requireExplicitSession() {
        FairyDeviceMode.entries.forEachIndexed { index, mode ->
            SensingGate.resetForTests()
            SensingGate.setConsentState(ConsentState.CONFIRMED)
            SensingGate.onServiceConnected()
            assertFalse("$mode must not bypass the session boundary", SensingGate.isOpen)

            val session = FairySession.create(
                FairySessionIdentity("session-$index", mode),
                sink,
            )
            session.start()
            assertTrue("$mode must open only after explicit session start", SensingGate.isOpen)
            session.end()
            assertFalse(SensingGate.isOpen)
        }
    }

    @Test
    fun serviceDisconnect_closesGateWithoutDestroyingSession() {
        val session = session(FairyDeviceMode.DEDICATED_CHILD_DEVICE)
        SensingGate.setConsentState(ConsentState.CONFIRMED)
        session.start()
        SensingGate.onServiceConnected()

        SensingGate.onServiceDisconnected()

        assertFalse(SensingGate.isOpen)
        assertTrue(session.isActive)
        assertEquals(session.identity, SensingGate.activeSessionIdentity)
    }

    private fun session(mode: FairyDeviceMode): FairySession = FairySession.create(
        identity = FairySessionIdentity("test-session-${mode.name}", mode),
        sink = sink,
    )
}
