package app.tofairy.child.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.tofairy.child.ui.FairyStage

/**
 * 온보딩 각성 의식 화면 (CLAUDE.md §10-1). 모델 없이 더미 intent + responsebank 로 동작.
 * 마지막 단계는 동의 게이트(#6).
 */
@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    onFinished: () -> Unit,
) {
    val ui by viewModel.ui.collectAsState()

    // 완료 전이는 부수효과로 처리(컴포지션 중 콜백 호출로 인한 재구성 루프 방지).
    LaunchedEffect(ui.finished) {
        if (ui.finished) onFinished()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        FairyStage(mood = ui.mood, bubbleText = ui.line?.text)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (ui.step) {
                OnboardingViewModel.Step.ARRIVAL -> {
                    Button(
                        onClick = viewModel::onArrivalContinue,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("안녕!") }
                }

                OnboardingViewModel.Step.NAMING -> {
                    OutlinedTextField(
                        value = ui.fairyName,
                        onValueChange = viewModel::onNameChanged,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("요정 이름") },
                    )
                    Button(
                        onClick = viewModel::onNameConfirmed,
                        enabled = ui.fairyName.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("이 이름으로 부를게") }
                }

                OnboardingViewModel.Step.FIRST_BOND -> {
                    Button(
                        onClick = viewModel::onBondContinue,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("좋아!") }
                }

                OnboardingViewModel.Step.CONSENT -> {
                    // 부모(보호자) 대상 동의 안내. 아이 문구와 구분되는 보호자용 텍스트.
                    Text(
                        text = "보호자 확인이 필요해요.\n아이가 보는 화면 맥락은 기기 안에서만 살펴보며, 내용은 저장·전송되지 않습니다. 동의하시겠어요?",
                        textAlign = TextAlign.Center,
                    )
                    Button(
                        onClick = { viewModel.onConsentDecision(true) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("동의하고 시작하기") }
                    OutlinedButton(
                        onClick = { viewModel.onConsentDecision(false) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("나중에 할게요") }
                }

                OnboardingViewModel.Step.DONE -> Unit
            }
        }
    }
}
