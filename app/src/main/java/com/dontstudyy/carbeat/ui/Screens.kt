package com.dontstudyy.carbeat.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import ai.pleos.playground.vehicle.constant.control.WiperSwitch
import androidx.compose.runtime.LaunchedEffect
import com.dontstudyy.carbeat.reaction.ReactionSettings
import com.dontstudyy.carbeat.vehicle.ChargeAdvice
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dontstudyy.carbeat.game.GameResult
import com.dontstudyy.carbeat.game.Song
import com.dontstudyy.carbeat.gate.GateState

@Composable
private fun ScreenFrame(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(48.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(title, fontSize = 40.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        content()
    }
}

@Composable
fun HomeScreen(
    songs: List<Song>,
    selectedId: String,
    onSelect: (String) -> Unit,
    settings: ReactionSettings,
    advice: ChargeAdvice,
    tutorialDone: Boolean,
    onStart: () -> Unit,
    onTutorial: () -> Unit,
) {
    var vehicleReaction by remember { mutableStateOf(settings.vehicleReaction) }
    var wiperReaction by remember { mutableStateOf(settings.wiperReaction) }
    ScreenFrame("카비트") {
        // 충전 추천: 충전 중이면 남은 시간과 "M곡 정도"를 보여 준다('예상' 표기)
        Text(advice.message, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = if (advice.minutesLeft != null) LaneColors.Wiper else MaterialTheme.colorScheme.onBackground)
        Text("곡 선택", fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        for (song in songs) {
            Row(
                Modifier.fillMaxWidth().clickable { onSelect(song.id) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = song.id == selectedId, onClick = { onSelect(song.id) })
                Text("${song.title}  ·  ${song.bpm.toInt()}BPM  ·  ${song.durationSec.toInt()}초  ·  노트 ${song.notes.size}", fontSize = 22.sp)
            }
        }
        Text("설정", fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Switch(checked = vehicleReaction, onCheckedChange = { vehicleReaction = it; settings.vehicleReaction = it })
            Text("차량 반응 (앰비언트·실내등)", fontSize = 20.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Switch(
                checked = wiperReaction,
                onCheckedChange = { wiperReaction = it; settings.wiperReaction = it },
                enabled = vehicleReaction,
            )
            Text("와이퍼 반응 (기본 꺼짐, 콤보 10·20에서 곡당 최대 2회)", fontSize = 20.sp)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(onClick = onStart) { Text("시작하기", fontSize = 22.sp) }
            OutlinedButton(onClick = onTutorial) { Text(if (tutorialDone) "튜토리얼 미션 ✔" else "튜토리얼 미션", fontSize = 22.sp) }
        }
    }
}

@Composable
fun ConditionsScreen(gate: GateState, onNext: () -> Unit, onBack: () -> Unit) {
    ScreenFrame("실행 조건") {
        Text((if (gate.gearOk) "✔" else "✖") + "  기어 P", fontSize = 26.sp)
        Text((if (gate.stopped) "✔" else "✖") + "  정차 확인 (주행 규제 없음)", fontSize = 26.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedButton(onClick = onBack) { Text("뒤로", fontSize = 22.sp) }
            Button(onClick = onNext, enabled = gate.open) { Text("다음", fontSize = 22.sp) }
        }
    }
}

@Composable
fun ResultScreen(result: GameResult?, onHome: () -> Unit) {
    ScreenFrame("결과") {
        if (result == null) {
            Text("결과가 없습니다", fontSize = 22.sp)
        } else {
            Text(result.title, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
            Text("점수  %,d".format(result.score), fontSize = 36.sp, fontWeight = FontWeight.ExtraBold)
            Text("최대 콤보  ${result.maxCombo}", fontSize = 26.sp)
            Text("PERFECT ${result.perfect}   GOOD ${result.good}   MISS ${result.miss}   롱노트 성공 ${result.holdSuccess}", fontSize = 22.sp)
            Text("적중률  %.0f%%  (%d / %d)".format(result.hitRate * 100, result.perfect + result.good, result.total), fontSize = 22.sp)
        }
        Button(onClick = onHome) { Text("홈으로", fontSize = 22.sp) }
    }
}

/** 와이퍼 스위치 값을 사람이 읽는 말로 */
private fun wiperLabel(w: WiperSwitch?): String = when (w) {
    null -> "읽는 중"
    WiperSwitch.OFF -> "꺼짐"
    WiperSwitch.AUTO -> "자동"
    WiperSwitch.CONTINUOUS_LEVEL_1 -> "1단(저속)"
    WiperSwitch.CONTINUOUS_LEVEL_3 -> "2단(고속)"
    else -> w.name
}

/**
 * 튜토리얼 미션: "와이퍼를 2단으로 올려 보세요". 앞 와이퍼 스위치가 CONTINUOUS_LEVEL_3(Virtual Console의 High)이 되면 완료.
 * 와이퍼 입력 리스너는 켜짐/꺼짐만 알려 주므로, 스위치 단계는 0.5초마다 직접 읽는다.
 */
@Composable
fun TutorialScreen(readWiper: ((WiperSwitch?) -> Unit) -> Unit, done: Boolean, onComplete: () -> Unit, onBack: () -> Unit) {
    var level by remember { mutableStateOf<WiperSwitch?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            readWiper { w ->
                level = w
                if (w == WiperSwitch.CONTINUOUS_LEVEL_3) onComplete()
            }
            kotlinx.coroutines.delay(500)
        }
    }
    ScreenFrame("튜토리얼 미션") {
        Text("와이퍼를 2단으로 올려 보세요", fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
        Text("현재 와이퍼: ${wiperLabel(level)}", fontSize = 24.sp)
        if (done) {
            Text("미션 완료! 와이퍼 2단 위치를 익혔어요", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = LaneColors.Wiper)
        } else {
            Text("차의 와이퍼 스위치(또는 시연 도구)로 2단(고속)을 선택하세요", fontSize = 18.sp)
        }
        OutlinedButton(onClick = onBack) { Text("뒤로", fontSize = 22.sp) }
    }
}

/** 게이트가 닫히면 모든 화면 위에 보이는 화면. 조작 요소 없음. */
@Composable
fun HiddenScreen(gate: GateState) {
    Column(
        Modifier.fillMaxSize().padding(48.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("주행 중에는 숨겨져요", fontSize = 44.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text(gate.reason.ifEmpty { "차량 상태를 확인하는 중입니다" }, fontSize = 22.sp)
    }
}
