package app.tofairy.child.localstore

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RelationshipStateCodecTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun v1BreakAcceptance_isMigratedIntoPeriodAggregate() {
        val legacy = """
            {
              "fairyName": "별이",
              "awakened": true,
              "bondLevel": 3,
              "promiseStreakDays": 4,
              "breaksAccepted": 7,
              "lastInteractionAt": 123
            }
        """.trimIndent()

        val migrated = RelationshipStateCodec.decode(legacy, json)

        assertEquals(RelationshipState.SCHEMA_VERSION, migrated.schemaVersion)
        assertEquals(7, migrated.periodAggregate.breaksAccepted)
        assertEquals(0, migrated.periodAggregate.breakSuggestions)
        assertEquals(0L, migrated.periodAggregate.periodStart)
        assertEquals("별이", migrated.fairyName)
    }

    @Test
    fun v2RoundTrip_preservesSeparatedAggregateAndSchemaVersion() {
        val original = RelationshipState(
            bondLevel = 5,
            promiseStreakDays = 6,
            periodAggregate = PeriodAggregateState(
                periodStart = 100L,
                breakSuggestions = 4,
                breaksAccepted = 3,
                interventions = 2,
            ),
        )

        val encoded = RelationshipStateCodec.encode(original, json)
        val decoded = RelationshipStateCodec.decode(encoded, json)

        assertTrue(encoded.contains("\"schemaVersion\":2"))
        assertEquals(original, decoded)
    }
}
