package app.tofairy.child.localstore

import android.content.Context
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.io.File

/**
 * [RelationshipStore] 의 Jetpack Security 기반 구현 (골격 단계).
 *
 * MasterKey 는 Android Keystore(가능하면 StrongBox)로 보호된다.
 * 최종 키·저장소 수명주기는 ../../docs/50 §4 계약에 맞춰 검증한다.
 * 저장 대상은 [RelationshipState] (집계/관계만) — 원시 라벨은 들어올 수 없는 타입 구조다.
 */
class EncryptedRelationshipStore(
    context: Context,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
) : RelationshipStore {

    private val appContext = context.applicationContext
    private val file = File(appContext.filesDir, FILE_NAME)
    private val masterKey = MasterKey.Builder(appContext)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .setRequestStrongBoxBacked(true)
        .build()

    private val mutex = Mutex()
    private val _state = MutableStateFlow(readFromDisk())
    override val state = _state.asStateFlow()

    override suspend fun current(): RelationshipState = _state.value

    override suspend fun update(transform: (RelationshipState) -> RelationshipState) {
        mutex.withLock {
            val next = transform(_state.value)
            writeToDisk(next)
            _state.value = next
        }
    }

    override suspend fun clear() {
        mutex.withLock {
            runCatching { file.delete() }
            _state.value = RelationshipState()
        }
    }

    private fun encryptedFile(): EncryptedFile =
        EncryptedFile.Builder(
            appContext,
            file,
            masterKey,
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB,
        ).build()

    private fun readFromDisk(): RelationshipState {
        if (!file.exists()) return RelationshipState()
        return runCatching {
            val bytes = encryptedFile().openFileInput().use { it.readBytes() }
            RelationshipStateCodec.decode(bytes.decodeToString(), json)
        }.getOrDefault(RelationshipState())
    }

    private fun writeToDisk(state: RelationshipState) {
        // EncryptedFile 은 덮어쓰기를 허용하지 않으므로 기존 파일을 먼저 제거.
        if (file.exists()) file.delete()
        encryptedFile().openFileOutput().use { out ->
            out.write(RelationshipStateCodec.encode(state, json).encodeToByteArray())
        }
    }

    private companion object {
        const val FILE_NAME = "relationship_state.enc"
    }
}
