# responsebank/audio

사전 녹음된 요정 음성 클립이 들어가는 곳입니다 (CLAUDE.md §7).

- 성우 녹음 + 클론(`../../../../../../To_Fairy_제품_메커니즘_설계.md` §10.3) 으로 제작한 클립.
- `dialogue.json` 의 각 라인 `audioClip` 값이 이 디렉터리의 파일명(확장자 제외)에 대응합니다.
  예) `audioClip: "greet_morning_1"` → `greet_morning_1.ogg`
- 온디바이스 재생, 지연 0. OTA 없음 — 갱신은 앱 업데이트로만.

골격 단계에서는 실제 오디오 자산이 없으며,
`responsebank.AudioPlayer` 의 기본 구현이 클립 누락을 무해하게 처리합니다(텍스트 버블만 노출).
