package com.dontstudyy.carbeat.debug

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.constant.control.WiperState
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.SteeringWheelAngleListener
import ai.pleos.playground.vehicle.listener.WiperStateListener
import android.car.Car
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log

/**
 * 단계 0 실험 모음 (debug 전용).
 * 로그 한 줄 = `CARBEAT <id> w=<epoch ms> n=<elapsedRealtimeNanos/1e6> ...`
 */
class Experiments private constructor(private val ctx: Context) {
    companion object {
        private const val TAG = "CARBEAT"
        private const val PROP_PARKING_BRAKE_ON = 0x11200402

        @Volatile
        private var inst: Experiments? = null
        fun get(c: Context): Experiments = inst ?: synchronized(this) { inst ?: Experiments(c).also { inst = it } }
    }

    private val main = Handler(Looper.getMainLooper())
    private var vehicleObj: Vehicle? = null
    private val vehicle: Vehicle
        get() = vehicleObj ?: Vehicle(ctx).also { it.initialize(); vehicleObj = it }
    private var car: Car? = null

    private fun log(id: String, msg: String) {
        val n = SystemClock.elapsedRealtimeNanos() / 1_000_000.0
        Log.i(TAG, "$id w=${System.currentTimeMillis()} n=${"%.1f".format(n)} $msg")
    }

    private fun fail(id: String, what: String): (Exception) -> Unit =
        { e -> log(id, "FAIL $what ${e.javaClass.simpleName}: ${e.message}") }

    fun run(id: String, arg: String) {
        try {
            when (id) {
                "watch" -> watch()
                "parking" -> parking()
                "ambient" -> ambient()
                "state" -> state()
                "ambsw" -> ambsw(arg)
                else -> log("RUN", "unknown id=$id")
            }
        } catch (t: Throwable) {
            log(id, "EXCEPTION ${t.javaClass.name}: ${t.message}")
        }
    }

    // ---- 입력 신호 구독: 브레이크, 핸들, 와이퍼, 기어. 이벤트마다 시각을 남긴다 ----
    private var watching = false
    private fun watch() {
        if (watching) { log("watch", "already"); return }
        watching = true
        vehicle.getBrake().registerBrakePedalStatus(object : BrakePedalStatusListener {
            override fun onBrakePedalStatusUpdated(p0: Boolean?) { log("EVT_BRAKE", "v=$p0") }
            override fun onFailed(e: Exception) { log("EVT_BRAKE", "onFailed $e") }
        })
        vehicle.getSteeringWheel().registerSteeringWheelAngle(object : SteeringWheelAngleListener {
            override fun onSteeringWheelAngleUpdated(p0: Float?) { log("EVT_STEER", "v=$p0") }
            override fun onFailed(e: Exception) { log("EVT_STEER", "onFailed $e") }
        })
        vehicle.getGear().registerGearSelection(object : GearSelectionListener {
            override fun onSelected(p0: VehicleGear?) { log("EVT_GEAR", "v=$p0") }
            override fun onFailed(e: Exception) { log("EVT_GEAR", "onFailed $e") }
        })
        val w = vehicle.getWiper()
        w.checkWiperStateCapabilities({ _, map ->
            log("watch", "wiperState capability areas=${map.entries.map { it.key.values.toList() to it.value }}")
            for (a in map.keys) {
                w.registerWiperState(a, object : WiperStateListener {
                    override fun onWiperStateUpdated(p0: WiperState?) { log("EVT_WIPER", "area=${a.values.toList()} v=$p0") }
                    override fun onFailed(e: Exception) { log("EVT_WIPER", "onFailed $e") }
                })
            }
        }, fail("watch", "checkWiperStateCapabilities"))
        log("watch", "registered brake/steer/gear/wiper")
    }

    // ---- 주차 브레이크 읽기 (AAOS PARKING_BRAKE_ON) ----
    private fun parking() {
        val c = car ?: Car.createCar(ctx).also { car = it }
        val pm = c.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
        val cfg = pm.propertyList.firstOrNull { it.propertyId == PROP_PARKING_BRAKE_ON }
        log("parking", "inPropertyList=${cfg != null} cfg=$cfg")
        try {
            log("parking", "getBoolean=${pm.getBooleanProperty(PROP_PARKING_BRAKE_ON, 0)}")
        } catch (t: Throwable) {
            log("parking", "getBoolean FAIL ${t.javaClass.simpleName}: ${t.message}")
        }
        try {
            val ok = pm.registerCallback(object : CarPropertyManager.CarPropertyEventCallback {
                override fun onChangeEvent(v: CarPropertyValue<*>) { log("EVT_PARKING", "v=${v.value}") }
                override fun onErrorEvent(propId: Int, zone: Int) { log("EVT_PARKING", "ERR $propId zone=$zone") }
            }, PROP_PARKING_BRAKE_ON, CarPropertyManager.SENSOR_RATE_ONCHANGE)
            log("parking", "registerCallback=$ok")
        } catch (t: Throwable) {
            log("parking", "register FAIL ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    // ---- 앰비언트: 0.5초 간격 10회 쓰기, 레인 색 4개 순환. 끝나면 원래 값으로 ----
    private val laneColors = intArrayOf(0xFFFF7A1A.toInt(), 0xFFFF3355.toInt(), 0xFFFFC233.toInt(), 0xFF4CC9FF.toInt())

    private fun ambient() {
        val l = vehicle.getLight()
        l.checkAmbientLightBrightnessColorCapabilities({ cfg, areas ->
            log("ambient", "color capability cfg=$cfg areas=${areas.map { it.values.toList() }}")
            val a = areas.firstOrNull() ?: return@checkAmbientLightBrightnessColorCapabilities
            l.getAmbientLightBrightnessColor(a, { orig ->
                log("ambient", "orig=${orig?.let { "0x%08X".format(it) }}")
                var okCnt = 0
                var failCnt = 0
                val t00 = SystemClock.elapsedRealtime()
                for (i in 0 until 10) {
                    main.postDelayed({
                        val color = laneColors[i % 4]
                        val t0 = SystemClock.elapsedRealtime()
                        l.setAmbientLightBrightnessColor(a, color, { _, v ->
                            okCnt++
                            log("ambient", "write#$i ok dt=${SystemClock.elapsedRealtime() - t0}ms sent=0x%08X cb=0x%08X".format(color, v ?: 0))
                        }, { e -> failCnt++; log("ambient", "write#$i FAIL ${e.message}") })
                    }, i * 500L)
                }
                main.postDelayed({
                    log("ambient", "summary ok=$okCnt fail=$failCnt total=${SystemClock.elapsedRealtime() - t00}ms")
                    l.getAmbientLightBrightnessColor(a, { now ->
                        log("ambient", "reread=${now?.let { "0x%08X".format(it) }} lastSent=0x%08X".format(laneColors[9 % 4]))
                        l.setAmbientLightBrightnessColor(a, orig ?: 0xFF00E5FF.toInt(),
                            { _, r -> log("ambient", "restored=0x%08X".format(r ?: 0)) }, fail("ambient", "restore"))
                    }, fail("ambient", "reread"))
                }, 10 * 500L + 1500)
            }, fail("ambient", "getColor"))
        }, fail("ambient", "colorCapability"))
    }

    // ---- 현재 값 덤프 ----
    private fun state() {
        vehicle.getGear().getCurrentGearSelectionState({ g -> log("state", "gear=$g") }, fail("state", "gear"))
        vehicle.getBrake().getCurrentBrakePedalStatus({ b -> log("state", "brake=$b") }, fail("state", "brake"))
        vehicle.getSteeringWheel().getCurrentSteeringWheelAngle({ a -> log("state", "steer=$a") }, fail("state", "steer"))
        val w = vehicle.getWiper()
        w.checkWiperSwitchCapabilities({ _, map ->
            log("state", "wiperSwitch areas=${map.entries.map { it.key.values.toList() to it.value }}")
            for (a in map.keys) {
                w.getCurrentWiperSwitch(a, { s -> log("state", "wiperSwitch[${a.values.toList()}]=$s") }, fail("state", "wiperSwitch"))
            }
        }, fail("state", "wiperCapability"))
        vehicle.getLight().getCurrentCabinLightState({ s -> log("state", "cabin=$s") }, fail("state", "cabin"))
    }

    // ---- 앰비언트 스위치 쓰기가 Virtual Console에 어떻게 반영되는지: arg = ON / OFF / AUTOMATIC / DAYTIME_RUNNING ----
    private fun ambsw(arg: String) {
        val l = vehicle.getLight()
        l.checkAmbientLightSwitchCapabilities({ _, map ->
            log("ambsw", "capability ${map.entries.map { it.key.values.toList() to it.value }}")
            val a = map.keys.firstOrNull() ?: return@checkAmbientLightSwitchCapabilities
            l.getAmbientLightSwitch(a, { cur -> log("ambsw", "read switch=$cur") }, fail("ambsw", "read"))
            val v = runCatching { ai.pleos.playground.vehicle.constant.control.LightSwitch.valueOf(arg) }.getOrNull() ?: return@checkAmbientLightSwitchCapabilities
            l.setAmbientLightSwitch(a, v, { _, r -> log("ambsw", "wrote=$v cb=$r") }, fail("ambsw", "write $v"))
        }, fail("ambsw", "capability"))
    }
}
