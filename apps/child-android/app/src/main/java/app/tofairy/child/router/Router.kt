package app.tofairy.child.router

import app.tofairy.child.core.FairyIntent

/**
 * 라우터 (CLAUDE.md §6) — 상황 컨텍스트 → 구조화 intent.
 *
 * 불변식 #3: 라우터는 판단기일 뿐 화자가 아니다. 출력은 [FairyIntent] 뿐이며 자유 텍스트가 없다.
 *
 * 구현은 플러그러블:
 *  - [RuleBasedRouter]: 결정적 규칙(저사양 폴백/골격 기본값).
 *  - (후순위) GemmaFunctionCallRouter: Gemma 4 E2B function-calling. 자유 텍스트 출력 미사용,
 *    선택된 function call 만 [FairyIntent] 로 디코드.
 * 모델 선택은 앱 골격 후 벤치마크로 결정한다(저사양 기기 고려).
 */
fun interface Router {
    fun route(context: RouterContext): FairyIntent
}
