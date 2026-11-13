package com.dontstudyy.carbeat.input

import com.dontstudyy.carbeat.game.Lane

/** 공통 입력 이벤트. [timeNanos]는 리스너가 불린 순간의 SystemClock.elapsedRealtimeNanos(). */
data class InputEvent(val lane: Lane, val down: Boolean, val timeNanos: Long)

/** 브레이크: false→true가 누름, true→false가 뗌. */
class BrakeConverter {
    private var prev = false

    fun onValue(pressed: Boolean?, timeNanos: Long): InputEvent? {
        if (pressed == null || pressed == prev) return null
        prev = pressed
        return InputEvent(Lane.BRAKE, pressed, timeNanos)
    }
}

/**
 * 핸들: 앱이 받는 각도 기준으로 -15° 이하가 왼쪽, +15° 이상이 오른쪽 누름.
 * ±8° 안으로 돌아와야 뗌 처리되고 다음 입력을 받는다(떨림 방지).
 * 반대쪽 +15°/-15°로 바로 넘어가면 원래 쪽을 떼고 반대쪽을 누른 것으로 본다.
 *
 * 주의: Virtual Console에서 steering=+30을 보내면 앱은 -30을 받는다(에뮬레이터 실측).
 */
class SteerConverter(
    private val pressDeg: Float = 15f,
    private val releaseDeg: Float = 8f,
) {
    private var active: Lane? = null

    fun onAngle(angleDeg: Float?, timeNanos: Long): List<InputEvent> {
        if (angleDeg == null) return emptyList()
        val out = ArrayList<InputEvent>(2)
        when (active) {
            null -> {
                if (angleDeg <= -pressDeg) { active = Lane.STEER_LEFT; out += InputEvent(Lane.STEER_LEFT, true, timeNanos) }
                else if (angleDeg >= pressDeg) { active = Lane.STEER_RIGHT; out += InputEvent(Lane.STEER_RIGHT, true, timeNanos) }
            }
            Lane.STEER_LEFT -> {
                if (angleDeg >= pressDeg) {
                    out += InputEvent(Lane.STEER_LEFT, false, timeNanos)
                    out += InputEvent(Lane.STEER_RIGHT, true, timeNanos)
                    active = Lane.STEER_RIGHT
                } else if (Math.abs(angleDeg) < releaseDeg) {
                    out += InputEvent(Lane.STEER_LEFT, false, timeNanos)
                    active = null
                }
            }
            Lane.STEER_RIGHT -> {
                if (angleDeg <= -pressDeg) {
                    out += InputEvent(Lane.STEER_RIGHT, false, timeNanos)
                    out += InputEvent(Lane.STEER_LEFT, true, timeNanos)
                    active = Lane.STEER_LEFT
                } else if (Math.abs(angleDeg) < releaseDeg) {
                    out += InputEvent(Lane.STEER_RIGHT, false, timeNanos)
                    active = null
                }
            }
            else -> Unit
        }
        return out
    }
}

/**
 * 와이퍼: OFF에서 다른 값(ON 등)으로 바뀌는 순간이 누름.
 * 앱이 반응으로 와이퍼를 켠 뒤 [ignoreNanos] 동안 들어오는 변화는 입력으로 치지 않는다.
 * 무시 구간 중에도 이전 상태는 갱신한다(앱이 끈 OFF가 나중에 오인되지 않게).
 */
class WiperConverter(private val ignoreNanos: Long = 1_500_000_000L) {
    private var prevOn = false
    private var lastAppActionNanos: Long? = null

    /** 앱이 와이퍼를 켜거나 끄는 명령을 보낸 시각을 알린다. */
    fun markAppAction(timeNanos: Long) { lastAppActionNanos = timeNanos }

    fun onState(on: Boolean, timeNanos: Long): InputEvent? {
        if (on == prevOn) return null
        prevOn = on
        val last = lastAppActionNanos
        if (last != null && timeNanos - last in 0 until ignoreNanos) return null
        return InputEvent(Lane.WIPER, on, timeNanos)
    }
}
