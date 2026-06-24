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
    val state by container.relationshipStore.state.collectAsState(initial = null)
    // 각성(온보딩) 완료 여부에 따라 분기. 세션 내 강제 전환을 위한 로컬 플래그도 둔다.
    var onboardingDone by remember { mutableStateOf(false) }

    val awakened = state?.awakened == true || onboardingDone

    if (!awakened) {
        val vm: OnboardingViewModel = viewModel(
            factory = factory { OnboardingViewModel(container.responseBank, container.audioPlayer, container.relationshipStore) },
        )
        OnboardingScreen(viewModel = vm, onFinished = { onboardingDone = true })
    } else {
        val vm: HomeViewModel = viewModel(
            factory = factory {
                HomeViewModel(container.router, container.responseBank, container.audioPlayer, container.relationshipStore)
            },
        )
        HomeScreen(viewModel = vm)
    }
}

/** 간단한 ViewModel 팩토리 헬퍼(골격 단계). */
private inline fun <VM : ViewModel> factory(crossinline create: () -> VM): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = create() as T
    }
