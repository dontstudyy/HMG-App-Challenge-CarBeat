package com.dontstudyy.carbeat.reaction

import com.dontstudyy.carbeat.game.Judgment
import com.dontstudyy.carbeat.game.Lane

/** 앰비언트에 쓰고 싶은 상태. 구역이 하나뿐이라 색(ARGB)과 밝기(0~100)만 있다. [on]이 false이면 "끔"(밝기 0으로 쓴다). */
data class AmbientTarget(val on: Boolean, val argb: Int, val brightness: Int)

/** 이번에 차량에 새로 쓰고 싶은 값. null이면 바꿀 것이 없다. */
data class Desired(
    val ambient: AmbientTarget? = null,
    /** true = 앞 와이퍼를 1단으로 켠다, false = 끈다 */
    val wiperOn: Boolean? = null,
    /** true = 실내등 켬, false = 끔 */
    val cabinOn: Boolean? = null,
) {
    val isEmpty: Boolean get() = ambient == null && wiperOn == null && cabinOn == null
}

/**
 * 게임 결과를 차량 반응으로 바꾸는 규칙(설계서 7장). 시간은 모두 ms이고 호출자가 준다.
 *
 * - PERFECT: 앰비언트를 레인 색으로, 밝기를 100으로 올렸다가 0.3초 뒤 기본 밝기로. 0.5초에 한 번 이하.
 * - 기본 밝기: 콤보 0 → 0, 10 이상 → 30, 30 이상 → 55, 50 이상 → 80.
 * - MISS: 앰비언트 끔. 다음 판정(PERFECT·GOOD)이 나오면 복귀.
 * - 콤보 10, 20에 처음 도달하면 와이퍼 1단 1.2초 뒤 끔(곡당 2회, [wiperEnabled]일 때만).
 * - 곡 완주: 실내등 0.5초 켜고 0.5초 끄기 3회.
 *
 * [poll]은 지금 시각에 원하는 상태를 계산해 직전에 내보낸 값과 달라진 것만 돌려준다.
 */
class ReactionPlanner(
    private val ambientEnabled: Boolean = true,
    private val wiperEnabled: Boolean = false,
) {
    companion object {
        private const val NEVER = -1_000_000_000L
        const val FLASH_BRIGHTNESS = 100
        const val FLASH_MS = 300L
        const val FLASH_MIN_INTERVAL_MS = 500L
        const val WIPER_ON_MS = 1200L
        const val CABIN_STEP_MS = 500L
        const val CABIN_BLINKS = 3
        val WIPER_COMBOS = intArrayOf(10, 20)
        const val DEFAULT_COLOR = 0xFFFFFFFF.toInt()
        val LANE_COLORS = intArrayOf(0xFFFF7A1A.toInt(), 0xFFFF3355.toInt(), 0xFFFFC233.toInt(), 0xFF4CC9FF.toInt())

        fun baseBrightness(combo: Int) = when {
            combo >= 50 -> 80
            combo >= 30 -> 55
            combo >= 10 -> 30
            else -> 0
        }
    }

    private var combo = 0
    private var missed = false
    private var color = DEFAULT_COLOR
    private var flashUntil = Long.MIN_VALUE
    // 뺄셈에 쓰이므로 Long.MIN_VALUE는 오버플로한다. 충분히 오래전 시각을 쓴다.
    private var lastFlashStart = NEVER
    private var wiperUntil = Long.MIN_VALUE
    private val wiperDone = HashSet<Int>()
    private var cabinStart = Long.MIN_VALUE
    private var completed = false

    private var emittedAmbient: AmbientTarget? = null
    private var emittedWiper: Boolean? = null
    private var emittedCabin: Boolean? = null

    /** 곡을 시작할 때: 앰비언트를 켜고 기본 밝기 0으로 시작 */
    fun onStart(nowMs: Long) {
        combo = 0; missed = false; color = DEFAULT_COLOR
        flashUntil = Long.MIN_VALUE; lastFlashStart = NEVER
        wiperUntil = Long.MIN_VALUE; wiperDone.clear()
        cabinStart = Long.MIN_VALUE; completed = false
        emittedAmbient = null; emittedWiper = null; emittedCabin = null
    }

    /** 판정이 났을 때. [comboAfter]는 이 판정 뒤의 콤보 */
    fun onJudged(lane: Lane, judgment: Judgment, comboAfter: Int, nowMs: Long) {
        combo = comboAfter
        if (judgment == Judgment.MISS) {
            missed = true
            return
        }
        missed = false
        if (judgment == Judgment.PERFECT && nowMs - lastFlashStart >= FLASH_MIN_INTERVAL_MS) {
            color = LANE_COLORS[lane.ordinal]
            lastFlashStart = nowMs
            flashUntil = nowMs + FLASH_MS
        }
        if (wiperEnabled && wiperDone.size < WIPER_COMBOS.size) {
            for (c in WIPER_COMBOS) {
                if (comboAfter == c && wiperDone.add(c)) wiperUntil = nowMs + WIPER_ON_MS
            }
        }
    }

    /** 롱노트가 끊겼을 때: 콤보가 0이 된다 */
    fun onComboBroken(nowMs: Long) {
        combo = 0
    }

    /** 곡을 끝까지 쳤을 때(완주) 실내등 연출 시작 */
    fun onComplete(nowMs: Long) {
        completed = true
        cabinStart = nowMs
    }

    /** 실내등 연출이 끝나는 시각(ms). 완주 전이면 null */
    fun cabinEndMs(): Long? = if (completed) cabinStart + CABIN_STEP_MS * 2 * CABIN_BLINKS else null

    private fun wantedAmbient(nowMs: Long): AmbientTarget {
        if (missed) return AmbientTarget(false, color, 0)
        val flashing = nowMs < flashUntil
        val b = if (flashing) FLASH_BRIGHTNESS else baseBrightness(combo)
        return AmbientTarget(true, color, b)
    }

    private fun wantedCabin(nowMs: Long): Boolean? {
        if (!completed) return null
        val step = (nowMs - cabinStart) / CABIN_STEP_MS
        if (step >= CABIN_BLINKS * 2) return false
        return step % 2 == 0L
    }

    /** 지금 원하는 상태 중 직전과 달라진 것만 돌려준다 */
    fun poll(nowMs: Long): Desired {
        val a = if (ambientEnabled) wantedAmbient(nowMs).takeIf { it != emittedAmbient } else null
        val wantW = if (wiperEnabled && wiperUntil != Long.MIN_VALUE) nowMs < wiperUntil else null
        val w = wantW?.takeIf { it != emittedWiper }
        val wantC = wantedCabin(nowMs)
        val c = wantC?.takeIf { it != emittedCabin }
        if (a != null) emittedAmbient = a
        if (w != null) emittedWiper = w
        if (c != null) emittedCabin = c
        return Desired(a, w, c)
    }

    /** 이미 내보낸 것으로 치지 않고 다시 보내게 한다(전송 실패 등) */
    fun forget() {
        emittedAmbient = null; emittedWiper = null; emittedCabin = null
    }
}
