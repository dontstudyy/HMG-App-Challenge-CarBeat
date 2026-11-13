"""Virtual Console의 차량 값(앰비언트 색·밝기·켜짐, 앞 와이퍼, 실내등)을 지켜보다가 바뀔 때마다 시각과 함께 출력한다.
차량 반응(단계 4) 검증용: 곡을 플레이하는 동안 값이 실제로 바뀌는지, 끝난 뒤 원래 값으로 돌아오는지 본다.

  python tools/watch_vehicle.py 60        # 60초 동안 지켜본다 (기본 60)

사전 조건: adb forward tcp:8088 tcp:8088, Virtual Console 페이지 열림.
"""
import json
import sys
import time
import urllib.request

BASE = "http://127.0.0.1:8088"


def snapshot():
    d = json.load(urllib.request.urlopen(BASE + "/vehicle/state", timeout=3))
    b = d["body"]
    amb = b["ambient_light"]
    return {
        "ambient": f"{amb.get('mode')} {amb.get('color')} {amb.get('brightness_pct')}%",
        "wiper_front": b["wipers"]["front"]["mode"],
        "cabin": b["cabin_light"]["on"],
    }


def main():
    secs = float(sys.argv[1]) if len(sys.argv) > 1 else 60
    t0 = time.time()
    last = None
    n = {"ambient": 0, "wiper_front": 0, "cabin": 0}
    print("시작 값:", snapshot())
    while time.time() - t0 < secs:
        try:
            cur = snapshot()
        except Exception as e:  # noqa: BLE001
            print("읽기 실패", e)
            time.sleep(0.5)
            continue
        if last is not None:
            for k in cur:
                if cur[k] != last[k]:
                    n[k] += 1
                    print(f"+{time.time() - t0:6.2f}s {k}: {last[k]} -> {cur[k]}")
        last = cur
        time.sleep(0.08)
    print("끝 값:", last)
    print("변화 횟수:", n)


if __name__ == "__main__":
    main()
