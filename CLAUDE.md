# CLAUDE.md — To Fairy 개발 가이드

> 이 파일은 Claude Code(및 모든 기여자)가 To Fairy를 개발할 때 따르는 최상위 지침이다.
> 제품 기획의 단일 출처(source of truth)는 `../To_Fairy_제품_메커니즘_설계.md`. 충돌 시 기획서가 우선한다.

---

## 0. 한 줄 정의

부모가 자녀를 감시하는 도구가 아니라, **자녀(만 7~9세)가 '핸드폰 요정'과 함께 자기 핸드폰 사용을 돌보는 관계형 동반자**.
스크리닝은 자녀 기기 안에서 수행하고, 부모에게는 '무엇을 봤는지'가 아니라 사전에 허용한 코드와 집계로 구성된 **요약 맥락**만 전달한다.

---

## 1. 절대 불변식 (Invariants) — 이걸 깨는 코드는 머지 금지

다음 원칙은 제품의 정체성이자 법적·프라이버시 경계다. 편의·성능·기능을 이유로 위반하지 않는다.

1. **원시 스크리닝 자료는 자녀 기기를 떠나지 않는다.** 화면 원문, 스크린샷, URL·검색어·영상명, 앱 내부 콘텐츠명, 세부 사용 로그와 dimension별 판정은 백엔드나 부모 기기로 보내지 않는다. 부모에게 전달 가능한 콘텐츠 관련 산출물은 허용된 집계와 사전 정의 `DigestHighlight`로 구성한 `ContextDigest`뿐이며, 이 또한 E2EE로 암호화한다.
2. **원시 자료와 민감 중간 결과는 목적이 끝나면 폐기한다.** 일일 A축 분석에 필요한 최소 스크린샷과 메타데이터는 자녀 기기의 암호화된 임시 저장소에만 제한적으로 보존할 수 있다. 정상 집계 후 원본 sample을 삭제하고, screenshot별 `DimensionAssessment`는 로그·DB·다이제스트에 남기지 않는다. 메모리 전용 신호는 소비 직후 폐기한다.
3. **요정은 생성된 문장을 말하지 않는다.** 아이가 듣는 모든 발화는 사람이 작성·검수한 응답 뱅크에서 선택한다. 현재 Router는 규칙 기반이며, 향후 ML Router를 검토하더라도 구조화 intent만 출력한다. A축 Shieldstral과 Router는 서로 독립이며 어느 모델의 자유 텍스트도 child-facing 경로에 닿지 않는다.
4. **케이스 A/B 모두 명시적인 `FairySession` 안에서만 센싱한다.** `ConsentState.CONFIRMED && FairySession.ACTIVE && AccessibilityService.CONNECTED`일 때만 센싱을 허용한다. 자녀 전용폰인 케이스 A도 설치·서비스 연결만으로 자동 센싱하지 않는다. 케이스 B에서는 세션 밖 부모 사용을 절대 스크리닝하지 않으며, 부모 영역은 PIN 인증과 `LocalDataGate`를 거쳐 허용된 부모용 projection만 읽는다.
5. **A축은 현재 자녀의 공식 연령 기준만 평가한다.** 적용 가능한 공식 등급이 있으면 이를 우선하고 모델 호출을 생략한다. 공식 등급이 없으면 Shieldstral이 현재 threshold에 대해 각 rating dimension의 초과 여부만 binary로 판정한다. 모델이 `7/12/15/18` 등 새 연령등급, 설명문 또는 외부 계약용 confidence를 생성하게 하지 않는다.
6. **확인된 법정대리인 동의 없이는 자녀 데이터 처리를 시작하지 않는다.** 동의 상태는 `UNKNOWN`, `CONFIRMED`, `REVOKED`로 구분한다. 부모의 철회 control event를 받으면 활성 세션 중에도 즉시 게이트를 닫고, 앱 재기동·재연결 시 최신 상태를 확인한다. 완전히 오프라인인 기기에 즉시 철회를 전달할 수 없다는 한계를 숨기지 않는다.
7. **아이에게 향하는 모든 문구는 쉬운 언어**(제22조의2③ 기준)로 작성한다. 비난·훈계·비밀 강요를 금지한다.

> Claude Code에게: 위 불변식과 충돌하는 요구가 들어오면 구현하지 말고 플래그하라. 서버 콘텐츠 분석, 원시 사용기록 백업, 생성 문장의 자녀 노출, 세션 밖 수집, 부모의 child raw store 직접 열람이 대표적인 위반 신호다.

---

## 2. 기술 스택과 책임 분리 (v1, 잠정 — 확정 전 `@ahn` 승인 필요)

| 영역 | 선택/방향 | 계약 |
|---|---|---|
| 자녀 앱 | Android 네이티브 Kotlin + Jetpack Compose | AccessibilityService·UsageStatsManager·로컬 암호화·명시적 session 경계 |
| 부모 앱 | Android 네이티브 Kotlin/Compose (v1) | 페어링·동의·복호화된 digest 표시. 케이스 B 로컬 부모 영역도 PIN 필요 |
| A축 ML | canonical: `mistralai/Shieldstral-1.0-3B`; GGUF + multimodal projector + `llama.cpp` Android 우선 검토 | 일일 batch, screenshot + 최소 metadata, 현재 연령 threshold의 dimension별 yes/no만 소비. 공식 등급 우선 |
| B축 | Kotlin 규칙 엔진 | 콘텐츠와 독립된 사용패턴 판정 |
| Router | 현재 규칙 기반, 향후 ML Router 별도 평가 | A축 모델과 완전 분리. 구조화 intent만 출력 |
| 백엔드 | FastAPI + PostgreSQL (구현 후순위, 현재 API 계약 중심) | auth·consent·pairing·trusted device·rendezvous/connection relay. digest mailbox/history 없음 |
| E2EE | Google Tink HPKE/Hybrid Encryption + 플랫폼 secure storage 우선 검토 | 실제 Android API와 지원 template 검증 후 확정. 자체 X25519/HKDF/AEAD framing을 기본 전제로 두지 않음 |
| 응답 뱅크 | 구조화 JSON 스키마 + 오디오 앱 번들 내장 | 서버 자산/모델 OTA 배포 없음. 앱 업데이트로 갱신 |

> iOS 자녀 앱은 v1 명시적 제외(ScreenTime API 한계로 행위 스크리닝 불가).

제3자 GGUF 저장소는 실험 후보일 뿐 canonical source나 프로덕션 trust anchor가 아니다. 프로덕션 모델은 공식 모델을 기준으로 검증한 변환 artifact와 고정 hash를 사용한다.

---

## 3. 모노레포 구조 (제안)

```text
to-fairy/
├── apps/
│   ├── child-android/      # session·센싱·일일 스크리닝·요정·pending digest
│   └── parent-android/     # 페어링·동의·기기 승인·digest 복호화/표시
├── services/
│   └── backend/            # auth·consent·pairing·trusted device·rendezvous/relay
├── packages/
│   ├── response-bank/      # intent 스키마·검수 대사·오디오
│   └── screening/          # 공식 등급 adapter·정적 policy·모델 artifact 정의
└── docs/
```

응답 뱅크·오디오·모델·screening policy는 앱 번들에 포함하고 앱 업데이트로 갱신한다. 백엔드는 이 자산의 OTA 배포자가 아니다.

---

## 4. 핵심 도메인 개념 (용어 통일)

- **Fairy Session**: 케이스 A/B 모두에서 사용하는 명시적 센싱 수명주기. A는 장시간 유지 가능하지만 반드시 명시적으로 시작·종료한다.
- **A축 (적절성)**: 적용 가능한 공식 등급을 우선하고, 없을 때만 Shieldstral로 현재 자녀 threshold 대비 dimension별 초과 여부를 일일 batch로 판정한다. 실시간 차단기가 아니다.
- **B축 (중독성)**: 세션 길이·전환 속도·취침 전 사용 등 콘텐츠와 무관한 사용패턴 규칙이다.
- **Screening Sample**: checkpoint에서 수집한 screenshot과 최소 metadata. 암호화된 자녀 기기 임시 저장소에만 존재한다.
- **Dimension Assessment**: screenshot 한 장에 대한 dimension별 binary 중간 판정. temporary/ephemeral이며 장기 저장·전송하지 않는다.
- **Daily Screening Aggregate**: 일일 분석이 끝난 뒤 제품 로직에 필요한 최소 집계. dimension별 상세 콘텐츠 성격을 그대로 보존하지 않는다.
- **Router**: 상황에서 검수된 발화 intent를 선택하는 독립 컴포넌트. A축 모델과 모델·입력·출력 계약을 공유하지 않는다.
- **Context Digest**: `PeriodAggregate`, `RelationshipSnapshot`, 사전 정의 highlight로 구성한 부모용 산출물. 원시 콘텐츠나 세부 판정을 포함하지 않는다.
- **Pending Digest**: 자녀 기기의 암호화된 로컬 저장소에서 ACK 전까지 유지하는 E2EE ciphertext와 delivery state다.
- **Relay Tunnel**: 백엔드가 연결과 routing만 중계하는 통로. 백엔드는 digest mailbox나 history를 보관하지 않는다.
- **Local Data Gate**: 케이스 B에서 child session domain으로부터 허용된 `ContextDigest`/parent projection만 parent domain으로 통과시키는 경계다.
- **Parent Gate**: `ParentAuthState`와 `ParentModeSession`으로 부모 모드·설정·digest 접근 전에 PIN 인증을 요구하는 경계다.

---

## 5. E2EE 보안 목표와 비목표

E2EE의 핵심 목표는 **content confidentiality**다. 보호 대상은 `ContextDigest` 평문, 자녀 사용 집계, 관계 상태 snapshot, 부모에게 전달되는 요약 맥락이다. 서버는 자녀 콘텐츠 원문·스크린샷·세부 스크리닝 결과·관계 상태 내용·digest 평문을 볼 수 없어야 한다.

서버는 서비스 운영을 위해 부모 계정, child/parent device identity, pairing metadata, routing identifier/token, public key, 연결 시각, digest ID, 전송/ACK 상태와 인증 metadata를 처리할 수 있다. 따라서 다음을 보장한다고 주장하지 않는다.

- 서버에 대한 익명성 또는 unlinkability
- 트래픽 분석 방지
- metadata confidentiality
- 장기 private key 탈취 뒤 모든 과거 메시지까지 보호하는 강한 forward secrecy

HPKE의 ephemeral sender key를 사용하더라도 그 목적은 메시지별 키 생성·encapsulation과 장기 송신 private key 의존 최소화다. 발신자 추적 방지 수단으로 설명하지 않는다.

각 부모 기기는 자체 keypair를 secure storage에 생성한다. 신규 부모 기기는 pending public key를 등록하고 기존 trusted parent device의 명시적 승인을 받아 `TrustedDeviceSet`에 들어간다. private key export/import를 기본 흐름으로 사용하지 않으며 device revoke를 지원한다. 모든 기존 신뢰 기기를 잃은 경우의 복구와 digest fan-out 정책은 Open Issue다.

---

## 6. 컴플라이언스 훅 (코드가 법과 만나는 지점)

기능을 구현할 때 아래 지점은 반드시 대응 코드를 동반한다. 근거는 `../To_Fairy_제품_메커니즘_설계.md` §8이다.

- **센싱 진입 전** → `ConsentState.CONFIRMED && FairySession.ACTIVE && AccessibilityService.CONNECTED` 검증. 케이스 A/B 예외 없음.
- **동의 철회 수신** → 활성 session 여부와 무관하게 즉시 센싱 게이트 폐쇄. 앱 시작·재연결 시 최신 상태 조회.
- **sample 저장 전** → 최소 metadata만 포함하고 encrypted temporary store만 사용. URL·검색어·화면 전체 텍스트를 별도 필드로 보존하지 않음.
- **일일 A축 완료 후** → aggregate 생성이 정상 완료된 sample의 screenshot 원본과 dimension assessment 폐기. 분석 실패 sample은 retry 정책 범위에서만 유지.
- **다이제스트 생성** → 기간 집계와 누적 관계 snapshot을 분리하고 arbitrary string highlight·원시 콘텐츠·dimension 결과 포함 금지.
- **다이제스트 송신 전** → E2EE 봉인 후 child `PendingDigestStore`에 저장. 부모 ACK 전 삭제 금지, ACK 후 pending digest와 해당 source aggregate 정리.
- **케이스 B 부모 영역 진입** → PIN 인증과 `LocalDataGate` 강제. child raw store·screening state·전체 `RelationshipState` 직접 접근 금지.
- **아이 대상 고지 화면** → 쉬운 언어 카피 + 투명성 표시("요정이 ~라고 전했어").

---

## 7. 코딩 컨벤션 (초안)

- 주석·문서는 한국어, 코드 식별자는 영어.
- 자녀 데이터를 다루는 클래스/함수에는 `// INVARIANT:` 주석으로 어떤 경계를 지키는지 명시한다.
- `Ephemeral*`은 메모리 전용·소비 후 폐기 타입에만 사용한다. 일일 분석을 위해 제한적으로 보존하는 raw sample은 `ScreeningSample`/`EncryptedLocalImage`와 전용 encrypted temporary store로 분리한다.
- `ScreeningSample`과 `DimensionAssessment`는 digest·API DTO로 변환할 수 없게 의존 방향을 제한한다.
- 평문 `ContextDigest`는 기기 내부 builder/sealer 경계까지만 허용한다. 네트워크 모듈은 E2EE ciphertext를 담은 relay envelope만 받을 수 있다.
- highlight는 enum/sealed code만 허용하고 자유 문자열을 금지한다.
- confidence는 외부 도메인 모델, DB, digest, 서버 payload, 로그, 부모 UI에 넣지 않는다.
- delivery/ACK는 `digestId`를 기준으로 중복 안전(idempotent)해야 한다.

---

## 8. 문서 맵

- `../To_Fairy_제품_메커니즘_설계.md` — 제품 기획 (source of truth)
- `docs/00_아키텍처_개요.md` — 시스템 아키텍처·신뢰 경계·Open Issues
- `apps/child-android/CLAUDE.md` — 자녀 앱 구현 계약
- `docs/10_자녀앱_설계.md` — 예정 (상세 화면·상태 흐름)
- `docs/11_부모앱_설계.md` — 예정
- `docs/20_스크리닝_엔진.md` — A축 일일 batch·Shieldstral·sampling·privacy 계약
- `docs/30_대화_응답뱅크.md` — 예정 (intent 스키마·Router·오디오 파이프라인)
- `docs/40_백엔드_API.md` — auth·consent·pairing·trusted device·relay/control API 계약
- `docs/50_데이터_프라이버시_구현.md` — E2EE와 로컬 데이터 수명주기의 normative 문서
- `docs/60_로드맵_마일스톤.md` — 예정

Open Issues는 `docs/00_아키텍처_개요.md` §12와 각 상세 문서에서 관리한다. 특히 consent TTL/signed lease, trusted device 상실 복구, relay transport, biometric, rating source 우선순위, 모델 threshold·sampling·retention·dimension·정책·quantization·저사양 지원 범위를 임의로 확정하지 않는다.
