# CarBeat 개발 체크리스트

완료한 항목은 `[ ]`를 `[x]`로 바꾼다. 단계마다 **통과 기준**을 만족해야 다음 단계로 간다. 못 미치면 측정값과 원인을 기록한다.
근거: `.private/카비트_개발설계.md` (비공개). 목표: 11월 15일 제출 전까지 에뮬레이터에서 동작.

- applicationId / namespace: `com.dontstudyy.carbeat`
- 환경: `C:\pleosEnv` (`start-android-studio.bat`로 Android Studio 실행)

## 결정 필요

- [x] 와이퍼 반응 기본값: **기본 꺼짐**(기획서 기준). 시연·영상에서는 설정에서 켠 상태로 촬영 (2026-11-12 결정)
- [x] 접수양식 수정: 방향지시등 → 핸들, 조용한 모드·TurnSignal 삭제 (`기획 v2/접수양식_5_카비트.docx`, `기획서_5개_복붙용.md`) (2026-11-12)
- [ ] 접수양식 최종 검토 후 Word에서 열어 서식 확인 (사용자)

## 준비: 프로젝트 뼈대

- [x] `HYU_Challange` probe 프로젝트에서 빌드 설정 복사 (Gradle 9.6, compileSdk 37, minSdk 28, Pleos Nexus 저장소, Vehicle 2.3.2)
- [x] `namespace` / `applicationId` = `com.dontstudyy.carbeat`, `rootProject.name` 변경
- [x] 매니페스트 권한 선언 가져오기 (`CAR_DRIVE`, `CAR_LIGHTS`, `CAR_WINDOWS`, `CAR_ENERGY`, `android.car.permission.CAR_POWERTRAIN`) — CONTROL_CAR_LIGHTS·CONTROL_CAR_WINDOWS는 probe 매니페스트에서 확인한 이름
- [~] 제어용 권한 이름은 probe 매니페스트(CONTROL_CAR_LIGHTS, CONTROL_CAR_WINDOWS)에서 가져옴. SDK 쪽 재확인은 단계 4 전
- [x] `.private/카비트_개발자료/songs`의 곡 3개(ogg·json)를 `app/src/main/assets/songs/`로 복사 (`.private`는 공개되지 않으므로 빌드에 필요한 파일은 앱 폴더로)
- [~] 빈 앱 CLI 빌드(assembleDebug)·단위 테스트 통과. 에뮬레이터 실행은 사용자 확인 필요

## 단계 0. 확인 실험

통과 기준: 측정값을 README '검증 기록'에 기록. 핸들 흔들림이 ±180ms 안.

- [x] 핸들 임계값 통과 시각의 흔들림 20회 측정: 중앙값에서 최대 133ms (통과 기준 ±180ms 안). 부호 반대(전송 +30 → 앱 -30) 확인
- [~] AAOS `PARKING_BRAKE_ON`(0x11200402): 읽기·구독 가능, 그러나 에뮬레이터에서 항상 false(텔테일 시뮬레이터로도 안 바뀜) → 실행 조건에서 뺄지 사용자 결정 필요
- [x] Virtual Console 와이퍼 주소: `PUT /vehicle/body?front_wiper=Off|Auto|Low|High`
- [x] 앰비언트 0.5초 간격 10회 쓰기: 10/10 성공, 임의 ARGB 적용, 원복 확인
- [x] 화면 해상도·비율 확인: 앱 창 1682x1210dp (가로 약 1.39:1)
- [~] 브레이크 왕복·일부 값만 PUT 확인(exp_input.py). `tools/key_bridge.py` 본체는 단계 3

## 단계 1. 뼈대

통과 기준: 기어를 D로 바꾸면 어느 화면에서든 1초 안에 숨김 화면.

- [ ] `vehicle` 층: Vehicle SDK / `CarPropertyManager` / `CarUxRestrictionsManager` 초기화, 리스너 등록·해제
- [ ] `gate` 주차 게이트: 기어 P + 주행 규제 해제(+ 읽을 수 있으면 주차 브레이크)
- [ ] 화면 7개 이동 뼈대 (홈 / 실행 조건 / 박자 보정 / 플레이 / 결과 / 주행 중 숨김 / 튜토리얼)
- [ ] 기어가 P를 벗어나면 즉시 "주행 중에는 숨겨져요" 화면
- [ ] 첫 커밋 (뼈대)

## 단계 2. 게임 엔진

통과 기준: 3곡이 끝까지 재생되고, 노트가 판정선에 닿는 순간과 소리가 맞음.

- [ ] `audio`: Media3 ExoPlayer 곡 재생, 판정 기준 시각 = 재생 위치
- [ ] `game`: 노트 JSON 읽기 (`assets/songs/*.json`)
- [ ] 판정: 브레이크·와이퍼 ±60/±120ms, 핸들 ±100/±180ms
- [ ] 콤보, 점수 (PERFECT 1000, GOOD 600, 롱노트 최대 +1500)
- [ ] 롱노트 (끝 100ms 전까지 유지)
- [ ] 레인 Canvas 렌더링, 판정 연출 (터짐 / 회색 낙하 + 흔들림)
- [ ] 디버그 빌드 전용 키 입력 (release에서는 끔)
- [ ] 곡 3개(`slow`, `brake`, `festival`) 완주 확인

## 단계 3. 차량 입력

통과 기준: 방향키로 '천천히 한 바퀴'를 PERFECT·GOOD 80% 이상으로 완주.

- [ ] `input`: 차량 신호 → 입력 이벤트 `(레인, 누름/뗌, 도착 시각)` 변환
  - [ ] 브레이크 (false→true 누름, true→false 뗌)
  - [ ] 핸들 (-15° 아래 = 왼쪽, +15° 위 = 오른쪽, ±8° 안에서 재무장)
  - [ ] 와이퍼 (OFF→다른 값 = 누름, 반응 후 1.5초 무시)
- [ ] `tools/key_bridge.py`: 방향키 → Virtual Console REST (`adb forward tcp:8088 tcp:8088`)
- [ ] 박자 보정: 100BPM 메트로놈, 브레이크 5회 + 핸들 5회, 중앙값 지연 저장·재사용
- [ ] 방향키로 `slow` 곡 80% 이상 완주

## 단계 4. 차량 반응

통과 기준: Virtual Console에서 앰비언트 색, 와이퍼, 실내등이 바뀌고, 곡이 끝나면 원래 값.

- [ ] `reaction`: 반응 큐, 속도 제한(초당 3회 미만), 최신 명령만 유지
- [ ] PERFECT → 앰비언트 레인 색 (0.5초에 1회 이하)
- [ ] 콤보 10/30/50 → 기본 밝기 단계, MISS → 앰비언트 끔
- [ ] 콤보 10, 20 첫 도달 → 와이퍼 LEVEL_1 1.2초 (곡당 2회)
- [ ] 곡 완주 → 실내등 0.5초 ON/OFF × 3
- [ ] 시작 전 상태 저장, 종료·일시정지·주행 전환·앱 종료 시 원상 복구
- [ ] 실내 그림·차량 신호 띠를 차량에서 읽은 실제 값으로 그림
- [ ] 설정에서 차량 반응 끄기

## 단계 5. 부가 기능

통과 기준: SOC와 충전 스위치를 바꾸면 추천 문구가 바뀜. 미션 완료 표시.

- [ ] 홈: 충전 남은 시간 → "M곡 정도" 추천 ('예상' 표기)
- [ ] 튜토리얼 미션: 와이퍼 `CONTINUOUS_LEVEL_3` 감지 시 완료
- [ ] Gleo: `assets/schema.json` + intent-filter (`START_CARBEAT`, `PAUSE_GAME`)
- [ ] `adb shell am start -a START_CARBEAT` 동작 확인

## 단계 6. 제출 준비

통과 기준: 초기화한 에뮬레이터에 APK를 설치해 처음부터 끝까지 동작.

- [ ] release 서명 설정 (키스토어는 저장소 밖에 보관)
- [ ] release APK 빌드, 에뮬레이터 설치·실행 확인 (최적화로 SDK 클래스가 제거되지 않았는지)
- [ ] `.gitignore`에 `!apk/*.apk` 예외 추가, `apk/정통윤_CarBeat_release.apk` 배치
- [ ] README: 앱 이름·한 줄 소개, 실행 방법, 테스트 환경·필요 설정, APK 위치, 검증 기록
- [ ] 시연 영상 녹화 (앱 + Virtual Console 동시)
- [ ] 클린 빌드 최종 검증 (인터넷 연결 상태)
