package com.dontstudyy.carbeat.gate

/** 기어 위치. AAOS GEAR_SELECTION 값(P=4, R=2, N=1, D=8)을 변환한다. */
enum class Gear {
    PARK, REVERSE, NEUTRAL, DRIVE, UNKNOWN;

    companion object {
        fun fromAaos(value: Int): Gear = when (value) {
            4 -> PARK
            2 -> REVERSE
            1 -> NEUTRAL
            8 -> DRIVE
            else -> UNKNOWN
        }
    }
}

/**
 * 게이트 판단에 쓰는 차량 값.
 * [uxRestricted]: CarUxRestrictions.isRequiresDistractionOptimization. 아직 못 읽었으면 null.
 */
data class VehicleSnapshot(
    val gear: Gear = Gear.UNKNOWN,
    val uxRestricted: Boolean? = null,
)

data class GateState(
    val open: Boolean,
    val gearOk: Boolean,
    val stopped: Boolean,
) {
    val reason: String
        get() = when {
            open -> ""
            !gearOk -> "기어가 P가 아닙니다"
            else -> "주행 규제가 켜져 있습니다"
        }
}

/**
 * 주차 게이트. 기어 P와 주행 규제 해제(정차) 둘 다 확인돼야 열린다.
 * 값을 아직 못 읽었거나 알 수 없으면 닫는다(안전 쪽).
 * 주차 브레이크는 에뮬레이터에서 값이 바뀌지 않아 조건에서 뺐다.
 */
object ParkingGate {
    fun evaluate(s: VehicleSnapshot): GateState {
        val gearOk = s.gear == Gear.PARK
        val stopped = s.uxRestricted == false
        return GateState(open = gearOk && stopped, gearOk = gearOk, stopped = stopped)
    }
}
