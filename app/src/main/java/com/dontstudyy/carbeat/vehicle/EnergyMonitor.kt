package com.dontstudyy.carbeat.vehicle

import ai.pleos.playground.vehicle.Vehicle
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class EnergyState(val soc: Int? = null, val charging: Boolean? = null)

/**
 * 배터리 잔량(SOC)과 충전 스위치 상태를 읽는다(홈의 충전 추천용).
 * 충전 스위치는 변화 알림 API가 없어 [POLL_MS]마다 읽는다. SOC는 몇 초 간격으로 함께 읽는다.
 */
class EnergyMonitor {
    companion object {
        private const val TAG = "CARBEAT"
        const val POLL_MS = 2000L
    }

    private val _state = MutableStateFlow(EnergyState())
    val state: StateFlow<EnergyState> = _state.asStateFlow()

    private val main = Handler(Looper.getMainLooper())
    private var vehicle: Vehicle? = null
    private var running = false

    private val poll = object : Runnable {
        override fun run() {
            if (!running) return
            readOnce()
            main.postDelayed(this, POLL_MS)
        }
    }

    private fun readOnce() {
        val ev = vehicle?.getEvBattery() ?: return
        try {
            ev.getEvBatteryStateOfCharge({ soc -> _state.update { it.copy(soc = soc) } }, { e -> Log.w(TAG, "SOC read failed: $e") })
            ev.getEvChargeSwitchState({ on -> _state.update { it.copy(charging = on) } }, { e -> Log.w(TAG, "charge switch read failed: $e") })
        } catch (t: Throwable) {
            Log.w(TAG, "energy read FAIL ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    fun start(v: Vehicle) {
        vehicle = v
        if (running) return
        running = true
        main.post(poll)
    }

    fun stop() {
        running = false
        main.removeCallbacks(poll)
        vehicle = null
    }
}
