package app.tofairy.child.localstore

import kotlinx.coroutines.flow.Flow

/**
 * 관계·집계 상태의 암호화 온디바이스 저장 (CLAUDE.md §3, §7).
 *
 * 구현 계약:
 *  - 저장은 Android Keystore 등 platform secure storage로 보호된 키로 암호화한다(../../docs/50 §4).
 *  - 집계/관계 상태만 저장한다. 원시 신호·민감 라벨 저장 금지(불변식 #1/#2).
 *  - 평문이 백업/로그로 새지 않는다(매니페스트 allowBackup=false + data_extraction_rules).
 */
interface RelationshipStore {
    val state: Flow<RelationshipState>
    suspend fun current(): RelationshipState
    suspend fun update(transform: (RelationshipState) -> RelationshipState)
    suspend fun clear()
}
