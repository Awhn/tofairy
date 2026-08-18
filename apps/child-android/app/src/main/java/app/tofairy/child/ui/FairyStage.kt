package app.tofairy.child.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.tofairy.child.fairy.FairyMood
import app.tofairy.child.fairy.FairyView

/**
 * 요정 무대: 부드러운 배경 + 살아있는 요정 + (있으면) 말풍선.
 *
 * 불변식 #3: [bubbleText] 는 항상 responsebank 가 고른 [app.tofairy.child.responsebank.FairyLine.text] 여야 한다.
 * 호출부는 모델 자유 텍스트를 절대 여기로 넘기지 않는다.
 */
@Composable
fun FairyStage(
    mood: FairyMood,
    bubbleText: String?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFFEFF4FF), Color(0xFFF7F8FE)),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            FairyView(
                mood = mood,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp),
            )

            AnimatedVisibility(
                visible = bubbleText != null,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                SpeechBubble(text = bubbleText.orEmpty())
            }
        }
    }
}

@Composable
fun SpeechBubble(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .clip(RoundedCornerShape(20.dp)),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 4.dp,
        tonalElevation = 1.dp,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(20.dp),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
    }
}
