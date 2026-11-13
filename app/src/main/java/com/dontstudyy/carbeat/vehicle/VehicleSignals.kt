package com.dontstudyy.carbeat.vehicle

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 화면(실내 그림, 차량 신호 띠)이 그리는 값. 게임 내부 상태가 아니라 **차량에서 읽은 값**이다:
 * 브레이크·핸들·와이퍼는 입력 리스너 값, 앰비언트·실내등은 차량 리스너가 알려 준 값.
 * 읽은 적이 없으면 null.
 */
data class SignalState(
    val brakePressed: Boolean? = null,
    val steerDeg: Float? = null,
    val wiperOn: Boolean? = null,
    val ambientArgb: Int? = null,
    val ambientBrightness: Int? = null,
    val cabinOn: Boolean? = null,
)

class VehicleSignals {
    private val _state = MutableStateFlow(SignalState())
    val state: StateFlow<SignalState> = _state.asStateFlow()

    fun update(f: (SignalState) -> SignalState) = _state.update(f)
}
