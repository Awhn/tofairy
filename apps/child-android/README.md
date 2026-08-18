# To Fairy — child-android

자녀(만 7~9세)용 네이티브 앱. 온디바이스 센싱 → 판단 → 요정과의 관계형 상호작용,
부모에게는 허용된 맥락 다이제스트만 내보낸다. 기기 간 전달은 E2EE이며, 공유 기기의
로컬 부모 영역은 PIN과 Local Data Gate를 거친다. 거의 모든 처리가 기기 안에서 끝난다.

개발 가이드·불변식은 [`CLAUDE.md`](./CLAUDE.md) 참조.

## 빠르게 보기 (이 골격이 하는 것)
- **온보딩 각성 의식**: 요정이 깨어나고 → 아이가 이름을 지어주고 → 유대 → 보호자 동의 게이트(#6).
- **살아있는 요정 홈**: 부드러운 호흡/부유/반짝임 애니메이션. 톡 건드리면 인사.
- **개입 데모 칩**: "오래 봤어요/하루 점검 결과/약속 지켰어요" → 규칙 라우터가 intent 를 만들고
  응답뱅크가 대사를 골라 요정이 '말한다'. (아이가 듣는 문장은 전량 응답뱅크에서 선택 — 불변식 #3)

## 빌드 / 테스트
Android SDK 가 필요합니다 (`ANDROID_HOME` 설정 + `local.properties` 의 `sdk.dir`).

```bash
./gradlew :app:assembleDebug        # 디버그 APK
./gradlew :app:testDebugUnitTest    # JVM 단위 테스트 (불변식/라우터/규칙/응답뱅크)
```

`local.properties` 예시:
```
sdk.dir=/path/to/Android/Sdk
```

## 구조
`app/src/main/java/app/tofairy/child/` 아래 모듈별 패키지:
`core`(ephemeral·intent), `sensing`, `screening/axisa`·`screening/axisb`, `router`,
`responsebank`, `fairy`, `onboarding`, `localstore`, `digest`, `parentgate`, `session`, `apiclient`, `ui`.

## 현재 상태
앱 골격(Compose 데모 + 계약 타입/interface/mock). 자세한 진척은 `CLAUDE.md` §12.
온디바이스 A축(Shieldstral 일일 batch)과 향후 별도 ML Router는 모두 미배선 —
각각 `BuildConfig.AXIS_A_SCREENING_ENABLED`, `BuildConfig.ML_ROUTER_ENABLED`로 독립되어 있어
모델 없이도 빌드·실행된다.
