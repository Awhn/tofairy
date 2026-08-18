package app.tofairy.child.router

import app.tofairy.child.core.FairyIntent

/**
 * 라우터 (CLAUDE.md §6) — 상황 컨텍스트 → 구조화 intent.
 *
 * 불변식 #3: 라우터는 판단기일 뿐 화자가 아니다. 출력은 [FairyIntent] 뿐이며 자유 텍스트가 없다.
 *
 * 구현은 플러그러블:
 *  - [RuleBasedRouter]: 결정적 규칙(저사양 폴백/골격 기본값).
 *  - (후순위) 별도 ML router 후보: 구조화 function call만 [FairyIntent]로 디코드.
 * A축 Shieldstral과 라우터 모델은 완전히 별개이며, 라우터 모델 선택은 아직 Open Issue다.
 */
fun interface Router {
    fun route(context: RouterContext): FairyIntent
}
