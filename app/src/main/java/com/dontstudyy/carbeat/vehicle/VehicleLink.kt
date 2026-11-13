package com.dontstudyy.carbeat.vehicle

import ai.pleos.playground.vehicle.Vehicle
import android.car.Car
import android.car.drivingstate.CarUxRestrictionsManager
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.dontstudyy.carbeat.gate.Gear
import com.dontstudyy.carbeat.gate.VehicleSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 차량 연결 층. AAOS CarPropertyManager(기어), CarUxRestrictionsManager(주행 규제),
 * Pleos Vehicle SDK(입력·반응용, 이후 단계)를 초기화하고 게이트용 값을 [snapshot]으로 내보낸다.
 * 읽기·구독이 실패해도 앱은 멈추지 않고 snapshot이 UNKNOWN/null로 남아 게이트가 닫힌다.
 */
class VehicleLink(private val ctx: Context) {
    companion object {
        private const val TAG = "CARBEAT"
        /** AAOS VehiclePropertyIds.GEAR_SELECTION */
        private const val PROP_GEAR_SELECTION = 0x11400400
    }

    /** 차량 입력(브레이크·핸들·와이퍼) 허브. start() 뒤에 Vehicle SDK에 연결된다. */
    val input = com.dontstudyy.carbeat.input.InputHub()

    private val _snapshot = MutableStateFlow(VehicleSnapshot())
    val snapshot: StateFlow<VehicleSnapshot> = _snapshot.asStateFlow()

    /** Pleos Vehicle SDK. 단계 3·4(입력, 반응)에서 쓴다. start() 전에는 null. */
    var vehicle: Vehicle? = null
        private set

    private var car: Car? = null
    private var propertyManager: CarPropertyManager? = null
    private var uxManager: CarUxRestrictionsManager? = null
    private var started = false

    private val gearCallback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            val v = value.value as? Int ?: return
            setGear(Gear.fromAaos(v), "event")
        }

        override fun onErrorEvent(propId: Int, zone: Int) {
            Log.w(TAG, "GEAR error prop=$propId zone=$zone")
        }
    }

    private val uxListener = CarUxRestrictionsManager.OnUxRestrictionsChangedListener { r ->
        setUx(r.isRequiresDistractionOptimization, "event")
    }

    fun start() {
        if (started) return
        started = true
        try {
            vehicle = Vehicle(ctx).also { it.initialize(); input.attach(it) }
        } catch (t: Throwable) {
            Log.w(TAG, "Vehicle SDK init FAIL ${t.javaClass.simpleName}: ${t.message}")
        }
        try {
            val c = Car.createCar(ctx)
            car = c
            if (c == null) {
                Log.w(TAG, "Car.createCar returned null")
                return
            }
            startGear(c)
            startUx(c)
        } catch (t: Throwable) {
            Log.w(TAG, "Car init FAIL ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    private fun startGear(c: Car) {
        try {
            val pm = c.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
            propertyManager = pm
            setGear(Gear.fromAaos(pm.getIntProperty(PROP_GEAR_SELECTION, 0)), "initial")
            pm.registerCallback(gearCallback, PROP_GEAR_SELECTION, CarPropertyManager.SENSOR_RATE_ONCHANGE)
        } catch (t: Throwable) {
            Log.w(TAG, "GEAR init FAIL ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    private fun startUx(c: Car) {
        try {
            val ux = c.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager
            uxManager = ux
            setUx(ux.currentCarUxRestrictions.isRequiresDistractionOptimization, "initial")
            ux.registerListener(uxListener)
        } catch (t: Throwable) {
            Log.w(TAG, "UX init FAIL ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    fun stop() {
        if (!started) return
        started = false
        runCatching { propertyManager?.unregisterCallback(gearCallback) }
        runCatching { uxManager?.unregisterListener() }
        runCatching { car?.disconnect() }
        input.detach()
        runCatching { vehicle?.release() }
        propertyManager = null
        uxManager = null
        car = null
        vehicle = null
    }

    private fun setGear(g: Gear, src: String) {
        _snapshot.update { it.copy(gear = g) }
        Log.i(TAG, "GATE_IN gear=$g src=$src n=${SystemClock.elapsedRealtimeNanos() / 1_000_000}")
    }

    private fun setUx(restricted: Boolean, src: String) {
        _snapshot.update { it.copy(uxRestricted = restricted) }
        Log.i(TAG, "GATE_IN ux=$restricted src=$src n=${SystemClock.elapsedRealtimeNanos() / 1_000_000}")
    }
}
