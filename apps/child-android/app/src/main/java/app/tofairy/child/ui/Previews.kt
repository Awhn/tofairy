package app.tofairy.child.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.tofairy.child.fairy.FairyMood
import app.tofairy.child.fairy.FairyView
import app.tofairy.child.ui.theme.ToFairyTheme

/**
 * 디자인 미리보기 (Android Studio Compose Preview 전용, 런타임 비포함).
 * 요정 무드/무대를 빠르게 시각 점검하기 위한 도구. 불변식과 무관.
 */

@Preview(name = "Fairy · all moods", showBackground = true, backgroundColor = 0xFFF7F8FE, heightDp = 900)
@Composable
private fun FairyMoodsPreview() {
    ToFairyTheme {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FairyMood.entries.forEach { mood ->
                Text(mood.name)
                FairyView(mood = mood, modifier = Modifier.size(160.dp))
            }
        }
    }
}

@Preview(name = "Stage · with bubble", widthDp = 360, heightDp = 720)
@Composable
private fun FairyStageWithBubblePreview() {
    ToFairyTheme {
        FairyStage(
            mood = FairyMood.CELEBRATE,
            bubbleText = "약속을 스스로 지켰구나. 정말 자랑스러워!",
        )
    }
}

@Preview(name = "Stage · idle", widthDp = 360, heightDp = 720)
@Composable
private fun FairyStageIdlePreview() {
    ToFairyTheme {
        FairyStage(mood = FairyMood.CALM, bubbleText = null)
    }
}

@Preview(name = "Speech bubble")
@Composable
private fun SpeechBubblePreview() {
    ToFairyTheme {
        SpeechBubble(text = "우리 잠깐 눈 좀 쉬게 해줄까? 나랑 같이 기지개 한 번 켜자!")
    }
}
