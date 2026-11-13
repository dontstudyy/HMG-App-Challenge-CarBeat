"""단계 1 통과 기준 측정: 기어를 D로 바꾼 뒤 앱이 숨김 화면(GATE_UI open=false)으로 바뀔 때까지의 시간.

사전 조건: adb forward tcp:8088 tcp:8088, Virtual Console 페이지가 열려 있을 것(emulator_active),
앱이 원하는 화면에 떠 있을 것. 끝나면 기어를 P로 되돌린다.
사용: python tools/exp_gate.py [횟수]
시계 오차(약 ±60ms)가 포함된다. 기준은 1초.
"""
import re
import statistics
import subprocess
import sys
import time
import urllib.request

ADB = r"C:\pleosEnv\Android\Sdk\platform-tools\adb.exe"
BASE = "http://127.0.0.1:8088"


def put(path):
    urllib.request.urlopen(urllib.request.Request(BASE + path, method="PUT"), timeout=5).read()


def adb(*a):
    return subprocess.run([ADB, *a], capture_output=True, text=True, encoding="utf8").stdout


def offset_ms():
    best = None
    for _ in range(15):
        t0 = time.time() * 1000
        out = adb("shell", "date", "+%s%3N").strip()
        t1 = time.time() * 1000
        if best is None or t1 - t0 < best[0]:
            best = (t1 - t0, int(out) - (t0 + t1) / 2)
    return best[1]


LINE = re.compile(r"^\s*(\d+\.\d+)\s+.*CARBEAT\s*:\s*(?:GATE_UI open=(true|false)|GATE_IN (gear|ux)=(\w+))")


def events(after_ms):
    """after_ms 이후의 (종류, 값, 시각ms) 목록. 종류: ui / gear / ux"""
    out = adb("logcat", "-d", "-v", "epoch", "-s", "CARBEAT")
    res = []
    for line in out.splitlines():
        m = LINE.search(line)
        if not m:
            continue
        t = float(m.group(1)) * 1000
        if t < after_ms - 150:
            continue
        if m.group(2):
            res.append(("ui", m.group(2), t))
        else:
            res.append((m.group(3), m.group(4), t))
    return res


def first(ev, kind, pred):
    for k, v, t in ev:
        if k == kind and pred(v):
            return t
    return None


def fmt(x, t0):
    return "없음" if x is None else f"{x - t0:.0f}ms"


def main():
    n = int(sys.argv[1]) if len(sys.argv) > 1 else 10
    wait = float(sys.argv[2]) if len(sys.argv) > 2 else 3.0
    off = offset_ms()
    closed = []
    try:
        for i in range(n):
            adb("logcat", "-c")
            t = time.time() * 1000 + off
            put("/vehicle/gear?gear=Drive")
            time.sleep(wait)
            ev = events(t)
            g = first(ev, "gear", lambda v: v == "DRIVE")
            u = first(ev, "ux", lambda v: v == "true")
            c = first(ev, "ui", lambda v: v == "false")
            closed.append(None if c is None else c - t)
            adb("logcat", "-c")
            t2 = time.time() * 1000 + off
            put("/vehicle/gear?gear=Park")
            time.sleep(wait)
            ev2 = events(t2)
            o = first(ev2, "ui", lambda v: v == "true")
            print(f"#{i}: 기어이벤트 {fmt(g, t)}, 규제이벤트 {fmt(u, t)}, 숨김화면 {fmt(c, t)}, P→열림 {fmt(o, t2)}")
    finally:
        put("/vehicle/gear?gear=Park")
    v = [x for x in closed if x is not None]
    print(f"\n숨김 화면 전환: 성공 {len(v)}/{n}", end="")
    if v:
        print(f", 최소 {min(v):.0f} 최대 {max(v):.0f} 평균 {statistics.mean(v):.0f}ms, 1초 초과 {sum(1 for x in v if x > 1000)}회 (기준 1000ms)")
    else:
        print()


if __name__ == "__main__":
    main()
