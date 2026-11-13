package com.dontstudyy.carbeat.reaction

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.constant.control.LightState
import ai.pleos.playground.vehicle.constant.control.LightSwitch
import ai.pleos.playground.vehicle.constant.control.WiperSwitch
import ai.pleos.playground.vehicle.listener.AmbientLightBrightnessColorListener
import ai.pleos.playground.vehicle.listener.AmbientLightBrightnessListener
import ai.pleos.playground.vehicle.listener.LightStateListener
import android.os.SystemClock
import android.util.Log
import com.dontstudyy.carbeat.vehicle.VehicleSignals

/**
 * 앰비언트·와이퍼·실내등을 실제 차량에 쓰는 층(Pleos Vehicle SDK).
 * 쓰기는 `check…Capabilities`가 알려 준 area 객체로만 한다(직접 만든 area는 실패한다).
 * 앰비언트 **스위치** API는 쓰지 않는다: 에뮬레이터에서 쓰기 콜백은 성공하지만 Virtual Console의 앰비언트 모드에
 * 반영되지 않고, SDK가 읽어 주는 상태도 Virtual Console과 다르다(항상 OFF). 그래서 "앰비언트 끔"은 밝기 0으로 한다.
 * 쓰기 전 값을 읽어 두었다가([saveState]) 끝나면 되돌린다([restoreState]).
 * 실패해도 예외를 던지지 않고 로그만 남긴다(게임은 계속된다).
 */
class VehicleReactor(
    private val signals: VehicleSignals,
    /** 앱이 와이퍼를 움직였음을 알린다(앱이 켠 와이퍼를 입력으로 읽지 않게) */
    private val onWiperAppAction: (Long) -> Unit,
) {
    companion object {
        private const val TAG = "CARBEAT"
    }

    private var vehicle: Vehicle? = null
    private var colorArea: SeatArea? = null
    private var brightArea: SeatArea? = null
    private var wiperArea: WindowArea? = null

    private var savedColor: Int? = null
    private var savedBrightness: Int? = null
    private var savedWiper: WiperSwitch? = null
    private var savedCabin: LightSwitch? = null
    private var haveSaved = false

    val isReady: Boolean get() = colorArea != null && brightArea != null

    private fun fail(what: String): (Exception) -> Unit = { e -> Log.w(TAG, "REACT FAIL $what ${e.javaClass.simpleName}: ${e.message}") }

    private val colorListener = object : AmbientLightBrightnessColorListener {
        override fun onBrightnessColorUpdated(p0: Int?) { signals.update { it.copy(ambientArgb = p0) } }
        override fun onFailed(e: Exception) { Log.w(TAG, "ambient color listener failed: $e") }
    }
    private val brightListener = object : AmbientLightBrightnessListener {
        override fun onBrightnessUpdated(p0: Int?) { signals.update { it.copy(ambientBrightness = p0) } }
        override fun onFailed(e: Exception) { Log.w(TAG, "ambient brightness listener failed: $e") }
    }
    private val cabinListener = object : LightStateListener {
        override fun onLightStateUpdated(p0: LightState?) { signals.update { it.copy(cabinOn = p0?.let { s -> s != LightState.OFF }) } }
        override fun onFailed(e: Exception) { Log.w(TAG, "cabin listener failed: $e") }
    }

    fun attach(v: Vehicle) {
        vehicle = v
        try {
            val l = v.getLight()
            l.checkAmbientLightBrightnessColorCapabilities({ _, areas ->
                colorArea = areas.firstOrNull()
                colorArea?.let { a ->
                    runCatching { l.registerAmbientLightBrightnessColorListener(a, colorListener) }
                    l.getAmbientLightBrightnessColor(a, { c -> signals.update { it.copy(ambientArgb = c) } }, fail("read color"))
                }
            }, fail("color capability"))
            l.checkAmbientLightBrightnessCapabilities({ _, map ->
                brightArea = map.keys.firstOrNull()
                brightArea?.let { a ->
                    runCatching { l.registerAmbientLightBrightnessListener(a, brightListener) }
                    l.getAmbientLightBrightness(a, { b -> signals.update { it.copy(ambientBrightness = b) } }, fail("read brightness"))
                }
            }, fail("brightness capability"))
            runCatching { l.registerCabinLightState(cabinListener) }
            l.getCurrentCabinLightState({ s -> signals.update { it.copy(cabinOn = s != LightState.OFF) } }, fail("read cabin state"))
            val w = v.getWiper()
            w.checkWiperSwitchCapabilities({ _, map ->
                wiperArea = map.keys.firstOrNull { a -> a.values.any { it.name == "FRONT_WINDSHIELD" } } ?: map.keys.firstOrNull()
            }, fail("wiper capability"))
        } catch (t: Throwable) {
            Log.w(TAG, "VehicleReactor attach FAIL ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    fun detach() {
        val v = vehicle ?: return
        runCatching { colorArea?.let { v.getLight().unregisterAmbientLightBrightnessColorListener(it, colorListener) } }
        runCatching { brightArea?.let { v.getLight().unregisterAmbientLightBrightnessListener(it, brightListener) } }
        runCatching { v.getLight().unregisterCabinLightState(cabinListener) }
        vehicle = null
        colorArea = null; brightArea = null; wiperArea = null
    }

    /** 쓰기 전 값을 읽어 둔다. 다 읽거나 실패하면 [onDone]을 부른다(최대 약 1.5초는 호출자가 기다린다). */
    fun saveState(onDone: () -> Unit) {
        val v = vehicle
        if (v == null || !isReady) { onDone(); return }
        haveSaved = false
        var pending = 4
        var finished = false
        fun step() {
            pending--
            if (pending <= 0 && !finished) {
                finished = true
                haveSaved = true
                Log.i(TAG, "REACT saved color=${savedColor?.let { "0x%08X".format(it) }} brightness=$savedBrightness wiper=$savedWiper cabin=$savedCabin")
                onDone()
            }
        }
        val l = v.getLight()
        l.getAmbientLightBrightnessColor(colorArea!!, { c -> savedColor = c; step() }, { e -> fail("save color")(e); step() })
        l.getAmbientLightBrightness(brightArea!!, { b -> savedBrightness = b; step() }, { e -> fail("save brightness")(e); step() })
        l.getCabinLightSwitch({ s -> savedCabin = s; step() }, { e -> fail("save cabin")(e); step() })
        val wa = wiperArea
        if (wa != null) v.getWiper().getCurrentWiperSwitch(wa, { s -> savedWiper = s; step() }, { e -> fail("save wiper")(e); step() }) else step()
    }

    /** 앰비언트를 쓴다. 켜짐이 아니면(MISS) 밝기 0으로 "끈다". 스위치는 쓰지 않는다. */
    fun applyAmbient(t: AmbientTarget) {
        val v = vehicle ?: return
        if (!isReady) return
        val l = v.getLight()
        if (!t.on) {
            l.setAmbientLightBrightness(brightArea!!, 0, { _, v -> signals.update { it.copy(ambientBrightness = v) } }, fail("ambient off"))
            Log.i(TAG, "REACT ambient off (brightness 0)")
            return
        }
        // 쓰기 콜백이 성공한 값을 화면 상태에 반영한다(차량 리스너는 앱 자신의 쓰기에는 이벤트를 주지 않는다)
        l.setAmbientLightBrightnessColor(colorArea!!, t.argb, { _, c -> signals.update { it.copy(ambientArgb = c) } }, fail("ambient color"))
        l.setAmbientLightBrightness(brightArea!!, t.brightness, { _, b -> signals.update { it.copy(ambientBrightness = b) } }, fail("ambient brightness"))
        Log.i(TAG, "REACT ambient color=0x%08X brightness=${t.brightness}".format(t.argb))
    }

    fun applyWiper(on: Boolean) {
        val v = vehicle ?: return
        val wa = wiperArea ?: return
        onWiperAppAction(SystemClock.elapsedRealtimeNanos())
        v.getWiper().setWiperSwitch(wa, if (on) WiperSwitch.CONTINUOUS_LEVEL_1 else WiperSwitch.OFF, { _, _ -> }, fail("wiper"))
        Log.i(TAG, "REACT wiper ${if (on) "LEVEL_1" else "OFF"}")
    }

    fun applyCabin(on: Boolean) {
        val v = vehicle ?: return
        v.getLight().setCabinLightSwitch(if (on) LightSwitch.ON else LightSwitch.OFF, { sw -> signals.update { it.copy(cabinOn = sw == LightSwitch.ON) } }, fail("cabin"))
        Log.i(TAG, "REACT cabin ${if (on) "ON" else "OFF"}")
    }

    /** 곡이 끝나거나 중단될 때 읽어 둔 값으로 되돌린다 */
    fun restoreState() {
        val v = vehicle ?: return
        if (!haveSaved || !isReady) {
            // 읽어 둔 값이 없으면 안전한 쪽으로만: 실내등 끔, 와이퍼 끔
            runCatching { v.getLight().setCabinLightSwitch(LightSwitch.OFF, { }, fail("restore cabin")) }
            wiperArea?.let { wa ->
                onWiperAppAction(SystemClock.elapsedRealtimeNanos())
                runCatching { v.getWiper().setWiperSwitch(wa, WiperSwitch.OFF, { _, _ -> }, fail("restore wiper")) }
            }
            return
        }
        val l = v.getLight()
        savedColor?.let { c -> l.setAmbientLightBrightnessColor(colorArea!!, c, { _, v -> signals.update { it.copy(ambientArgb = v) } }, fail("restore color")) }
        savedBrightness?.let { b -> l.setAmbientLightBrightness(brightArea!!, b, { _, v -> signals.update { it.copy(ambientBrightness = v) } }, fail("restore brightness")) }
        savedCabin?.let { s -> l.setCabinLightSwitch(s, { }, fail("restore cabin")) }
        wiperArea?.let { wa ->
            onWiperAppAction(SystemClock.elapsedRealtimeNanos())
            v.getWiper().setWiperSwitch(wa, savedWiper ?: WiperSwitch.OFF, { _, _ -> }, fail("restore wiper"))
        }
        Log.i(TAG, "REACT restored")
        haveSaved = false
    }
}
