package com.dontstudyy.carbeat.input

import android.content.Context
import com.dontstudyy.carbeat.game.Lane

/** 박자 보정 결과(조작계별 입력 지연, 초)를 기기에 저장한다. 와이퍼는 브레이크 값을 쓴다. */
class CalibrationStore(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("carbeat_calibration", Context.MODE_PRIVATE)

    var brakeSec: Double
        get() = prefs.getFloat("brake", 0f).toDouble()
        private set(v) { prefs.edit().putFloat("brake", v.toFloat()).apply() }

    var steerSec: Double
        get() = prefs.getFloat("steer", 0f).toDouble()
        private set(v) { prefs.edit().putFloat("steer", v.toFloat()).apply() }

    val isCalibrated: Boolean get() = prefs.contains("brake") && prefs.contains("steer")

    fun save(brake: Double, steer: Double) {
        brakeSec = brake
        steerSec = steer
    }

    /** 판정에서 입력 시각에서 뺄 지연(초) */
    fun delayFor(lane: Lane): Double = if (lane.isSteer) steerSec else brakeSec
}
