package com.dontstudyy.carbeat.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.dontstudyy.carbeat.vehicle.SignalState
import com.dontstudyy.carbeat.vehicle.VehicleSignals

/**
 * 위에서 본 실내. **차량에서 읽은 값**으로 그린다(게임 내부 상태가 아님):
 * 브레이크 페달(리스너 값), 핸들 각도(리스너 값), 와이퍼(리스너 값), 앰비언트 색·밝기(차량이 알려 준 값), 실내등.
 * 읽은 값이 없으면 꺼진 모양으로 그린다. 오른쪽 패널은 교체 가능한 자리다(나중에 앨범 사진·곡 제목 화면).
 */
@Composable
fun InteriorPanel(signals: VehicleSignals) {
    val s by signals.state.collectAsState()
    val sweep by rememberInfiniteTransition(label = "wiper").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse),
        label = "wiperSweep",
    )
    Box(Modifier.fillMaxSize().padding(20.dp)) {
        Canvas(Modifier.fillMaxSize()) { drawInterior(s, sweep) }
    }
}

private val BODY = Color(0xFF1B2030)
private val LINE = Color(0xFF3A4258)

private fun DrawScope.drawInterior(s: SignalState, sweep: Float) {
    val w = size.width
    val h = size.height
    // 차 몸통(앞이 위)
    val bw = minOf(w * 0.78f, h * 0.55f)
    val bh = h * 0.92f
    val left = (w - bw) / 2f
    val top = (h - bh) / 2f
    drawRoundRect(BODY, Offset(left, top), Size(bw, bh), CornerRadius(bw * 0.28f))
    drawRoundRect(LINE, Offset(left, top), Size(bw, bh), CornerRadius(bw * 0.28f), style = Stroke(3f))

    // 앰비언트: 실내 가장자리를 따라 빛 띠(색과 밝기). 꺼져 있거나 값이 없으면 어둡게
    val b = ((s.ambientBrightness ?: 0).coerceIn(0, 100)) / 100f
    val on = b > 0f
    val argb = s.ambientArgb ?: 0xFFFFFFFF.toInt()
    val ambient = Color(argb or 0xFF000000.toInt())
    val a = if (on) 0.15f + 0.85f * b else 0.08f
    val inset = bw * 0.07f
    drawRoundRect(
        ambient.copy(alpha = a),
        Offset(left + inset, top + inset),
        Size(bw - inset * 2, bh - inset * 2),
        CornerRadius(bw * 0.22f),
        style = Stroke(width = bw * 0.035f),
    )
    if (on && b > 0.05f) {
        drawRoundRect(
            ambient.copy(alpha = 0.10f * b),
            Offset(left + inset, top + inset),
            Size(bw - inset * 2, bh - inset * 2),
            CornerRadius(bw * 0.22f),
        )
    }

    // 실내등: 가운데 부드러운 흰 빛
    if (s.cabinOn == true) {
        drawCircle(Color.White.copy(alpha = 0.22f), radius = bw * 0.42f, center = Offset(w / 2f, top + bh * 0.52f))
        drawCircle(Color.White.copy(alpha = 0.18f), radius = bw * 0.24f, center = Offset(w / 2f, top + bh * 0.52f))
    }

    // 앞 유리와 와이퍼 두 개
    val glassTop = top + bh * 0.10f
    drawRoundRect(Color(0xFF2A3350), Offset(left + bw * 0.12f, glassTop), Size(bw * 0.76f, bh * 0.13f), CornerRadius(14f))
    val wiperOn = s.wiperOn == true
    val ang = if (wiperOn) -50f + 100f * sweep else -50f
    for (k in 0..1) {
        val px = left + bw * (0.34f + 0.32f * k)
        val py = glassTop + bh * 0.125f
        rotate(ang, pivot = Offset(px, py)) {
            drawLine(
                if (wiperOn) LaneColors.Wiper else Color(0xFF6A7390),
                Offset(px, py), Offset(px, py - bh * 0.11f), strokeWidth = 6f, cap = StrokeCap.Round,
            )
        }
    }

    // 핸들(운전석, 위쪽 왼쪽): 각도만큼 돌린다. 실제 값 범위가 작아 보기 좋게 2배로 그린다
    val wheelC = Offset(left + bw * 0.34f, top + bh * 0.34f)
    val wheelR = bw * 0.17f
    val deg = (s.steerDeg ?: 0f)
    rotate(deg * 2f, pivot = wheelC) {
        drawCircle(Color(0xFF9AA3BC), wheelR, wheelC, style = Stroke(width = 9f))
        drawLine(Color(0xFF9AA3BC), Offset(wheelC.x - wheelR, wheelC.y), Offset(wheelC.x + wheelR, wheelC.y), strokeWidth = 7f)
        drawLine(Color(0xFFFFC233), wheelC, Offset(wheelC.x, wheelC.y - wheelR), strokeWidth = 7f) // 맨 위 표시
    }

    // 브레이크 페달: 운전석 발밑
    val pressed = s.brakePressed == true
    val pw = bw * 0.14f
    val ph = bh * 0.07f
    val padPos = Offset(left + bw * 0.30f, top + bh * 0.56f)
    drawRoundRect(
        if (pressed) LaneColors.Brake else Color(0xFF4A526B),
        padPos, Size(pw, ph), CornerRadius(8f),
    )
    if (pressed) drawRoundRect(LaneColors.Brake.copy(alpha = 0.3f), Offset(padPos.x - 10f, padPos.y - 10f), Size(pw + 20f, ph + 20f), CornerRadius(14f))

    // 좌석 두 개(앞), 뒷좌석 자리 표시
    val seatC = Color(0xFF262E45)
    drawRoundRect(seatC, Offset(left + bw * 0.16f, top + bh * 0.44f), Size(bw * 0.26f, bh * 0.12f), CornerRadius(18f))
    drawRoundRect(seatC, Offset(left + bw * 0.58f, top + bh * 0.44f), Size(bw * 0.26f, bh * 0.12f), CornerRadius(18f))
    drawRoundRect(seatC, Offset(left + bw * 0.14f, top + bh * 0.70f), Size(bw * 0.72f, bh * 0.14f), CornerRadius(22f))
}
