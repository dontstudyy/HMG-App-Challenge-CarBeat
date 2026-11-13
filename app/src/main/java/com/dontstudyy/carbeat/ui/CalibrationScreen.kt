package com.dontstudyy.carbeat.ui

import android.os.SystemClock
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dontstudyy.carbeat.audio.Metronome
import com.dontstudyy.carbeat.game.Lane
import com.dontstudyy.carbeat.input.Calibration
import com.dontstudyy.carbeat.input.CalibrationStore
import com.dontstudyy.carbeat.input.InputEvent
import com.dontstudyy.carbeat.input.InputHub

private enum class Phase { IDLE, BRAKE, STEER, DONE }

private const val CALIB_BPM = 100.0
private const val SAMPLES = 5
/** 메트로놈이 시작하고 이 시간(박 2개) 안의 입력은 준비 구간이라 버린다 */
private const val LEAD_IN_SEC = 1.2

/**
 * 박자 보정: 100BPM 메트로놈에 맞춰 브레이크 5회, 핸들 5회.
 * 각 입력과 가장 가까운 박의 차이의 중앙값을 조작계별 지연으로 저장한다(와이퍼는 브레이크 값 사용).
 */
@Composable
fun CalibrationScreen(hub: InputHub, store: CalibrationStore, onNext: () -> Unit, onBack: () -> Unit) {
    var phase by remember { mutableStateOf(Phase.IDLE) }
    var brakeCount by remember { mutableStateOf(0) }
    var steerCount by remember { mutableStateOf(0) }
    var brakeResult by remember { mutableStateOf<Double?>(null) }
    var steerResult by remember { mutableStateOf<Double?>(null) }
    var failMessage by remember { mutableStateOf<String?>(null) }
    val metronome = remember { Metronome(CALIB_BPM) }
    val brakeInputs = remember { ArrayList<Long>() }
    val steerInputs = remember { ArrayList<Long>() }
    var startNanos by remember { mutableLongStateOf(0L) }
    val focus = remember { FocusRequester() }

    fun finishPhase(inputs: List<Long>): Double? {
        val b0 = metronome.beat0HeardNanos()
        val times = inputs.map { (it - b0) / 1e9 }
        val d = Calibration.delaySec(times, CALIB_BPM)
        Log.i("CARBEAT", "CALIB samples=${times.map { "%.3f".format(it) }} delay=$d beat0=$b0 tsOk=${metronome.isTimestampAvailable}")
        return d
    }

    fun onInput(e: InputEvent) {
        if (!e.down) return
        if (phase == Phase.IDLE || phase == Phase.DONE) return
        if ((e.timeNanos - startNanos) / 1e9 < LEAD_IN_SEC) return
        if (phase == Phase.BRAKE && e.lane == Lane.BRAKE) {
            brakeInputs += e.timeNanos
            brakeCount = brakeInputs.size
            if (brakeInputs.size >= SAMPLES) {
                brakeResult = finishPhase(brakeInputs)
                phase = Phase.STEER
            }
        } else if (phase == Phase.STEER && e.lane.isSteer) {
            steerInputs += e.timeNanos
            steerCount = steerInputs.size
            if (steerInputs.size >= SAMPLES) {
                steerResult = finishPhase(steerInputs)
                metronome.stop()
                val b = brakeResult
                val s = steerResult
                if (b != null && s != null) {
                    store.save(b, s)
                    failMessage = null
                } else {
                    failMessage = "측정에 실패했습니다. 다시 시도해 주세요."
                }
                phase = Phase.DONE
            }
        }
    }

    fun start() {
        brakeInputs.clear(); steerInputs.clear()
        brakeCount = 0; steerCount = 0
        brakeResult = null; steerResult = null; failMessage = null
        metronome.start()
        startNanos = SystemClock.elapsedRealtimeNanos()
        phase = Phase.BRAKE
        // 외부 자동 입력 스크립트(tools)가 박 시각을 알 수 있게, 첫 박이 들린 epoch 시각을 남긴다(타임스탬프가 잡힌 뒤)
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            val b0 = metronome.beat0HeardNanos()
            val epoch = System.currentTimeMillis() - (SystemClock.elapsedRealtimeNanos() - b0) / 1_000_000
            Log.i("CARBEAT", "CALIB_BEAT0 epoch=$epoch beatMs=${(60000.0 / CALIB_BPM).toLong()}")
        }, 600)
    }

    DisposableEffect(Unit) {
        val main = android.os.Handler(android.os.Looper.getMainLooper())
        hub.listener = { e -> main.post { onInput(e) } }
        onDispose {
            hub.listener = null
            metronome.stop()
        }
    }
    LaunchedEffect(Unit) { focus.requestFocus() }

    // 박에 맞춰 커졌다 작아지는 원(시각 안내). 소리가 안 들려도 박을 볼 수 있다.
    var frame by remember { mutableLongStateOf(0L) }
    LaunchedEffect(phase) {
        while (phase == Phase.BRAKE || phase == Phase.STEER) {
            withFrameNanos { frame = it }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(48.dp)
            .focusRequester(focus)
            .focusable()
            .debugLaneKeys(
                onDown = { lane -> onInput(InputEvent(lane, true, SystemClock.elapsedRealtimeNanos())) },
                onUp = { },
            ),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text("박자 보정", fontSize = 40.sp, fontWeight = FontWeight.Bold)
        when (phase) {
            Phase.IDLE -> {
                Text("메트로놈 소리에 맞춰 브레이크를 5번, 핸들을 5번 조작합니다.", fontSize = 22.sp)
                Text("차량마다 신호가 늦게 들어오는 시간이 달라서, 그만큼 판정을 당겨서 하기 위한 과정입니다.", fontSize = 18.sp)
            }
            Phase.BRAKE -> Text("① 박자에 맞춰 브레이크를 밟았다 떼세요  ($brakeCount / $SAMPLES)", fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
            Phase.STEER -> Text("② 박자에 맞춰 핸들을 좌우로 돌렸다 놓으세요  ($steerCount / $SAMPLES)", fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
            Phase.DONE -> {
                if (failMessage != null) {
                    Text(failMessage!!, fontSize = 24.sp)
                } else {
                    Text("보정 완료", fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                    Text("브레이크·와이퍼 지연  ${"%+.0f".format((brakeResult ?: 0.0) * 1000)}ms", fontSize = 24.sp)
                    Text("핸들 지연  ${"%+.0f".format((steerResult ?: 0.0) * 1000)}ms", fontSize = 24.sp)
                }
            }
        }
        if (phase == Phase.BRAKE || phase == Phase.STEER) {
            val period = (60.0 / CALIB_BPM * 1e9)
            val b0 = metronome.beat0HeardNanos()
            val now = SystemClock.elapsedRealtimeNanos()
            val phaseInBeat = (((now - b0) % period.toLong()) + period.toLong()) % period.toLong() / period
            val pulse = (1.0 - phaseInBeat).coerceIn(0.0, 1.0).toFloat()
            Canvas(Modifier.size(220.dp)) {
                if (frame < 0L) return@Canvas
                drawCircle(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.15f), radius = size.minDimension / 2f, center = Offset(size.width / 2f, size.height / 2f))
                drawCircle(
                    (if (phase == Phase.BRAKE) LaneColors.Brake else LaneColors.SteerLeft).copy(alpha = 0.3f + 0.7f * pulse),
                    radius = size.minDimension / 2f * (0.35f + 0.65f * pulse),
                    center = Offset(size.width / 2f, size.height / 2f),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onBack) { Text("뒤로", fontSize = 22.sp) }
            when (phase) {
                Phase.IDLE -> Button(onClick = { start() }) { Text(if (store.isCalibrated) "다시 보정" else "보정 시작", fontSize = 22.sp) }
                Phase.DONE -> {
                    OutlinedButton(onClick = { start() }) { Text("다시 보정", fontSize = 22.sp) }
                    Button(onClick = onNext) { Text("곡 시작", fontSize = 22.sp) }
                }
                else -> {}
            }
            if (phase == Phase.IDLE || phase == Phase.DONE) {
                if (phase == Phase.IDLE) OutlinedButton(onClick = onNext) { Text(if (store.isCalibrated) "저장된 값으로 시작" else "건너뛰기", fontSize = 22.sp) }
            }
        }
        if (store.isCalibrated && phase == Phase.IDLE) {
            Text(
                "저장된 보정값: 브레이크 ${"%+.0f".format(store.brakeSec * 1000)}ms, 핸들 ${"%+.0f".format(store.steerSec * 1000)}ms",
                fontSize = 18.sp,
            )
        }
    }
}
