package app.tofairy.child.fairy

import androidx.compose.ui.graphics.Color

/**
 * 요정의 표현 무드. intent → responsebank 로 '말'을 고르는 것과 별개로,
 * 시각 표현을 결정하는 상태값. 무드는 분위기만 바꾸며 문구를 만들지 않는다(불변식 #3).
 */
enum class FairyMood(
    val coreColor: Color,
    val glowColor: Color,
    val breathPeriodMs: Int,
    val sparkleCount: Int,
) {
    /** 평온하게 곁에 있음(기본). */
    CALM(Color(0xFF8EC5FC), Color(0xFFB9D7FF), breathPeriodMs = 4200, sparkleCount = 5),

    /** 반가움/인사. */
    HAPPY(Color(0xFFFFE08A), Color(0xFFFFF1B8), breathPeriodMs = 3000, sparkleCount = 8),

    /** 부드러운 주의 환기(휴식/콘텐츠 묻기). */
    GENTLE_ALERT(Color(0xFFB9A7FF), Color(0xFFD9CCFF), breathPeriodMs = 2600, sparkleCount = 6),

    /** 축하/격려. */
    CELEBRATE(Color(0xFFFFB3C7), Color(0xFFFFD6E2), breathPeriodMs = 2200, sparkleCount = 10),

    /** 막 깨어남(온보딩). */
    AWAKENING(Color(0xFFCFE8FF), Color(0xFFEAF5FF), breathPeriodMs = 5200, sparkleCount = 4),
}
