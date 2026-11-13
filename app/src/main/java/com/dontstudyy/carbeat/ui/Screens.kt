package com.dontstudyy.carbeat.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
fun HomeScreen(onStart: () -> Unit, onTutorial: () -> Unit) {
    ScreenFrame("카비트") {
        Text("충전 정보는 단계 5에서 연결됩니다.", fontSize = 20.sp)
        Text("곡 목록 (자리 표시)", fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        for (t in listOf("천천히 한 바퀴", "퇴근길 브레이크", "충전소 페스티벌")) {
            Text("• $t", fontSize = 22.sp)
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
fun CalibrationScreen(onNext: () -> Unit, onBack: () -> Unit) {
    ScreenFrame("박자 보정") {
        Text("메트로놈에 맞춰 브레이크 5회, 핸들 5회 (단계 3에서 구현)", fontSize = 22.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedButton(onClick = onBack) { Text("뒤로", fontSize = 22.sp) }
            Button(onClick = onNext) { Text("건너뛰기", fontSize = 22.sp) }
        }
    }
}

@Composable
fun PlayScreen(onFinish: () -> Unit, onBack: () -> Unit) {
    ScreenFrame("플레이") {
        Text("게임 화면 (단계 2에서 구현)", fontSize = 22.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedButton(onClick = onBack) { Text("중단", fontSize = 22.sp) }
            Button(onClick = onFinish) { Text("완주(임시)", fontSize = 22.sp) }
        }
    }
}

@Composable
fun ResultScreen(onHome: () -> Unit) {
    ScreenFrame("결과") {
        Text("점수, 최대 콤보, 판정 수 (단계 2에서 구현)", fontSize = 22.sp)
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
