package app.tofairy.child.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import app.tofairy.child.AppContainer
import app.tofairy.child.ToFairyApp
import app.tofairy.child.fairy.FairyMood
import app.tofairy.child.fairy.HomeScreen
import app.tofairy.child.fairy.HomeViewModel
import app.tofairy.child.onboarding.OnboardingScreen
import app.tofairy.child.onboarding.OnboardingViewModel
import app.tofairy.child.ui.theme.ToFairyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as ToFairyApp).container
        setContent {
            ToFairyTheme {
                ToFairyRoot(container)
            }
        }
    }
}

@Composable
private fun ToFairyRoot(container: AppContainer) {
    // 암호화 저장소 로드 전에는 null. 이때 온보딩을 깜빡 노출하지 않도록 중립 화면을 보여준다.
    val state by container.relationshipStore.state.collectAsState(initial = null)
    // 세션 내 강제 전환(온보딩 직후 전이)을 위한 로컬 플래그.
    var onboardingDone by remember { mutableStateOf(false) }

    when {
        state == null -> {
            // 로딩 중: 같은 비주얼 언어의 중립 무대(요정만 떠 있음).
            FairyStage(mood = FairyMood.CALM, bubbleText = null)
        }

        state?.awakened == true || onboardingDone -> {
            val vm: HomeViewModel = viewModel(
                factory = factory {
                    HomeViewModel(container.router, container.responseBank, container.audioPlayer, container.relationshipStore)
                },
            )
            HomeScreen(viewModel = vm)
        }

        else -> {
            val vm: OnboardingViewModel = viewModel(
                factory = factory {
                    OnboardingViewModel(
                        container.responseBank,
                        container.audioPlayer,
                        container.relationshipStore,
                        container.consentSynchronizer,
                    )
                },
            )
            OnboardingScreen(viewModel = vm, onFinished = { onboardingDone = true })
        }
    }
}

/** 간단한 ViewModel 팩토리 헬퍼(골격 단계). */
private inline fun <VM : ViewModel> factory(crossinline create: () -> VM): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = create() as T
    }
