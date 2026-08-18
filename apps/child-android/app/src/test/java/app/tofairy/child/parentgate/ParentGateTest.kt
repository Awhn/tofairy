package app.tofairy.child.parentgate

import app.tofairy.child.digest.ContextDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ParentGateTest {

    @Test
    fun pinAuthenticationRequired_beforeProjectionSourceIsRead() {
        var sourceReads = 0
        val gate = gate { sourceReads += 1 }
        val wrongPin = "0000".toCharArray()

        val result = gate.authenticate(wrongPin)

        assertEquals(ParentAuthenticationResult.Rejected, result)
        assertEquals(ParentAuthState.LOCKED, gate.authState)
        assertEquals(0, sourceReads)
        assertTrue(wrongPin.all { it == '\u0000' })
    }

    @Test
    fun authenticatedSession_canReadOnlyDigestProjection() {
        var sourceReads = 0
        val gate = gate { sourceReads += 1 }

        val authentication = gate.authenticate("2468".toCharArray())
        val session = (authentication as ParentAuthenticationResult.Authenticated).session
        val access = session.readDigests()

        assertTrue(access is ParentDataAccess.Granted)
        assertEquals(emptyList<ContextDigest>(), (access as ParentDataAccess.Granted).digests)
        assertEquals(1, sourceReads)
        assertEquals(ParentAuthState.AUTHENTICATED, gate.authState)
        assertEquals(ParentSettingsAccess.GRANTED, session.accessSettings())
    }

    @Test
    fun lockedGate_deniesPreviouslyAuthenticatedSession() {
        var sourceReads = 0
        val gate = gate { sourceReads += 1 }
        val session = (
            gate.authenticate("2468".toCharArray()) as ParentAuthenticationResult.Authenticated
            ).session

        gate.lock()
        val access = session.readDigests()

        assertEquals(ParentDataAccess.Denied, access)
        assertEquals(ParentSettingsAccess.DENIED, session.accessSettings())
        assertFalse(session.isActive)
        assertEquals(0, sourceReads)
    }

    @Test
    fun parentPublicSurface_doesNotExposeChildRawStoreTypes() {
        val forbiddenTypeNames = listOf(
            "EphemeralSignal",
            "ScreeningSample",
            "DimensionAssessment",
            "RelationshipState",
        )
        val publicSurface = (ParentGate::class.java.methods + ParentModeSession::class.java.methods)
            .joinToString(separator = "\n") { method -> method.toGenericString() }

        forbiddenTypeNames.forEach { forbidden ->
            assertFalse("parent API must not expose $forbidden", publicSurface.contains(forbidden))
        }
        assertTrue(
            ParentDigestSource::class.java.methods
                .single { it.name == "readDigests" }
                .genericReturnType.typeName
                .contains(ContextDigest::class.java.name),
        )
    }

    @Test
    fun parentAuthentication_closesSharedChildModeBeforePinVerification() {
        var childModeClosed = false
        val gate = ParentGate(
            credentialStore = ParentPinCredentialStore { pin ->
                assertTrue(childModeClosed)
                pin.contentEquals("2468".toCharArray())
            },
            digestSource = ParentDigestSource { emptyList() },
            childModeBoundary = SharedDeviceChildModeBoundary { childModeClosed = true },
        )

        assertTrue(
            gate.authenticate("2468".toCharArray()) is ParentAuthenticationResult.Authenticated,
        )
    }

    private fun gate(onSourceRead: () -> Unit): ParentGate = ParentGate(
        credentialStore = ParentPinCredentialStore { pin ->
            pin.contentEquals("2468".toCharArray())
        },
        digestSource = ParentDigestSource {
            onSourceRead()
            emptyList()
        },
        childModeBoundary = SharedDeviceChildModeBoundary { },
    )
}
