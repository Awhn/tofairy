# CLAUDE.md — child-android (자녀용 네이티브 앱)

앱 스코프 개발 가이드. 상위 규칙은 루트 `../../CLAUDE.md`, 제품 출처는 `../../To_Fairy_제품_메커니즘_설계.md`. 충돌 시 우선순위: 루트 불변식 > 제품 기획 > 본 문서.

## 0. 이 앱이 하는 일
자녀(만 7~9세) 폰/세션에서 센싱 → 온디바이스 판단 → 요정과의 관계형 상호작용을 수행하고, 부모에게는 E2EE 맥락 다이제스트만 내보낸다. 거의 모든 처리가 기기 안에서 끝난다.

## 1. 이 앱에서 특히 조심할 불변식 (루트 #1~#7 중 앱 핵심)
* **#3** — 온디바이스 LLM은 라우터/판단기일 뿐, 화자가 아니다. Gemma 4 E2B를 도입하면서 가장 큰 위험은 "그냥 모델이 아이에게 말하게" 하고 싶은 유혹이다. 금지. 모델 출력은 function call(구조화 intent)·분류 결과로만 소비되고, 아이가 듣는 문장은 전량 응답 뱅크에서 선택된다. 모델의 자유 텍스트가 child-facing 경로에 닿는 코드는 머지 금지.
* **#1/#2** — 콘텐츠 원문·민감 라벨은 `Ephemeral*` 타입으로만 다루고 즉시 폐기. 디스크/로그/네트워크로 가는 경로 0.
* **#4** — 케이스 B: 요정 모드 세션 밖에서는 센싱 파이프라인이 시작조차 안 된다.
* **#6** — 동의 미확인 시 센싱 비활성(시작 게이트).
* **#7** — 아이에게 향하는 모든 문구는 쉬운 언어. 비난·훈계·비밀 강요 금지.

Claude Code: "서버로 콘텐츠 분석 전송", "모델이 직접 답변 생성해 아이에게 표시", "사용 로그 파일 저장", "세션 밖 백그라운드 수집" 류 요청은 구현 대신 플래그.

## 2. 기술 구성
* 언어/UI: Kotlin + Jetpack Compose
* 온디바이스 추론: Gemma 4 E2B (후보) — MediaPipe LLM Inference API 또는 MLC/LiteRT 런타임. function calling으로 라우터 intent 출력, 멀티모달로 A축 콘텐츠 판단. ML 최종 선택은 후순위(앱 골격 먼저).
* 암호화: libsodium(또는 Tink) + Android Keystore/StrongBox (`../../docs/50` §1)
* 백엔드 연동: `../../docs/40` 계약 소비 (구현은 후순위, 클라이언트는 mock으로 선개발 가능)

## 3. 모듈 구조
```
child-android/
├── sensing/          # AccessibilityService + UsageStats → EphemeralSignal
├── screening/
│   ├── axis_a/       # 적절성: 연령가 판정 (등급 매핑 + 온디바이스 추정)
│   └── axis_b/       # 중독성: 사용패턴 신호 규칙 엔진
├── router/           # 상황 → intent (Gemma function-calling 또는 경량 분류기)
├── responsebank/     # intent → 대사·오디오 선택·재생 (번들 자산)
├── fairy/            # 요정 렌더링·애니메이션·상호작용 (Compose)
├── onboarding/       # 각성 의식·이름짓기·동의 게이트
├── localstore/       # 관계·기억·집계 상태 암호화 저장 / Ephemeral 타입
├── digest/           # 다이제스트 생성·E2EE 암호화·송신 (유일 송신 게이트)
├── session/          # 케이스 B 요정 모드 세션 경계
└── apiclient/        # 백엔드 계약 클라이언트 (mock 가능)
```
> 현재 구현은 위 모듈을 단일 앱 모듈(`app`) 내 패키지(`app.tofairy.child.*`)로 둔다. 의존성이 안정되면 Gradle 모듈로 분리한다.

## 4. 핵심 Android 구성
* AccessibilityService: 포그라운드 앱·node-tree 텍스트·전환 이벤트 수신. node-tree 텍스트는 `EphemeralSignal`로만. 매니페스트에 서비스·`accessibility_service_config` 선언, 사용자(부모) 동의 하 활성.
* Foreground Service(케이스 A): 센싱·요정 상시 구동 표시. 케이스 B는 세션 동안만.
* UsageStatsManager: `PACKAGE_USAGE_STATS` 특수 권한(설정 화면 유도).
* DevicePolicyManager(케이스 A, 선택): 권한 회수 방지·기본 설정. 7~9세 대상이라 과도한 잠금 지양.
* 권한 원칙: 위치·연락처·메시지·카메라 권한은 요청하지 않는다(불변식 + 규제 표면 축소).

## 5. 인앱 데이터 플로우
```
AccessibilityService/UsageStats
        │  (EphemeralSignal, 메모리 전용)
        ▼
screening.axis_a (연령가) ─┐
screening.axis_b (패턴)  ─┼─▶ 판단 결과
        ▼                 │
      router.intent  ◀────┘   (Gemma function-call: 예 suggest_break)
        ▼
responsebank.select → fairy.play (오디오+애니)
        ▼
localstore (집계·관계만)  ──▶ digest.build → E2EE → apiclient.send
```
* A축 분류 직후 라벨 폐기. localstore에는 집계 지표·관계 상태만 남는다(원시·민감 라벨 금지).

## 6. 온디바이스 ML 통합 지점 (Gemma 4 E2B 후보)
* 라우터: 상황 컨텍스트(판단 결과·시간대·최근 상호작용) → function calling으로 intent 선택. 자유 텍스트 출력 미사용.
* A축 보조: 등급 없는 콘텐츠의 '등가 연령가' 추정에 멀티모달(텍스트/화면) 활용 가능. 출력은 등급 라벨(폐기 대상)뿐.
* B축: LLM 불필요 — 순수 규칙 엔진(가볍고 결정적).
* 하드웨어 바닥: E2B도 보급형/저사양 기기엔 부담일 수 있음. 7~9세는 물려받은 폰 비율이 높음 → 저사양 폴백(경량 분류기) 경로를 인터페이스로 분리해 모델을 플러그러블하게. (최종 선택은 앱 골격 후 벤치마크)
* 추론은 전량 온디바이스, 결과는 네트워크로 안 나간다.

## 7. 응답 뱅크 (번들 내장 · OTA 없음)
* intent 스키마 + 대사(JSON) + 오디오 클립을 앱 번들에 내장, 갱신은 앱 업데이트로. (자산 스토리지·OTA 불필요)
* 오디오: 성우 녹음 + 클론(`제품 설계` §10.3) → 사전 녹음 클립을 온디바이스 재생(지연 0).
* 상세 스키마는 `../../docs/30_대화_응답뱅크.md`(예정)에서. 본 앱은 그 스키마의 소비자.

## 8. API 클라이언트
* `../../docs/40` 계약 소비: `consent/status`(센싱 게이트), `pairing/*`(키교환), `digests`(E2EE 업로드).
* 송신은 `digest` 모듈을 통해서만. apiclient는 암호문 blob만 전송, 평문 다이제스트를 받지 않는다.
* 백엔드 미구현 동안 mock 서버/페이크 구현으로 앱 선개발.

## 9. 빌드/실행
* Gradle(Kotlin DSL) + 버전 카탈로그(`gradle/libs.versions.toml`). `minSdk = 26`, `compileSdk/targetSdk = 35`.
* ML 런타임 의존성은 `BuildConfig.ML_INFERENCE_ENABLED` feature flag로 분리해 골격 빌드가 모델 없이도 돌게(기본 off).
* 빌드: `./gradlew :app:assembleDebug` / 단위 테스트: `./gradlew :app:testDebugUnitTest` (Android SDK 필요).

## 10. 개발 순서 (제안)
1. 앱 골격 + 요정 UI + 온보딩 각성 의식(모델 없이, 더미 intent로) — 핵심 경험 먼저. ✅ (v0.1 골격)
2. 응답뱅크 소비 + 오디오 재생 — 요정이 '말하는' 느낌 완성. ✅ (텍스트/오디오 경로, 클립 자산은 추후)
3. sensing + axis_b(규칙) — 중독성 개입을 실제 신호로. ◐ (구조/규칙 엔진 완료, UsageStats 수집기 배선은 추후)
4. router(규칙 버전) → 이후 Gemma function-calling 교체. ◐ (규칙 라우터 완료, Gemma 교체 지점 표시)
5. axis_a(연령가 매핑) + 온디바이스 추정. ◐ (인터페이스/매핑 완료, 추정기 미배선)
6. digest + E2EE + apiclient(mock). ◐ (파이프라인/인터페이스 + mock 완료, 실제 E2EE sealer 미구현)
7. session(케이스 B) 별도 트랙. ◐ (세션 경계 + 게이트 완료)

1~2단계만으로도 "creepy하지 않은 살아있는 요정" 데모가 가능하다 — 투자·사용자 검증의 핵심 자산.

## 변경 이력
* v0.1: 자녀 앱 개발 가이드 — 모듈 구조·Android 구성·인앱 플로우·Gemma 4 E2B 통합 지점·응답뱅크 번들·개발 순서. 온디바이스 LLM은 라우터/판단기로만(불변식 #3 강조).
* v0.1-impl: 앱 골격 구현 — Compose 요정 UI·온보딩 각성 의식·응답뱅크 소비·규칙 라우터/규칙 엔진·ephemeral 타입·센싱 게이트·세션 경계·localstore(암호화)·digest 파이프라인·mock apiclient. 단위 테스트(불변식/라우터/규칙/응답뱅크).
