# To Fairy

자녀(만 7~9세)가 '핸드폰 요정'과 함께 자기 핸드폰 사용을 돌보는 **관계형 동반자**.
스크리닝 신호는 자녀 폰 안(on-device)에 머물고, 부모에게는 '무엇을 봤는지'가 아니라 **요약된 맥락**만 전달된다. 기기 간 전달은 E2EE이고, 공유 기기의 로컬 부모 영역은 PIN과 Local Data Gate를 거친다.

> 개발 최상위 지침과 절대 불변식은 [`CLAUDE.md`](./CLAUDE.md). 시스템 설계는 [`docs/00_아키텍처_개요.md`](./docs/00_아키텍처_개요.md).

## 모노레포 구조
```
to-fairy/
├── apps/
│   ├── child-android/      # 자녀 앱 (구현 시작됨 — 앱 골격 + 살아있는 요정 데모)
│   └── parent-android/     # 부모 앱 (예정)
├── services/
│   └── backend/            # FastAPI (구현 후순위, 현재 API 명세만 → docs/40)
├── packages/
│   ├── response-bank/      # intent 스키마·대사·오디오 (앱 번들 내장)
│   └── screening/          # 공식 등급 adapter·Shieldstral 정책/자산·신호 규칙
├── docs/                   # 개발 문서
└── index.html              # legacy 시각 목업(비규범·screening/auth 동작 없음)
```

## 현재 진척
- **`apps/child-android`** — 앱 골격 구현 시작. Compose 요정 UI·온보딩 각성 의식·응답뱅크 소비·규칙
  라우터/규칙 엔진·ephemeral 타입·센싱 게이트·세션 경계·암호화 localstore·digest 파이프라인·mock apiclient.
  자세한 내용은 [`apps/child-android/README.md`](./apps/child-android/README.md) 및 그 안의 `CLAUDE.md`.

> `index.html`은 초기 화면 배치만 보존한 legacy 목업이다. 실시간 A축 차단, 부모 인증,
> 응답 생성 또는 데이터 처리 동작을 나타내지 않으며 제품 계약의 근거로 사용하지 않는다.

## 문서
- [`CLAUDE.md`](./CLAUDE.md) — 개발 가이드 · 7개 절대 불변식
- [`docs/00_아키텍처_개요.md`](./docs/00_아키텍처_개요.md) — 시스템 아키텍처
- [`docs/20_스크리닝_엔진.md`](./docs/20_스크리닝_엔진.md) — 일일 A축·Shieldstral·sampling 계약
- [`docs/40_백엔드_API.md`](./docs/40_백엔드_API.md) — 백엔드 API 계약(구현 후순위)
- [`docs/50_데이터_프라이버시_구현.md`](./docs/50_데이터_프라이버시_구현.md) — E2EE·프라이버시 구현
