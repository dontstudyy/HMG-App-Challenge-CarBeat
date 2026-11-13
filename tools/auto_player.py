"""자동 입력 스크립트: Virtual Console REST로 곡 노트(또는 박자 보정 박)에 맞춰 입력을 보낸다.
사람이 칠 수 없는 속도의 곡 시연·녹화와, 입력 파이프라인(REST → 차량 신호 → 앱 판정) 검증에 쓴다.

  python tools/auto_player.py play slow    # 앱에서 곡을 시작하면(PLAY_T0 로그) 노트 시각에 맞춰 입력
  python tools/auto_player.py calib        # 앱에서 '보정 시작'을 누르면(CALIB_BEAT0 로그) 박에 맞춰 입력

사전 조건: adb forward tcp:8088 tcp:8088, Virtual Console 페이지 열림, 앱(debug)이 해당 화면에 있을 것.
에뮬레이터와 PC의 시계 차이(약 ±60ms)는 박자 보정이 흡수한다(보정 → 곡을 같은 방식으로 실행할 것).
"""
import argparse
import json
import re
import subprocess
import threading
import time
import urllib.request
from collections import deque
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

ADB = r"C:\pleosEnv\Android\Sdk\platform-tools\adb.exe"
BASE = "http://127.0.0.1:8088"
SONGS = Path(__file__).resolve().parent.parent / "app/src/main/assets/songs"

# 레인 번호 → (누름, 뗌) 요청. 핸들은 부호가 반전되어 앱에 들어간다(+30 → 앱 -30 = 왼쪽).
LANE_PATHS = {
    0: ("/vehicle/controls?steering=30", "/vehicle/controls?steering=0"),
    1: ("/vehicle/controls?brake=100", "/vehicle/controls?brake=0"),
    2: ("/vehicle/controls?steering=-30", "/vehicle/controls?steering=0"),
    3: ("/vehicle/body?front_wiper=Low", "/vehicle/body?front_wiper=Off"),
}


# 누름을 유지하는 시간(초). 핸들 신호는 약 8회/초로만 갱신돼 80ms 펄스는 놓칠 수 있어 길게 잡는다.
TAP_SEC = {0: 0.25, 1: 0.08, 2: 0.25, 3: 0.25}


def adb(*a):
    return subprocess.run([ADB, *a], capture_output=True, text=True, encoding="utf8").stdout


def put(path):
    try:
        urllib.request.urlopen(urllib.request.Request(BASE + path, method="PUT"), timeout=3).read()
    except Exception as e:  # noqa: BLE001
        print("PUT 실패", path, e)


def clock_offset_ms():
    """에뮬레이터 epoch ms - PC epoch ms. 왕복이 가장 짧은 표본을 쓴다."""
    best = None
    for _ in range(15):
        t0 = time.time() * 1000
        out = adb("shell", "date", "+%s%3N").strip()
        t1 = time.time() * 1000
        if best is None or t1 - t0 < best[0]:
            best = (t1 - t0, int(out) - (t0 + t1) / 2)
    return best[1]


def wait_log(pattern, timeout=120):
    """지금부터 새로 나오는 logcat(CARBEAT) 줄 중 pattern에 처음 맞는 match를 돌려준다."""
    adb("logcat", "-c")
    p = subprocess.Popen([ADB, "logcat", "-v", "brief", "-s", "CARBEAT"], stdout=subprocess.PIPE, text=True, encoding="utf8")
    rx = re.compile(pattern)
    t_end = time.time() + timeout
    try:
        for line in p.stdout:
            m = rx.search(line)
            if m:
                return m
            if time.time() > t_end:
                break
    finally:
        p.kill()
    return None


def run_schedule(events, t0_pc_ms):
    """events: (초, 경로) 목록. PC epoch t0_pc_ms + 초 시각에 PUT을 보낸다(동시 입력은 스레드로)."""
    pool = ThreadPoolExecutor(max_workers=8)
    for sec, path in sorted(events):
        target = t0_pc_ms / 1000 + sec
        while True:
            now = time.time()
            if now >= target:
                break
            time.sleep(max(0.0, min(0.01, target - now - 0.002)))
        pool.submit(put, path)
    pool.shutdown(wait=True)


class SongClock(threading.Thread):
    """앱이 0.25초마다 남기는 PLAY_SYNC(곡 시각 t, 에뮬 epoch)를 읽어, 곡 시각 → PC 시각을 예측한다.
    오디오가 실제 시간보다 느리게 도는 환경에서도 곡 시계를 따라가도록 최근 표본으로 속도를 직선 보정한다."""

    SYNC = re.compile(r"PLAY_SYNC t=([\d.]+) epoch=(\d+)")
    RESULT = re.compile(r"PLAY_RESULT (.*)")

    def __init__(self, off_ms):
        super().__init__(daemon=True)
        self.off = off_ms
        self.samples = deque(maxlen=12)  # (곡 시각 초, PC epoch 초)
        self.lock = threading.Lock()
        self.first = threading.Event()
        self.result = None
        self.proc = None

    def run(self):
        adb("logcat", "-c")
        self.proc = subprocess.Popen([ADB, "logcat", "-v", "brief", "-s", "CARBEAT"], stdout=subprocess.PIPE, text=True, encoding="utf8")
        for line in self.proc.stdout:
            m = self.SYNC.search(line)
            if m:
                with self.lock:
                    self.samples.append((float(m.group(1)), (int(m.group(2)) - self.off) / 1000))
                self.first.set()
                continue
            m = self.RESULT.search(line)
            if m:
                self.result = m.group(1)

    def wall_for(self, s):
        """곡 시각 s초에 해당하는 PC epoch(초)를 예측한다."""
        with self.lock:
            pts = list(self.samples)
        if len(pts) < 3:
            t, e = pts[-1]
            return e + (s - t)
        n = len(pts)
        mt = sum(p[0] for p in pts) / n
        me = sum(p[1] for p in pts) / n
        var = sum((p[0] - mt) ** 2 for p in pts)
        slope = sum((p[0] - mt) * (p[1] - me) for p in pts) / var if var > 1e-9 else 1.0
        slope = min(max(slope, 0.5), 3.0)  # 비정상 값 방지
        return me + slope * (s - mt)

    def stop(self):
        if self.proc:
            self.proc.kill()


def run_song_schedule(events, clock):
    pool = ThreadPoolExecutor(max_workers=8)
    for sec, path in sorted(events):
        while time.time() < clock.wall_for(sec):
            time.sleep(0.003)
        pool.submit(put, path)
    pool.shutdown(wait=True)


def play(song_id):
    song = json.loads((SONGS / f"{song_id}.json").read_text(encoding="utf8"))
    off = clock_offset_ms()
    print(f"시계 오프셋(에뮬-PC) {off:.0f}ms. 앱에서 '{song['title']}'를 시작하세요(대기 중)...")
    clock = SongClock(off)
    clock.start()
    if not clock.first.wait(timeout=180):
        raise SystemExit("PLAY_SYNC를 못 받았습니다")
    events = []
    for n in song["notes"]:
        press, release = LANE_PATHS[n["lane"]]
        events.append((n["t"], press))
        events.append((n["t"] + n.get("hold", TAP_SEC[n["lane"]]), release))  # 롱노트는 끝까지, 탭은 TAP_SEC 뒤 뗌
    print(f"곡 시작 감지. 노트 {len(song['notes'])}개 입력 시작 (곡 시계를 따라감)")
    run_song_schedule(events, clock)
    for _ in range(300):
        if clock.result:
            break
        time.sleep(0.1)
    clock.stop()
    print("결과:", clock.result or "(결과 로그 없음)")


def calib():
    off = clock_offset_ms()
    print(f"시계 오프셋(에뮬-PC) {off:.0f}ms. 앱에서 '보정 시작'을 누르세요(대기 중)...")
    m = wait_log(r"CALIB_BEAT0 epoch=(\d+) beatMs=(\d+)")
    if not m:
        raise SystemExit("CALIB_BEAT0를 못 받았습니다")
    b0_pc = int(m.group(1)) - off
    beat = int(m.group(2)) / 1000
    events = []
    for i in range(5):  # 준비 구간(박 2개)을 건너뛰고 브레이크 5회
        k = 3 + i
        events += [(k * beat, LANE_PATHS[1][0]), (k * beat + 0.08, LANE_PATHS[1][1])]
    for i in range(5):  # 이어서 핸들 5회(좌우 번갈아)
        k = 9 + i
        lane = 0 if i % 2 == 0 else 2
        events += [(k * beat, LANE_PATHS[lane][0]), (k * beat + TAP_SEC[lane], LANE_PATHS[lane][1])]
    print("박 시각 감지. 입력 시작")
    run_schedule(events, b0_pc)
    time.sleep(1.0)
    for line in adb("logcat", "-d", "-s", "CARBEAT").splitlines():
        if "CALIB samples" in line:
            print(line.split("CARBEAT :")[-1].strip())


if __name__ == "__main__":
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("mode", choices=["play", "calib"])
    ap.add_argument("song", nargs="?", default="slow")
    a = ap.parse_args()
    if a.mode == "play":
        play(a.song)
    else:
        calib()
