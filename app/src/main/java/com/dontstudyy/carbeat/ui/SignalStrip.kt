package com.dontstudyy.carbeat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dontstudyy.carbeat.gate.Gear
import com.dontstudyy.carbeat.vehicle.VehicleSignals

/** 아래 띠: 기어, 브레이크 페달, 핸들 조향각, 와이퍼, 앰비언트, 실내등. 모두 차량에서 읽은 값이다. */
@Composable
fun SignalStrip(signals: VehicleSignals, gear: Gear) {
    val s by signals.state.collectAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFF10131C))
            .padding(horizontal = 24.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cell("기어") { Value(gearLabel(gear), if (gear == Gear.PARK) Color(0xFF6EE7A8) else Color(0xFFFFC233)) }
        Cell("브레이크") { Value(onOff(s.brakePressed, "밟음", "뗌"), if (s.brakePressed == true) LaneColors.Brake else Color.Gray) }
        Cell("핸들") {
            Value(
                s.steerDeg?.let { d -> if (Math.abs(d) < 0.5f) "0°" else "%+.0f°".format(d) } ?: "--",
                Color.White,
            )
        }
        Cell("와이퍼") { Value(onOff(s.wiperOn, "ON", "OFF"), if (s.wiperOn == true) LaneColors.Wiper else Color.Gray) }
        Cell("앰비언트") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val c = s.ambientArgb?.let { Color(it or 0xFF000000.toInt()) } ?: Color.Gray
                val b = s.ambientBrightness
                Box(Modifier.size(18.dp).clip(CircleShape).background(if (b == 0) Color(0xFF2A2F3E) else c))
                Value(if (b == null) "--" else if (b == 0) "꺼짐" else "$b%", Color.White)
            }
        }
        Cell("실내등") { Value(onOff(s.cabinOn, "ON", "OFF"), if (s.cabinOn == true) Color.White else Color.Gray) }
    }
}

private fun gearLabel(g: Gear) = when (g) {
    Gear.PARK -> "P"
    Gear.REVERSE -> "R"
    Gear.NEUTRAL -> "N"
    Gear.DRIVE -> "D"
    Gear.UNKNOWN -> "--"
}

private fun onOff(v: Boolean?, on: String, off: String) = when (v) {
    true -> on
    false -> off
    null -> "--"
}

@Composable
private fun Cell(label: String, content: @Composable () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, fontSize = 13.sp, color = Color(0xFF8A93AB))
        content()
    }
}

@Composable
private fun Value(text: String, color: Color) {
    Text(text, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = color)
}
