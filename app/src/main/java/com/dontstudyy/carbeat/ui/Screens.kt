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
    onStart: () -> Unit,
    onTutorial: () -> Unit,
) {
    ScreenFrame("카비트") {
        Text("충전 정보는 단계 5에서 연결됩니다.", fontSize = 20.sp)
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
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(onClick = onStart) { Text("시작하기", fontSize = 22.sp) }
            OutlinedButton(onClick = onTutorial) { Text("튜토리얼 미션", fontSize = 22.sp) }
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

@Composable
fun TutorialScreen(onBack: () -> Unit) {
    ScreenFrame("튜토리얼 미션") {
        Text("와이퍼를 2단으로 올려 보세요 (단계 5에서 구현)", fontSize = 22.sp)
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
