package app.tofairy.child

import android.content.Context
import app.tofairy.child.apiclient.ApiClient
import app.tofairy.child.apiclient.MockApiClient
import app.tofairy.child.localstore.EncryptedRelationshipStore
import app.tofairy.child.localstore.RelationshipStore
import app.tofairy.child.responsebank.AssetAudioPlayer
import app.tofairy.child.responsebank.AudioPlayer
import app.tofairy.child.responsebank.ResponseBank
import app.tofairy.child.router.RuleBasedRouter
import app.tofairy.child.router.Router

/**
 * 간단한 서비스 로케이터(골격 단계). 추후 Hilt 등으로 교체 가능.
 *
 * 라우터는 [RuleBasedRouter] (결정적·저사양 폴백)로 기본 배선한다.
 * 향후 별도 검증된 ML 라우터가 생기면 feature flag로 교체할 지점이다. A축 Shieldstral과는 무관하다.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val responseBank: ResponseBank by lazy { ResponseBank.load(appContext) }
    val audioPlayer: AudioPlayer by lazy { AssetAudioPlayer(appContext) }
    val relationshipStore: RelationshipStore by lazy { EncryptedRelationshipStore(appContext) }
    val apiClient: ApiClient by lazy { MockApiClient() }

    val router: Router by lazy {
        // if (BuildConfig.ML_INFERENCE_ENABLED) VerifiedMlRouter(...) else RuleBasedRouter()
        RuleBasedRouter()
    }
}
