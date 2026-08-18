# CLAUDE.md — child-android (자녀용 네이티브 앱)

앱 scope 개발 가이드. 상위 규칙은 루트 `../../CLAUDE.md`, 제품 출처는 `../../To_Fairy_제품_메커니즘_설계.md`다. 충돌 시 우선순위는 루트 불변식 > 제품 기획 > 본 문서다.

## 0. 이 앱이 하는 일

자녀(만 7~9세)가 사용하는 **명시적 `FairySession`** 안에서 sensing → 온디바이스 판단 → 요정과의 관계형 상호작용을 수행한다. 케이스 A(자녀 전용폰)와 케이스 B(공유폰) 모두 session을 요구한다. 부모에게는 원시 자료가 아니라 허용된 집계로 만든 `ContextDigest`만 전달한다. 기기 간 경로는 E2EE를 사용하고, 공유 기기 로컬 경로는 PIN 인증과 `LocalDataGate`를 사용한다.

## 1. 앱 불변식

- **Raw screening material은 device-local only다.** screenshot, 화면 원문, URL·검색어·영상명, 세부 사용 로그, screenshot별 dimension 결과는 backend 또는 parent domain으로 보내지 않는다.
- **일일 A축 sample에는 제한적 저장 예외가 있다.** 최소 screenshot + metadata는 encrypted temporary store에 보존할 수 있고, 일일 분석과 집계가 정상 완료되면 삭제한다. 메모리 전용 activity signal은 소비 직후 폐기한다.
- **A축 중간 판정은 장기 데이터가 아니다.** `DimensionAssessment`는 DB·로그·digest·server payload·parent UI에 넣지 않는다. 필요한 최소 `DailyScreeningAggregate`만 남긴다.
- **요정 발화는 전량 response bank에서 고른다.** 모델 자유 텍스트를 아이에게 보여주거나 들려주지 않는다.
- **A축과 Router는 완전히 독립이다.** A축은 Shieldstral 일일 batch, B축은 rule engine, Router는 현재 rule-based다. 향후 ML Router도 A축 모델과 별도로 평가한다.
- **A/B 모두 sensing 3조건을 지킨다.** `ConsentState.CONFIRMED && FairySessionState.ACTIVE && service connected`일 때만 허용한다. 설치·권한·서비스 연결만으로 자동 sensing하지 않는다.
- **consent revoke는 활성 session보다 우선한다.** control event 수신 즉시 gate를 닫고 앱 시작·재연결 시 최신 상태를 확인한다.
- **케이스 B 부모 영역은 `ParentGate`와 Local Data Gate 뒤에 둔다.** PIN 인증 전에는 설정·digest 접근을 거부하고 child raw store를 parent domain에 노출하지 않는다.
- **아이 대상 문구는 쉬운 언어를 쓴다.** 비난·훈계·비밀 강요를 금지한다.

다음 요청은 구현하지 말고 상위 불변식 충돌로 플래그한다: 서버 콘텐츠 분석, screenshot/dimension 결과 송신, raw 사용 기록 백업, 모델 생성 문장의 child-facing 노출, session 밖 background 수집, 부모 UI의 child raw store 직접 조회.

## 2. 기술 구성

- 언어/UI: Kotlin + Jetpack Compose
- A축 ML: canonical `mistralai/Shieldstral-1.0-3B`; 검증된 hash-fixed GGUF + multimodal projector와 `llama.cpp` Android integration을 우선 검토한다. 아직 runtime/API/artifact를 확정하지 않는다.
- B축: deterministic Kotlin rule engine
- Router: 현재 `RuleBasedRouter`; 미래 ML Router는 별도 interface/benchmark로 검토
- E2EE: Google Tink HPKE/Hybrid Encryption을 우선 검토하고 key material은 Android Keystore/StrongBox 등 platform secure storage에 둔다. 실제 Tink Android API/template 검증 전 가상의 API를 구현 계약으로 만들지 않는다.
- local encryption: raw sample, 관계 상태, aggregate, pending digest를 목적별 encrypted store에 보관
- backend 연동: `../../docs/40_백엔드_API.md` 계약 소비. backend는 auth/consent/pairing/trusted-device/rendezvous/relay 역할만 하며 digest mailbox가 아니다.

제3자 GGUF repo는 테스트 후보일 뿐 프로덕션 신뢰 기준이 아니다. 상세 A축 계약과 benchmark matrix는 `../../docs/20_스크리닝_엔진.md`를 따른다.

## 3. 패키지/책임 구조

```text
app.tofairy.child/
├── sensing/           # fail-closed SensingGate + AccessibilityService/control wiring
├── session/           # A/B 공통 FairySession identity/state/lifecycle
├── screening/
│   ├── axisa/         # sampler·temporary sample store·공식 등급·Shieldstral daily job
│   └── axisb/         # 사용패턴 rule engine
├── router/            # 상황 → structured intent (현재 rule-based)
├── responsebank/      # intent → 검수 대사·오디오
├── fairy/             # 요정 UI·상호작용
├── onboarding/        # 각성·이름짓기·동의 시작 UX
├── localstore/        # 관계·aggregate·pending digest·PIN credential 저장 경계
├── digest/            # digest build·seal·delivery state·ACK/purge
├── parentgate/        # 케이스 B ParentGate·parent-mode capability·digest projection
└── apiclient/         # consent/control·pairing·trusted device·relay 계약
```

현재는 위 책임을 단일 `app` Gradle module 안의 package로 둘 수 있다. 구조가 안정되기 전 불필요하게 module을 쪼개지 않되, 다음 의존 방향은 지킨다.

```text
screening raw types ─X─▶ apiclient / parentgate / digest DTO
parent domain       ─X─▶ child raw stores
apiclient           ─X─▶ plaintext ContextDigest
axis_a              ─X─▶ Router implementation
```

## 4. Session·Sensing·Consent 계약

### 4.1 명시적 session

- `FairyDeviceMode.DEDICATED_CHILD_DEVICE`: 케이스 A. session을 장시간 유지할 수 있으나 명시적으로 시작·종료한다.
- `FairyDeviceMode.SHARED_PARENT_CHILD_DEVICE`: 케이스 B. session이 자녀/부모 사용의 강한 데이터 경계다.
- `FairySessionIdentity(sessionId, deviceMode)`로 session을 식별한다.
- `FairySessionState.INACTIVE`와 `ACTIVE`를 명시적으로 전이한다.
- A/B 어느 mode도 service 연결만으로 `ACTIVE`가 되지 않는다.

### 4.2 fail-closed gate

```text
ConsentState.CONFIRMED
AND FairySessionState.ACTIVE
AND AccessibilityService connected
→ SensingGate open
```

`ConsentState.UNKNOWN` 또는 `REVOKED`, inactive session, disconnected service 중 하나라도 있으면 gate는 닫힌다. gate가 닫힌 동안 `SensingAccessibilityService`는 raw event를 `SignalSink`로 전달하지 않는다. session 종료, revoke, service disconnect는 이미 열려 있던 sink/capability도 무효화해야 한다.

케이스 A/B를 구분하는 mode는 정책 설명과 UI에 쓰되, session 요구 자체를 우회하는 조건으로 사용하지 않는다.

### 4.3 consent reverse propagation

- 상태 타입: `ConsentState.UNKNOWN`, `CONFIRMED`, `REVOKED`
- control abstraction: `ConsentUpdate`, `ConsentControlChannel`, `ConsentControlListener`, `ConsentSynchronizer`
- 부모 철회 → backend `REVOKED` → control/wake-up/active relay signal → child synchronizer → gate close
- `ConsentStatus`/`ConsentUpdate`의 monotonic server `revision`으로 순서를 정하고 동일 revision 충돌에서는 `REVOKED`를 우선한다. `updatedAt`만으로 ordering하지 않는다.
- 앱 기동과 relay 재연결 시 최신 consent state 조회
- control/relay 연결 단절 시 이전 `CONFIRMED`를 재사용하지 않고 `UNKNOWN`으로 닫은 뒤 authenticated event/status로 freshness 회복
- 완전히 offline인 child에는 즉시 철회가 전달되지 않는 한계가 있음
- TTL/signed lease는 필수 구조가 아니며 Open Issue

control/push payload에는 consent control 정보만 넣고 screenshot이나 digest 본문을 넣지 않는다.

## 5. A축 일일 screening 계약

### 5.1 입력과 sampling

기본 입력은 Accessibility node text 단독이 아니라 encrypted local screenshot과 최소 metadata다.

```kotlin
data class ScreeningSample(
    val sampleId: String,
    val screenshot: EncryptedLocalImage,
    val capturedAt: Long,
    val metadata: ScreeningMetadata,
    val state: ScreeningSampleState = ScreeningSampleState.ENCRYPTED_LOCAL,
)

interface ScreeningSampler {
    fun shouldCapture(context: SamplingContext): Boolean
}
```

metadata 후보는 timestamp, session identifier, foreground app/package, session duration, content/app category(가용한 경우), sampling reason, screen transition context다. 실제 목적에 불필요한 URL·검색어·화면 전체 text는 저장하지 않는다. package name도 제품상 필요성을 검증하기 전 무조건 보존하지 않는다.

capture trigger 후보는 새 앱/콘텐츠 진입, session 시작, 의미 있는 화면 변화, 일정 시간 동일 콘텐츠 체류, 장시간 session의 주기 checkpoint다. 모든 화면을 연속 capture하지 않는다. 하루 약 10~30장은 실험 범위이며 sampler threshold·interval을 hard-code하지 않는다.

### 5.2 공식 등급 우선

```text
applicable official rating exists
  → compare with current child threshold
  → skip Shieldstral

no applicable official rating
  → screenshot + minimal metadata
  → Shieldstral current-threshold screening
```

rating source의 신뢰 순서, 앱 전체 등급과 내부 콘텐츠 등급 충돌, 사용자 생성 콘텐츠와 국가별 mapping은 Open Issue다.

### 5.3 Shieldstral 결과 계약

- 현재 자녀의 하나의 threshold만 검사한다. 모든 연령 경계를 반복 추론하지 않는다.
- 모델이 새 연령등급을 생성하지 않는다.
- 고정된 공식 정책을 dimension별로 적용하고 첫 yes/no classification 결과만 소비한다.
- 후보 dimension: `VIOLENCE`, `SEXUAL_CONTENT`, `FEAR_OR_THREAT`, `LANGUAGE`, `DANGEROUS_OR_IMITABLE_BEHAVIOR`, `SUBSTANCE`.
- 하나 이상 threshold 초과 → `AgeSuitability.EXCEEDS_AGE_THRESHOLD`; 모두 기준 안 → `WITHIN_AGE_THRESHOLD`.
- 자유 텍스트 설명을 도메인 결과로 소비하지 않는다.
- confidence/logit/probability는 내부 runtime 판단에 필요할 수 있으나 DB, domain DTO, digest, network, log, parent UI에 넣지 않는다. threshold는 benchmark 전 확정하지 않는다.

dimension별 실제 policy text는 적용 국가의 공식 콘텐츠 등급 기준을 근거로 작성하고 bundle static asset으로 관리한다. 임의 policy를 제품 코드에 작성하지 않는다.

### 5.4 batch 수명주기

```text
CAPTURED
→ ENCRYPTED_LOCAL
→ SCREENING
→ SCREENED
→ AGGREGATED
→ RAW_SAMPLE_PURGED
```

`CAPTURED`는 암호화 저장 전의 순간적 lifecycle event이며 persisted `ScreeningSampleState`는 `ENCRYPTED_LOCAL`부터 시작한다. `RAW_SAMPLE_PURGED`는 record가 삭제된 상태라 enum에 남기지 않는다.

일일 job은 충전 중, 화면 꺼짐, 유휴 시간, 지정 batch 시간 등을 후보 실행 조건으로 삼는다. 실시간 latency를 목표로 하지 않는다. aggregate 생성까지 정상 완료된 sample만 삭제한다. 추론/집계 실패 시 retry를 위해 sample을 당장 잘못 삭제하지 않되 보존 기간은 Open Issue다. screenshot purge는 digest ACK와 연결하지 않는다.

```kotlin
data class DimensionAssessment(
    val dimension: RatingDimension,
    val exceedsThreshold: Boolean,
)

data class DailyScreeningAggregate(
    val batchId: String,
    val screenedSampleCount: Int,
    val thresholdExceededSampleCount: Int,
    val interventionNeeded: Boolean,
)
```

`DimensionAssessment`는 temporary/ephemeral scope이고 `DailyScreeningAggregate`만 필요한 제품 로직에 전달한다. `batchId`는 재시도 시 동일 aggregate commit을 멱등 처리하는 비콘텐츠 ID다. aggregate commit 뒤 sample 전체의 `AGGREGATED` 상태를 원자적으로 확정한 다음 raw를 삭제하고, 부분 purge 실패 재시도에서는 재집계하지 않는다. dimension별 상세 count의 장기 저장은 현재 결정하지 않으며 기본 방향은 저장하지 않는 것이다.

## 6. Router와 response bank

```text
A-axis → Shieldstral multimodal daily screening
B-axis → deterministic rule engine
Router → current RuleBasedRouter; future model evaluated separately
```

Router는 A축 prompt, runtime, model artifact, confidence 또는 dimension assessment를 공유하지 않는다. Router의 유일한 출력은 response bank가 이해하는 structured intent다. 아이에게 향하는 문자열과 audio는 번들에 포함된 검수 자산에서만 선택한다. 응답 뱅크·오디오·model/policy는 서버 OTA 없이 앱 업데이트로 갱신한다.

## 7. Digest·E2EE·relay 계약

- `ContextDigest`는 `digestId`, 기간, schema version, `PeriodAggregate`, `RelationshipSnapshot`, 사전 정의 `DigestHighlight`만 포함한다.
- 기간 발생량과 누적 관계 상태를 한 필드에 섞지 않는다.
- arbitrary string highlight와 raw/dimension type의 변환 경로를 만들지 않는다.
- 기기 간 네트워크 경로의 평문 digest는 child builder/sealer 경계를 벗어나지 않는다. 케이스 B의 같은 기기 전달은 PIN-authenticated Local Data Gate가 허용한 parent projection만 예외다.
- Google Tink HPKE/Hybrid Encryption을 기본 후보로 우선 검토하되 실제 Android 지원을 검증한 뒤 구체 template/wire format을 정한다.
- E2EE 목표는 content confidentiality다. 서버 익명성, unlinkability, traffic-analysis 방지, metadata confidentiality, 강한 forward secrecy를 주장하지 않는다.
- ephemeral sender key는 메시지별 key/HPKE encapsulation 용도이며 발신자 추적 방지 수단이 아니다.

delivery lifecycle:

```text
CREATED → SEALED → WAITING_FOR_PARENT → SENT → ACKED → PURGED
```

`CREATED`는 봉인 전 lifecycle이고, `PURGED`는 pending record가 삭제된 상태다. persisted `PendingDigest`는 ciphertext가 생긴 `SEALED`부터 저장하며 `PURGED`를 `DeliveryState` enum에 남기지 않는다.

child는 encrypted `PendingDigestStore`에 ACK 전까지 보존한다. 봉인 호출은 선택한 trusted parent device의 recipient public key를 명시적으로 받아야 하며 pending delivery는 `(digestId, recipientDeviceId)`로 구분한다. 이 복합 키는 single recipient와 향후 fan-out 양쪽을 표현할 뿐 fan-out 정책을 확정하지 않는다. backend는 rendezvous/byte relay만 하고 ciphertext mailbox/history를 보관하지 않는다. parent가 offline이면 child가 재전송한다. parent는 digest 저장과 processed `digestId` 기록을 원자적으로 처리해 중복 수신을 막는다. ACK는 인증된 parent relay peer에서 온 것이고 pending recipient와 일치할 때만 수락하며 idempotent해야 한다. 전송 성공 뒤 `SENT` 기록 전에 ACK가 도착한 race에서는 원자적 상태 전이로 `WAITING_FOR_PARENT → ACKED`를 허용한다. ACK 이후 해당 pending delivery를 삭제하고, source aggregate 정리는 추후 확정될 recipient 완료 정책을 적용한다.

## 8. 케이스 B Parent Gate

- `ParentGate`는 `ParentAuthState.LOCKED`/`AUTHENTICATED`를 관리한다.
- `ParentModeSession`은 성공한 인증 뒤 제한된 capability를 발급한다.
- `SharedDeviceChildModeBoundary`는 PIN 검증 전에 active child session을 끝내 부모 모드와 sensing이 동시에 활성화되지 않게 한다.
- `ParentPinCredentialStore`는 PIN verifier를 secure local storage에 보관하고 verification operation만 노출한다. plaintext PIN 조회 API를 만들지 않는다.
- `ParentDigestSource`는 허용된 `ContextDigest`/parent projection만 반환한다.
- 접근 결과는 `ParentDataAccess.Granted`/`Denied`처럼 명시적으로 표현한다.
- child mode에서 parent mode로 단순 flag 전환하지 않는다.
- parent settings와 digest 조회 모두 유효한 `ParentModeSession` capability와 PIN 인증을 요구한다.
- biometric은 교체 가능한 abstraction만 고려하고 구현은 Open Issue다.

parent domain에서는 `EphemeralSignal`, screenshot, screening state, dimension assessment, 전체 `RelationshipState`, child raw-store handle을 얻을 수 없어야 한다.

## 9. Android 구성과 권한

- AccessibilityService: session/consent gate 뒤에서 필요한 activity·screen transition 신호와 sampling checkpoint를 제공한다.
- Foreground Service: 케이스 A의 장시간 active session 또는 케이스 B active session을 표시할 수 있으나 스스로 session을 생성하지 않는다.
- UsageStatsManager: `PACKAGE_USAGE_STATS` 특수 권한이 있어도 session 밖 event는 pipeline에 진입시키지 않는다.
- screenshot capture: Android가 허용하는 명시적 API/권한 범위 안에서만 수행하고, capture 결과는 즉시 encrypted temporary store 경계로 넘긴다.
- DevicePolicyManager(케이스 A, 선택): 권한 회수 방지 수준은 Open Issue다.
- 위치·연락처·메시지·카메라 권한은 이 설계의 screenshot capture에 필요하지 않으므로 요청하지 않는다.

## 10. API client 경계

`apiclient`가 소비하는 책임은 다음으로 제한한다.

- 최신 consent state 조회와 control event 수신
- pairing·parent device public-key/trust set 동기화
- child-parent relay 연결과 opaque ciphertext/ACK byte 전달
- optional wake-up/control notification

금지 API:

- screenshot/화면 text/dimension assessment upload
- plaintext `ContextDigest` upload
- server digest mailbox list/poll/delete
- push payload 안 digest body

서버는 계정, pairing, device identity, public key, routing/connection, digest ID와 delivery/ACK metadata를 처리할 수 있다. “서버는 암호문만 본다”를 계정·routing metadata까지 숨긴다는 의미로 사용하지 않는다.

## 11. 빌드·검증

- Gradle Kotlin DSL + version catalog, `minSdk = 26`, `compileSdk/targetSdk = 35`를 현재 기준으로 삼는다.
- A축 runtime과 향후 ML Router는 서로 다른 feature flag/adapter 뒤에 두며, 모델 artifact 없이도 앱 골격과 unit test가 빌드되어야 한다.
- 빌드: `./gradlew :app:assembleDebug`
- 단위 테스트: `./gradlew :app:testDebugUnitTest`
- Android SDK가 필요한 lint/instrumentation 결과와 pure JVM unit test 결과를 구분해 보고한다.

최소 계약 테스트:

- A/B 모두 consent+session+service 3조건 필요, active 중 revoke 즉시 close
- official rating이 있으면 Shieldstral 미호출
- current threshold만 dimension별 평가
- daily success 후 raw sample purge, failure 시 잘못 purge하지 않음
- screenshot/dimension 타입이 digest/network payload로 진입하지 않음
- enum highlight만 허용
- duplicate `digestId` 미중복 처리, ACK idempotency, ACK 전 pending 유지/ACK 후 purge
- PIN 인증 전 parent access 거부, parent domain의 child raw store 접근 불가

## 12. 구현 상태를 표현하는 규칙

문서에서 다음 상태를 구분한다.

- **설계 계약**: 이 문서가 요구하지만 production adapter/wiring이 아직 없을 수 있음
- **골격 구현**: 타입/interface/fake/test가 있으나 실제 platform·network·crypto·ML integration은 없음
- **실동작 구현**: Android API/runtime/backend와 연결되고 device test를 통과함

현재 Compose UI, onboarding, response-bank 선택, rule-based Router와 B축 일부는 데모/골격으로 존재한다. Shieldstral runtime, encrypted screenshot store와 scheduler, official-rating adapter, Tink HPKE sealer, persistent pending store, relay transport, control push, production PIN UX는 각각 실동작 검증 전까지 “완료”로 표시하지 않는다.

## 변경 이력

- v0.2-contract: A/B 공통 explicit session, typed consent/control, encrypted temporary screenshot batch, Shieldstral current-threshold dimensions, Router 분리, Local Data Gate/PIN, relay/no-mailbox, ACK lifecycle, content-confidentiality E2EE 계약 반영
- v0.1-impl: Compose UI·온보딩·response-bank 소비·rule Router/B축·기초 sensing/session/localstore/digest/mock client 골격
- v0.1: 초기 child app 개발 가이드
