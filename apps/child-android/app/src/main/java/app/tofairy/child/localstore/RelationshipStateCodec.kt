package app.tofairy.child.localstore

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** 암호화 파일 안의 relationship JSON schema migration 경계. */
internal object RelationshipStateCodec {
    fun decode(raw: String, json: Json): RelationshipState {
        val objectValue = json.parseToJsonElement(raw).jsonObject
        val storedVersion = objectValue["schemaVersion"]?.jsonPrimitive?.intOrNull
            ?: if ("periodAggregate" in objectValue) RelationshipState.SCHEMA_VERSION else 1

        return when (storedVersion) {
            1 -> json.decodeFromString(RelationshipStateV1.serializer(), raw).toV2()
            RelationshipState.SCHEMA_VERSION ->
                json.decodeFromString(RelationshipState.serializer(), raw)
            else -> error("unsupported relationship schema: $storedVersion")
        }
    }

    fun encode(state: RelationshipState, json: Json): String {
        require(state.schemaVersion == RelationshipState.SCHEMA_VERSION)
        return json.encodeToString(RelationshipState.serializer(), state)
    }

    @Serializable
    private data class RelationshipStateV1(
        val fairyName: String? = null,
        val awakened: Boolean = false,
        val bondLevel: Int = 0,
        val promiseStreakDays: Int = 0,
        val breaksAccepted: Int = 0,
        val lastInteractionAt: Long = 0L,
    ) {
        fun toV2(): RelationshipState = RelationshipState(
            fairyName = fairyName,
            awakened = awakened,
            bondLevel = bondLevel,
            promiseStreakDays = promiseStreakDays,
            periodAggregate = PeriodAggregateState(
                periodStart = 0L,
                breaksAccepted = breaksAccepted,
            ),
            lastInteractionAt = lastInteractionAt,
        )
    }
}
