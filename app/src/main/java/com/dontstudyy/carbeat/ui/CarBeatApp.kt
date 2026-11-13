package com.dontstudyy.carbeat.ui

import android.os.SystemClock
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.dontstudyy.carbeat.game.GameResult
import com.dontstudyy.carbeat.game.SongRepository
import com.dontstudyy.carbeat.gate.GateState
import com.dontstudyy.carbeat.input.CalibrationStore
import com.dontstudyy.carbeat.gate.ParkingGate
import com.dontstudyy.carbeat.vehicle.ChargeEstimator
import com.dontstudyy.carbeat.vehicle.VehicleLink

/** 화면 7개 중 게이트가 직접 다루는 "주행 중 숨김"을 뺀 6개. 숨김 화면은 게이트가 닫히면 항상 우선한다. */
enum class Screen { HOME, CONDITIONS, CALIBRATION, PLAY, RESULT, TUTORIAL }

@Composable
fun CarBeatApp(link: VehicleLink, initialSongId: String? = null) {
    val ctx = LocalContext.current
    val snapshot by link.snapshot.collectAsState()
    val gate: GateState = ParkingGate.evaluate(snapshot)
    val songs = remember { SongRepository.loadAll(ctx) }
    val store = remember { CalibrationStore(ctx) }
    var selectedId by remember { mutableStateOf(initialSongId?.takeIf { id -> songs.any { it.id == id } } ?: "brake") }
    var screen by remember { mutableStateOf(Screen.HOME) }
    var result by remember { mutableStateOf<GameResult?>(null) }
    var tutorialDone by remember { mutableStateOf(link.settings.tutorialDone) }
    var pauseSignal by remember { mutableStateOf(0) }
    val energy by link.energy.state.collectAsState()
    val advice = ChargeEstimator.advice(energy.soc, energy.charging, songs.map { it.durationSec }.average())
    val gleo by GleoCommands.latest.collectAsState()

    // 게이트가 닫히면 진행 중이던 화면은 버리고, 다시 열리면 홈에서 시작한다(주행 중 재개 금지).
    LaunchedEffect(gate.open) {
        Log.i("CARBEAT", "GATE_UI open=${gate.open} n=${SystemClock.elapsedRealtimeNanos() / 1_000_000}")
        if (!gate.open) {
            screen = Screen.HOME
            link.reaction.abort() // 주행 전환: 차량 반응을 즉시 되돌린다
        }
    }

    LaunchedEffect(advice.message) { Log.i("CARBEAT", "ADVICE ${advice.message}") }

    // Gleo 음성 명령: START_CARBEAT는 시작 흐름(실행 조건)으로, PAUSE_GAME은 플레이 중이면 일시정지
    LaunchedEffect(gleo?.seq) {
        val c = gleo ?: return@LaunchedEffect
        Log.i("CARBEAT", "GLEO ${c.action} gateOpen=${gate.open} screen=$screen")
        when (c.action) {
            GleoCommands.START_CARBEAT -> if (gate.open && screen != Screen.PLAY) screen = Screen.CONDITIONS
            GleoCommands.PAUSE_GAME -> if (screen == Screen.PLAY) pauseSignal++
        }
    }

    CarBeatTheme {
        Surface(
            Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
        ) {
            Box(Modifier.fillMaxSize()) {
                if (!gate.open) {
                    HiddenScreen(gate)
                } else {
                    when (screen) {
                        Screen.HOME -> HomeScreen(
                            songs = songs,
                            selectedId = selectedId,
                            onSelect = { selectedId = it },
                            settings = link.settings,
                            advice = advice,
                            tutorialDone = tutorialDone,
                            onStart = { screen = Screen.CONDITIONS },
                            onTutorial = { screen = Screen.TUTORIAL },
                        )
                        Screen.CONDITIONS -> ConditionsScreen(
                            gate = gate,
                            onNext = { screen = Screen.CALIBRATION },
                            onBack = { screen = Screen.HOME },
                        )
                        Screen.CALIBRATION -> CalibrationScreen(
                            hub = link.input,
                            store = store,
                            onNext = { screen = Screen.PLAY },
                            onBack = { screen = Screen.HOME },
                        )
                        Screen.PLAY -> PlayScreen(
                            song = songs.first { it.id == selectedId },
                            hub = link.input,
                            store = store,
                            reaction = link.reaction,
                            signals = link.signals,
                            gear = snapshot.gear,
                            pauseSignal = pauseSignal,
                            onFinish = { result = it; screen = Screen.RESULT },
                            onBack = { screen = Screen.HOME },
                        )
                        Screen.RESULT -> ResultScreen(result = result, onHome = { screen = Screen.HOME })
                        Screen.TUTORIAL -> TutorialScreen(
                            readWiper = { cb -> link.readWiperSwitch(cb) },
                            done = tutorialDone,
                            onComplete = {
                                if (!tutorialDone) {
                                    link.settings.tutorialDone = true
                                    tutorialDone = true
                                    Log.i("CARBEAT", "TUTORIAL done")
                                }
                            },
                            onBack = { screen = Screen.HOME },
                        )
                    }
                }
            }
        }
    }
}
