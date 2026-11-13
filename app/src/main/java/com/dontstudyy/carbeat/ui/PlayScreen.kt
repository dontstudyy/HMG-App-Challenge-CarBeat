package com.dontstudyy.carbeat.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dontstudyy.carbeat.BuildConfig
import com.dontstudyy.carbeat.game.GameResult
import com.dontstudyy.carbeat.game.Lane
import com.dontstudyy.carbeat.game.PlaySession
import com.dontstudyy.carbeat.game.Song
import com.dontstudyy.carbeat.input.CalibrationStore
import com.dontstudyy.carbeat.input.InputHub
import kotlinx.coroutines.delay

/**
 * 플레이 화면. 왼쪽 약 60% 게임, 오른쪽 약 40%는 [rightPanel]로 교체할 수 있다
 * (실내 그림은 단계 4, 나중에 앨범 사진·곡 제목 화면으로 교체).
 */
@Composable
fun PlayScreen(
    song: Song,
    hub: InputHub,
    store: CalibrationStore,
    onFinish: (GameResult) -> Unit,
    onBack: () -> Unit,
    rightPanel: @Composable () -> Unit = { InteriorPlaceholder() },
) {
    val ctx = LocalContext.current
    val session = remember(song) { PlaySession(ctx, song, store::delayFor) }
    // frame, songTime은 그리기(Canvas)에서만 읽는다. 구성(composition)은 HUD 값이 바뀔 때만 다시 돈다.
    var frame by remember { mutableLongStateOf(0L) }
    var songTime by remember { mutableDoubleStateOf(-PlaySession.PRE_ROLL_SEC) }
    var hud by remember { mutableStateOf(HudState()) }
    var progress by remember { mutableFloatStateOf(0f) }
    var auto by remember { mutableStateOf(false) }
    val textMeasurer = rememberTextMeasurer(cacheSize = 64)
    val focus = remember { FocusRequester() }

    DisposableEffect(session) {
        session.start()
        // 차량 신호 입력: 스레드가 달라 메인 스레드로 넘겨서 처리한다
        val main = android.os.Handler(android.os.Looper.getMainLooper())
        hub.listener = { e -> main.post { session.onVehicleInput(e) } }
        onDispose {
            hub.listener = null
            session.release()
        }
    }

    LaunchedEffect(session) {
        focus.requestFocus()
        while (!session.finished) {
            androidx.compose.runtime.withFrameNanos { n ->
                val t = session.tick()
                songTime = t
                frame = n
                val e = session.engine
                val h = HudState(e.score, e.perfectCount, e.goodCount, e.missCount)
                if (h != hud) hud = h
                val p = (t / song.durationSec).toFloat().coerceIn(0f, 1f)
                val pq = (p * 200).toInt() / 200f
                if (pq != progress) progress = pq
            }
        }
        delay(900) // 마지막 연출이 보이도록
        onFinish(session.result())
    }

    Row(
        Modifier
            .fillMaxSize()
            .focusRequester(focus)
            .focusable()
            .debugLaneKeys(onDown = { session.press(it) }, onUp = { session.release(it) }),
    ) {
        Column(Modifier.weight(0.6f).fillMaxHeight()) {
            Hud(session.song.title, hud, progress, auto, onToggleAuto = {
                auto = !auto
                session.autoplay = auto
            }, onBack = onBack)
            Canvas(Modifier.weight(1f).fillMaxWidth()) {
                if (frame < 0L) return@Canvas // frame을 읽어 매 프레임 다시 그리게 한다
                drawGame(session, songTime, System.nanoTime(), textMeasurer)
            }
        }
        Box(Modifier.weight(0.4f).fillMaxHeight()) { rightPanel() }
    }
}

data class HudState(val score: Int = 0, val perfect: Int = 0, val good: Int = 0, val miss: Int = 0)

@Composable
private fun Hud(
    title: String,
    h: HudState,
    progress: Float,
    auto: Boolean,
    onToggleAuto: () -> Unit,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (BuildConfig.DEBUG) {
                    if (auto) Button(onClick = onToggleAuto) { Text("오토 ON") } else OutlinedButton(onClick = onToggleAuto) { Text("오토") }
                }
                OutlinedButton(onClick = onBack) { Text("중단") }
            }
        }
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Text("%,d".format(h.score), fontSize = 40.sp, fontWeight = FontWeight.ExtraBold)
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Text("PERFECT ${h.perfect}", fontSize = 18.sp)
                Text("GOOD ${h.good}", fontSize = 18.sp)
                Text("MISS ${h.miss}", fontSize = 18.sp)
            }
        }
    }
}

/** 오른쪽 패널 자리 표시. 단계 4에서 실내 그림으로 바뀐다. */
@Composable
fun InteriorPlaceholder() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("실내 그림 자리\n(단계 4)", fontSize = 22.sp)
    }
}
