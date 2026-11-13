package com.dontstudyy.carbeat.input

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.constant.control.WiperState
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.SteeringWheelAngleListener
import ai.pleos.playground.vehicle.listener.WiperStateListener
import android.os.SystemClock
import android.util.Log

/**
 * 차량 신호(브레이크, 핸들, 와이퍼)를 구독해 [InputEvent]로 바꿔 [listener]에 전달한다.
 * 이벤트 시각은 리스너가 불린 순간의 elapsedRealtimeNanos. 콜백은 차량 SDK 스레드에서 온다.
 */
class InputHub {
    companion object {
        private const val TAG = "CARBEAT"
    }

    @Volatile
    var listener: ((InputEvent) -> Unit)? = null

    private val brake = BrakeConverter()
    private val steer = SteerConverter()
    val wiper = WiperConverter()

    private var vehicle: Vehicle? = null
    private var wiperArea: ai.pleos.playground.vehicle.api.model.area.WindowArea? = null

    private fun emit(e: InputEvent) {
        Log.i(TAG, "INPUT lane=${e.lane} down=${e.down} n=${e.timeNanos / 1_000_000.0}")
        listener?.invoke(e)
    }

    private val brakeListener = object : BrakePedalStatusListener {
        override fun onBrakePedalStatusUpdated(p0: Boolean?) {
            val t = SystemClock.elapsedRealtimeNanos()
            val e = synchronized(brake) { brake.onValue(p0, t) }
            if (e != null) emit(e)
        }

        override fun onFailed(e: Exception) { Log.w(TAG, "brake listener failed: $e") }
    }

    private val steerListener = object : SteeringWheelAngleListener {
        override fun onSteeringWheelAngleUpdated(p0: Float?) {
            val t = SystemClock.elapsedRealtimeNanos()
            val list = synchronized(steer) { steer.onAngle(p0, t) }
            list.forEach { emit(it) }
        }

        override fun onFailed(e: Exception) { Log.w(TAG, "steering listener failed: $e") }
    }

    private val wiperListener = object : WiperStateListener {
        override fun onWiperStateUpdated(p0: WiperState?) {
            if (p0 == null) return
            val t = SystemClock.elapsedRealtimeNanos()
            val e = synchronized(wiper) { wiper.onState(p0 != WiperState.OFF, t) }
            if (e != null) emit(e)
        }

        override fun onFailed(e: Exception) { Log.w(TAG, "wiper listener failed: $e") }
    }

    fun attach(v: Vehicle) {
        detach()
        vehicle = v
        try {
            v.getBrake().registerBrakePedalStatus(brakeListener)
            v.getSteeringWheel().registerSteeringWheelAngle(steerListener)
            val w = v.getWiper()
            w.checkWiperStateCapabilities({ _, map ->
                // 앞 유리 와이퍼만 입력으로 쓴다
                val area = map.keys.firstOrNull { a -> a.values.any { it.name == "FRONT_WINDSHIELD" } } ?: map.keys.firstOrNull()
                if (area != null) {
                    wiperArea = area
                    w.registerWiperState(area, wiperListener)
                }
            }, { e -> Log.w(TAG, "wiper capability failed: $e") })
        } catch (t: Throwable) {
            Log.w(TAG, "InputHub attach FAIL ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    fun detach() {
        val v = vehicle ?: return
        runCatching { v.getBrake().unregisterBrakePedalStatus(brakeListener) }
        runCatching { v.getSteeringWheel().unregisterSteeringWheelAngle(steerListener) }
        runCatching { wiperArea?.let { v.getWiper().unregisterWiperState(it, wiperListener) } }
        wiperArea = null
        vehicle = null
    }
}
