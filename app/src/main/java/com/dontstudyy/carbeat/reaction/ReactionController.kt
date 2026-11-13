package com.dontstudyy.carbeat.reaction

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.dontstudyy.carbeat.game.Judgment
import com.dontstudyy.carbeat.game.Lane

/**
 * 한 곡 동안의 차량 반응을 진행한다. 메인 스레드 전용.
 *
 * - 곡 시작 시 [begin]: 차량 값을 읽어 둔 뒤 반응을 시작한다.
 * - 판정마다 [onJudged] / [onComboBroken], 완주하면 [complete](실내등 연출이 끝나면 스스로 원상 복구).
 * - 중단·일시정지·주행 전환·앱 종료는 [abort]: 즉시 읽어 둔 값으로 되돌린다.
 * - 앰비언트 쓰기는 [AMBIENT_MIN_INTERVAL_MS]에 한 번 이하로 제한하고, 밀리면 최신 명령만 남긴다(초당 3회 미만).
 * - 프레임 속도와 무관하게 자체 타이머([TICK_MS])로 돈다.
 */
class ReactionController(
    private val reactor: VehicleReactor,
    private val settings: ReactionSettings,
) {
    companion object {
        private const val TAG = "CARBEAT"
        const val TICK_MS = 50L
        const val AMBIENT_MIN_INTERVAL_MS = 340L
        private const val SAVE_TIMEOUT_MS = 1500L
        private const val NEVER = -1_000_000_000L
    }

    private val main = Handler(Looper.getMainLooper())
    private var planner: ReactionPlanner? = null
    private var active = false
    private var started = false // 값 저장이 끝나 반응을 내보내는 중
    private var pendingAmbient: AmbientTarget? = null
    private var lastAmbientSendMs = NEVER
    private var beginMs = 0L

    private fun nowMs() = SystemClock.elapsedRealtime()

    private val ticker = object : Runnable {
        override fun run() {
            if (!active) return
            tick()
            main.postDelayed(this, TICK_MS)
        }
    }

    /** 반응을 켜 두었으면 시작한다. 이미 진행 중이면 먼저 되돌린다. */
    fun begin() {
        if (active) abort()
        if (!settings.vehicleReaction) {
            Log.i(TAG, "REACT off by settings")
            return
        }
        active = true
        started = false
        pendingAmbient = null
        lastAmbientSendMs = NEVER
        beginMs = nowMs()
        planner = ReactionPlanner(ambientEnabled = true, wiperEnabled = settings.wiperReaction).also { it.onStart(beginMs) }
        reactor.saveState {
            if (active && !started) {
                started = true
                planner?.forget()
                Log.i(TAG, "REACT begin wiper=${settings.wiperReaction}")
            }
        }
        // 저장이 끝나지 않아도 일정 시간 뒤에는 시작(게임은 멈추지 않는다)
        main.postDelayed({ if (active && !started) { started = true; planner?.forget() } }, SAVE_TIMEOUT_MS)
        main.post(ticker)
    }

    fun onJudged(lane: Lane, judgment: Judgment, comboAfter: Int) {
        if (!active) return
        planner?.onJudged(lane, judgment, comboAfter, nowMs())
    }

    fun onComboBroken() {
        if (!active) return
        planner?.onComboBroken(nowMs())
    }

    /** 곡을 끝까지 쳤다. 실내등 연출을 하고, 끝나면 스스로 되돌린다. */
    fun complete() {
        if (!active) return
        planner?.onComplete(nowMs())
    }

    /** 즉시 중단하고 읽어 둔 값으로 되돌린다 */
    fun abort() {
        if (!active) return
        active = false
        main.removeCallbacks(ticker)
        pendingAmbient = null
        planner = null
        reactor.restoreState()
    }

    private fun tick() {
        val p = planner ?: return
        val now = nowMs()
        if (started) {
            val d = p.poll(now)
            d.ambient?.let { pendingAmbient = it } // 최신 명령만 남긴다
            val pend = pendingAmbient
            if (pend != null && now - lastAmbientSendMs >= AMBIENT_MIN_INTERVAL_MS) {
                reactor.applyAmbient(pend)
                lastAmbientSendMs = now
                pendingAmbient = null
            }
            d.wiperOn?.let { reactor.applyWiper(it) }
            d.cabinOn?.let { reactor.applyCabin(it) }
        }
        // 완주 연출이 끝났으면 원상 복구하고 종료
        val end = p.cabinEndMs()
        if (end != null && now > end + 200) abort()
    }
}
