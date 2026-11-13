"""키보드 연결 프로그램: PC 방향키를 Virtual Console REST로 보내 차량 신호로 바꾼다.

  ↓ 브레이크 100 / 뗌 0      ← 핸들 왼쪽 / 뗌 0      → 핸들 오른쪽 / 뗌 0      ↑ 앞 와이퍼 Low / 뗌 Off

실행 전: `adb forward tcp:8088 tcp:8088`, Virtual Console 웹페이지를 열어 둘 것(안 열면 503).
핸들 부호: Virtual Console에 steering=+N을 보내면 앱은 -N을 받는다(에뮬레이터 실측). 그래서 ←는 +, →는 -로 보낸다.
Windows 전용(GetAsyncKeyState 폴링). 상태 기반이라 키를 누르고 있을 때의 반복 입력은 자동으로 무시된다.
종료: Ctrl+C (종료하면 모든 입력을 0으로 되돌린다).
"""
import argparse
import ctypes
import queue
import threading
import time
import urllib.error
import urllib.request

VK_LEFT, VK_UP, VK_RIGHT, VK_DOWN = 0x25, 0x26, 0x27, 0x28
user32 = ctypes.windll.user32


def is_down(vk):
    return bool(user32.GetAsyncKeyState(vk) & 0x8000)


class Sender(threading.Thread):
    """PUT을 순서대로 보내는 스레드. 폴링 루프가 네트워크 때문에 막히지 않게 한다."""

    def __init__(self, base):
        super().__init__(daemon=True)
        self.base = base
        self.q = queue.Queue()

    def send(self, path):
        self.q.put(path)

    def run(self):
        while True:
            path = self.q.get()
            try:
                t0 = time.perf_counter()
                urllib.request.urlopen(urllib.request.Request(self.base + path, method="PUT"), timeout=3).read()
                print(f"  PUT {path}  ({(time.perf_counter() - t0) * 1000:.0f}ms)")
            except urllib.error.HTTPError as e:
                print(f"  PUT {path} 실패: HTTP {e.code} (503이면 Virtual Console 페이지가 열려 있는지 확인)")
            except Exception as e:  # noqa: BLE001
                print(f"  PUT {path} 실패: {e}  (adb forward tcp:8088 tcp:8088 확인)")


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--base", default="http://127.0.0.1:8088")
    ap.add_argument("--steer", type=int, default=30, help="핸들 입력 각도(도). 기본 30")
    a = ap.parse_args()

    sender = Sender(a.base)
    sender.start()
    prev = {"left": False, "right": False, "up": False, "down": False}
    last_steer = None
    steer_order = []  # 눌린 순서. 마지막이 우선한다
    print("키보드 연결 중. ← ↓ → ↑ 를 누르세요. 종료: Ctrl+C")
    try:
        while True:
            cur = {"left": is_down(VK_LEFT), "right": is_down(VK_RIGHT), "up": is_down(VK_UP), "down": is_down(VK_DOWN)}
            if cur["down"] != prev["down"]:
                print("↓ 브레이크", "누름" if cur["down"] else "뗌")
                sender.send(f"/vehicle/controls?brake={100 if cur['down'] else 0}")
            if cur["up"] != prev["up"]:
                print("↑ 와이퍼", "누름" if cur["up"] else "뗌")
                sender.send(f"/vehicle/body?front_wiper={'Low' if cur['up'] else 'Off'}")
            for k in ("left", "right"):
                if cur[k] and not prev[k]:
                    steer_order.append(k)
                if not cur[k] and prev[k] and k in steer_order:
                    steer_order.remove(k)
            if cur["left"] != prev["left"] or cur["right"] != prev["right"]:
                active = steer_order[-1] if steer_order else None
                value = {"left": a.steer, "right": -a.steer, None: 0}[active]  # 부호 반전 주의
                if value != last_steer:
                    print("핸들", {"left": "← 왼쪽", "right": "→ 오른쪽", None: "중앙"}[active])
                    sender.send(f"/vehicle/controls?steering={value}")
                    last_steer = value
            prev = cur
            time.sleep(0.004)
    except KeyboardInterrupt:
        print("\n종료: 입력을 0으로 되돌립니다.")
        for path in ("/vehicle/controls?brake=0", "/vehicle/controls?steering=0", "/vehicle/body?front_wiper=Off"):
            sender.send(path)
        time.sleep(0.6)


if __name__ == "__main__":
    main()
