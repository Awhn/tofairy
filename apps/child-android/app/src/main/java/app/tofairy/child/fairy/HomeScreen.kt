package app.tofairy.child.fairy

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.tofairy.child.ui.FairyStage
import app.tofairy.child.ui.josaWaGwa

/**
 * 홈 화면 (CLAUDE.md §10-1~2): "creepy 하지 않은 살아있는 요정" 데모.
 * 요정을 톡 건드리면 인사하고, 데모 칩으로 각 개입 intent → responsebank 대사를 확인할 수 있다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(viewModel: HomeViewModel) {
    val ui by viewModel.ui.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable { viewModel.onFairyTapped() },
        ) {
            FairyStage(mood = ui.mood, bubbleText = ui.line?.text)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                    ),
                )
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = "${ui.fairyName}${josaWaGwa(ui.fairyName)} 함께")
            // 데모용 트리거(실제 앱에서는 센싱/규칙 엔진이 자동으로 만든다).
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DemoChip("오래 봤어요") { viewModel.simulateContinuousUse() }
                DemoChip("어른 콘텐츠") { viewModel.simulateAboveAgeContent() }
                DemoChip("약속 지켰어요") { viewModel.simulatePromiseKept() }
            }
        }
    }
}

@Composable
private fun DemoChip(label: String, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        colors = AssistChipDefaults.assistChipColors(),
    )
}
