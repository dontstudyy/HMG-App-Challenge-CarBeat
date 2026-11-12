"""단계 0 실험: Virtual Console REST로 입력을 보내고, 앱(debug 빌드 ExperimentReceiver `watch`)이
logcat(CARBEAT)에 남긴 도착 시각과 비교해 지연·흔들림을 잰다.

사전 조건
  - `adb forward tcp:8088 tcp:8088`, Virtual Console 웹페이지가 열려 있을 것(emulator_active)
  - 앱 실행 + `watch` 실험 시작:
      adb shell am broadcast -n com.dontstudyy.carbeat/.debug.ExperimentReceiver --es id watch
사용: python tools/exp_input.py steer|brake|wiper [횟수]
지연의 절대값은 PC·에뮬레이터 시계 차이(±adb 왕복시간)를 포함한다. 흔들림(범위·표준편차)은 차이와 무관하다.
"""
import random
import re
import statistics
import subprocess
import sys
import time
import urllib.request

ADB = r"C:\pleosEnv\Android\Sdk\platform-tools\adb.exe"
BASE = "http://127.0.0.1:8088"


def put(path):
    req = urllib.request.Request(BASE + path, method="PUT")
    t0 = time.time() * 1000
    urllib.request.urlopen(req, timeout=5).read()
    return t0, time.time() * 1000


def adb(*args):
    return subprocess.run([ADB, *args], capture_output=True, text=True, encoding="utf8").stdout


def clock_offset():
    """에뮬레이터 epoch ms - PC epoch ms 추정. 왕복이 가장 짧은 표본을 쓴다."""
    best = None
    for _ in range(15):
        t0 = time.time() * 1000
        out = adb("shell", "date", "+%s%3N").strip()
        t1 = time.time() * 1000
        rtt = t1 - t0
        if best is None or rtt < best[0]:
            best = (rtt, int(out) - (t0 + t1) / 2)
    return best[1], best[0]


EVT = re.compile(r"(EVT_\w+) w=(\d+) n=([\d.]+) (.*)")


def events():
    out = adb("logcat", "-d", "-s", "CARBEAT")
    res = []
    for line in out.splitlines():
        m = EVT.search(line)
        if m:
            res.append((m.group(1), int(m.group(2)), m.group(4)))
    return res


def stats(name, lat):
    lat = [x for x in lat if x is not None]
    if not lat:
        print(name, "측정값 없음")
        return
    print(f"{name}: n={len(lat)} min={min(lat):.0f} max={max(lat):.0f} 범위={max(lat)-min(lat):.0f}ms "
          f"평균={statistics.mean(lat):.0f} 표준편차={statistics.pstdev(lat):.0f} "
          f"(중앙값={statistics.median(lat):.0f}, ±흔들림 최대={max(abs(x-statistics.median(lat)) for x in lat):.0f}ms)")


def main():
    kind = sys.argv[1]
    n = int(sys.argv[2]) if len(sys.argv) > 2 else 20
    off, rtt = clock_offset()
    print(f"시계 오프셋(에뮬-PC) {off:.0f}ms, adb 왕복 {rtt:.0f}ms")
    adb("logcat", "-c")
    trials = []  # (t_send_in_emu_clock, put_dur)
    for i in range(n):
        if kind == "steer":
            sign = 1 if i % 2 == 0 else -1
            t0, t1 = put(f"/vehicle/controls?steering={30 * sign}")
        elif kind == "brake":
            t0, t1 = put("/vehicle/controls?brake=100")
        elif kind == "wiper":
            t0, t1 = put("/vehicle/body?front_wiper=Low")
        else:
            raise SystemExit("steer|brake|wiper")
        trials.append((t0 + off, t1 - t0, 30 * sign if kind == "steer" else None))
        time.sleep(random.uniform(0.7, 1.2))
        if kind == "steer":
            put("/vehicle/controls?steering=0")
        elif kind == "brake":
            put("/vehicle/controls?brake=0")
        else:
            put("/vehicle/body?front_wiper=Off")
        time.sleep(random.uniform(0.5, 0.9))
    time.sleep(1.0)
    ev = events()
    # 시계 오차 때문에 시간창 대신 순서로 1:1 짝짓는다(시도 i ↔ i번째 해당 이벤트).
    hits = []
    for name, w, val in ev:
        if kind == "steer" and name == "EVT_STEER" and abs(float(val.split("=")[1])) >= 15:
            hits.append((w, float(val.split("=")[1])))
        elif kind == "brake" and name == "EVT_BRAKE" and val.endswith("true"):
            hits.append((w, None))
        elif kind == "wiper" and name == "EVT_WIPER" and val.endswith("ON") and "FRONT" in val:
            hits.append((w, None))
    print(f"전송 {len(trials)}회, 도착 이벤트 {len(hits)}개")
    lat = []
    signs = []
    for k, (ts, dur, sgn) in enumerate(trials):
        if k < len(hits):
            lat.append(hits[k][0] - ts)
            if kind == "steer":
                signs.append((sgn, hits[k][1]))
        else:
            lat.append(None)
    print("각 시도 지연(ms):", [None if x is None else round(x) for x in lat])
    stats(f"{kind} 지연(PC 전송 시작 → 앱 리스너)", lat)
    if kind == "steer":
        print("전송 부호 → 앱 수신 값:", signs[:6], "…  부호 반전 비율:",
              f"{sum(1 for s, v in signs if s * v < 0)}/{len(signs)}")
    print("PUT 응답 시간(ms) 평균:", round(statistics.mean(d for _, d, _ in trials)))


if __name__ == "__main__":
    main()
