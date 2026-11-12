# 카비트 (CarBeat)

차를 세워 둔 시간에 브레이크 페달, 핸들, 와이퍼 스위치로 박자를 맞추고, 맞힐 때마다 앰비언트 라이트·와이퍼·실내등이 반응하는 차량 조작계 리듬게임입니다.

- 현대 Pleos Connect 앱 공모전 출품작
- 대상 환경: Android(Kotlin) / Pleos Connect SDK
- 상태: 개발 중

## 검증 기록

Pleos Connect 에뮬레이터(AVD `Pleos_Connect_v2.1.0`, 2560×1440, 160dpi)에서 측정했다. 측정 스크립트는 `tools/exp_input.py`, 앱 쪽 실험 코드는 debug 빌드 전용(`app/src/debug`)이다.

### 단계 0 (2026-11-13)

입력 지연은 Virtual Console REST 전송 시각 → 앱 리스너 도착 시각이다. PC와 에뮬레이터의 시계 차이(약 ±60ms)가 절대값에 섞이므로 **흔들림(범위, 중앙값에서 최대 벗어남)만 의미가 있다.**

| 항목 | 결과 |
|---|---|
| 브레이크 입력 (20회) | 범위 55ms, 표준편차 12ms, 중앙값에서 최대 51ms. 해제도 즉시 반영 |
| 핸들 임계값 통과 시각 (20회) | 범위 204ms, 표준편차 65ms, **중앙값에서 최대 133ms** (통과 기준 ±180ms 안) |
| 와이퍼 입력 (10회) | 범위 80ms, 표준편차 22ms, 중앙값에서 최대 68ms |
| 앰비언트 0.5초 간격 10회 쓰기 | 10/10 성공, 콜백 22~125ms(첫 호출 276ms). 레인 색 ARGB(주황·빨강·노랑·하늘)가 그대로 적용됨. 쓰기 후 원래 값으로 복구 확인 |
| 화면 | 전체 2560×1440. 앱 창은 오른쪽 패널(멀티윈도우) **1682×1210dp**(가로, 약 1.39:1) |
| `PARKING_BRAKE_ON`(0x11200402) | 속성 목록에 있고 읽기·구독 가능(Boolean). **그러나 에뮬레이터에서 항상 false.** 텔테일 시뮬레이터(`ParkingBrake`, `ElectronicParkingBrake`)를 켜도 값이 바뀌지 않음 |
| 일부 값만 PUT | `brake`만, `steering`만 보내도 적용됨 |

### Virtual Console REST 확인된 주소

| 입력 | 요청 | 앱이 받는 값 |
|---|---|---|
| 브레이크 | `PUT /vehicle/controls?brake=0..100` | `registerBrakePedalStatus` Boolean |
| 핸들 | `PUT /vehicle/controls?steering=<도>` (-470~500) | `registerSteeringWheelAngle` Float. **부호가 반대로 들어옴**(전송 +30 → 앱 -30, 20/20) |
| 와이퍼 | `PUT /vehicle/body?front_wiper=Off\|Auto\|Low\|High` | `registerWiperState`: Low → `ON`, Off → `OFF` |
| 주차 브레이크 | `PUT /vehicle/telltales/simulator?type=ParkingBrake&state=On&color=Red` | 앱의 `PARKING_BRAKE_ON`에는 반영되지 않음 |

### 알아 둘 점

- 와이퍼 입력 리스너(`WiperState`)의 값은 `OFF`, `ON`, `SERVICE`뿐이다. 단계(1단/2단)는 상태 변화가 올 때 `getCurrentWiperSwitch`로 읽어야 한다.
- Virtual Console 웹페이지가 1초마다 보내는 하트비트가 끊기면(탭이 백그라운드로 밀리거나 닫힘) 쓰기가 `emulator_active=false`가 되어 503으로 막힌다. 측정·시연 중에는 이 탭을 별도 창으로 띄워 두고 가려지지 않게 한다.
