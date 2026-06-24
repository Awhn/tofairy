package app.tofairy.child.ui

/**
 * 한국어 조사 보정 헬퍼.
 *
 * 아이가 지은 요정 이름은 받침 유무가 제각각이라 조사를 고정하면 어색해진다("별이와"/"단추와" 등).
 * 화면 라벨 같은 UI 크롬에서만 사용한다. 요정의 '발화'는 전량 응답뱅크에서 오므로(불변식 #3)
 * 이 헬퍼는 그 경로에 쓰이지 않는다.
 */

/** 마지막 글자에 받침(종성)이 있으면 true. 한글 음절이 아니면 false 로 본다. */
private fun endsWithFinalConsonant(text: String): Boolean {
    val last = text.trim().lastOrNull() ?: return false
    if (last !in '가'..'힣') return false // 한글 음절 영역(가~힣)
    return (last.code - 0xAC00) % 28 != 0
}

/** 받침 있으면 "과", 없으면 "와". */
fun josaWaGwa(word: String): String = if (endsWithFinalConsonant(word)) "과" else "와"

/** 받침 있으면 "이", 없으면 "가". */
fun josaIGa(word: String): String = if (endsWithFinalConsonant(word)) "이" else "가"

/** 받침 있으면 "을", 없으면 "를". */
fun josaEulReul(word: String): String = if (endsWithFinalConsonant(word)) "을" else "를"
