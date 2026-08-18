package app.tofairy.child.apiclient

import app.tofairy.child.sensing.ConsentState
import app.tofairy.child.sensing.SensingGate
import app.tofairy.child.sensing.SignalSink
import app.tofairy.child.session.FairyDeviceMode
import app.tofairy.child.session.FairySession
import app.tofairy.child.session.FairySessionIdentity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ConsentSynchronizerTest {

    @Before
    fun setUp() {
        SensingGate.resetForTests()
    }

    @After
    fun tearDown() {
        SensingGate.resetForTests()
    }

    @Test
    fun startupRefresh_appliesLatestExplicitState() = runBlocking {
        val api = MockApiClient(
            currentConsentState = ConsentState.CONFIRMED,
            consentRevision = 10,
            consentUpdatedAt = 100,
        )
        val synchronizer = ConsentSynchronizer(api, FakeConsentControlChannel())

        val result = synchronizer.refreshAtStartup()

        assertTrue(result.isSuccess)
        assertEquals(ConsentState.CONFIRMED, SensingGate.consentState)
    }

    @Test
    fun startupRefreshFailure_remainsFailClosedAsUnknown() = runBlocking {
        val api = MockApiClient(currentConsentState = ConsentState.CONFIRMED).apply {
            consentStatusAvailable = false
        }
        SensingGate.setConsentState(ConsentState.CONFIRMED)
        val synchronizer = ConsentSynchronizer(api, FakeConsentControlChannel())

        val result = synchronizer.refreshAtStartup()

        assertTrue(result.isFailure)
        assertEquals(ConsentState.UNKNOWN, SensingGate.consentState)
        assertFalse(SensingGate.isOpen)
    }

    @Test
    fun controlRevocation_immediatelyClosesActiveSessionGate() {
        val api = MockApiClient()
        val channel = FakeConsentControlChannel()
        val synchronizer = ConsentSynchronizer(api, channel)
        val session = FairySession.create(
            FairySessionIdentity("active-session", FairyDeviceMode.DEDICATED_CHILD_DEVICE),
            SignalSink { signal -> signal.discard() },
        )
        SensingGate.setConsentState(ConsentState.CONFIRMED)
        session.start()
        SensingGate.onServiceConnected()
        synchronizer.connectControlChannel()
        assertTrue(SensingGate.isOpen)

        channel.emit(
            ConsentUpdate(
                state = ConsentState.REVOKED,
                revision = 20,
                updatedAt = 200,
            ),
        )

        assertFalse(SensingGate.isOpen)
        assertTrue(session.isActive)
        assertEquals(ConsentState.REVOKED, SensingGate.consentState)
    }

    @Test
    fun lowerRevisionConfirmation_cannotOverrideNewerRevocation() {
        val channel = FakeConsentControlChannel()
        val synchronizer = ConsentSynchronizer(MockApiClient(), channel)
        synchronizer.connectControlChannel()

        channel.emit(ConsentUpdate(ConsentState.REVOKED, revision = 20, updatedAt = 200))
        channel.emit(ConsentUpdate(ConsentState.CONFIRMED, revision = 19, updatedAt = 999))

        assertEquals(ConsentState.REVOKED, SensingGate.consentState)
    }

    private class FakeConsentControlChannel : ConsentControlChannel {
        private var listener: ConsentControlListener? = null

        override fun subscribe(listener: ConsentControlListener): ConsentControlSubscription {
            this.listener = listener
            return ConsentControlSubscription { this.listener = null }
        }

        fun emit(update: ConsentUpdate) {
            listener?.onConsentUpdate(update)
        }
    }
}
