package app.tofairy.child

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ToFairyApp : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // 설치/서비스 연결 상태는 동의가 아니다. cold start마다 UNKNOWN에서 최신 상태를 확인하고,
        // 실행 중 철회는 control channel을 통해 같은 synchronizer에 들어오게 한다.
        container.consentSynchronizer.connectControlChannel()
        applicationScope.launch {
            container.consentSynchronizer.refreshAtStartup()
        }
    }

    override fun onTerminate() {
        container.consentSynchronizer.close()
        applicationScope.cancel()
        super.onTerminate()
    }
}
