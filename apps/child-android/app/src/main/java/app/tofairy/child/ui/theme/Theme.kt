package app.tofairy.child.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// 7~9세 대상: 부드럽고 따뜻한 파스텔. 자극적이지 않게.
private val FairyBlue = Color(0xFF8EC5FC)
private val FairyLilac = Color(0xFFE0C3FC)
private val FairyDeep = Color(0xFF5B6CF0)

private val LightColors = lightColorScheme(
    primary = FairyDeep,
    secondary = FairyBlue,
    tertiary = FairyLilac,
    background = Color(0xFFF7F8FE),
    surface = Color(0xFFFFFFFF),
)

private val DarkColors = darkColorScheme(
    primary = FairyBlue,
    secondary = FairyLilac,
    tertiary = FairyDeep,
)

private val FairyTypography = Typography(
    headlineMedium = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 24.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 18.sp, lineHeight = 26.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
)

@Composable
fun ToFairyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = FairyTypography,
        content = content,
    )
}
