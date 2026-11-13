package com.dontstudyy.carbeat.game

import android.content.Context
import android.os.SystemClock
import com.dontstudyy.carbeat.audio.SongPlayer
import com.dontstudyy.carbeat.input.InputEvent

/** 화면 연출용 이벤트(히트 터짐, MISS 회색 낙하·흔들림, 롱노트 결과). 시각은 System.nanoTime(). */
data class Effect(val kind: Kind, val lane: Lane, val bornNanos: Long) {
    enum class Kind { PERFECT, GOOD, MISS, HOLD_OK, HOLD_BROKEN }
}

/**
 * 한 판의 진행. 곡 재생, 시계, 엔진, 연출 이벤트를 묶는다. 메인 스레드 전용.
 *
 * 시계: 시작 후 [PRE_ROLL_SEC]초는 시스템 시계로 센 음수 곡 시각이고, 0이 되면 재생을 시작한 뒤에는
 * 재생 위치를 곡 시각으로 쓴다(소리 기준). 재생이 실제로 시작될 때까지는 0에서 기다린다.
 */
class PlaySession(
    ctx: Context,
    val song: Song,
    delaySec: (Lane) -> Double = { 0.0 },
    private val reaction: com.dontstudyy.carbeat.reaction.ReactionController? = null,
) {
    companion object {
        const val PRE_ROLL_SEC = 2.0
        /** 곡이 끝난 뒤 마지막 노트 정리까지 기다리는 여유 */
        private const val END_GRACE_SEC = 0.4
    }

    val effects = ArrayList<Effect>()
    var lastComboChangeNanos = 0L; private set
    var lastBrokenCombo = 0; private set
    var lastBreakNanos = 0L; private set

    val engine = GameEngine(song, delaySec) { onEvent(it) }
    private val player = SongPlayer(ctx, song)
    private var startNanos = 0L
    private var playRequested = false
    private var prevCombo = 0

    /** 지금 눌려 있는 레인 (화면 표시용) */
    val pressed = BooleanArray(Lane.entries.size)

    var finished = false; private set

    /** debug 전용: 모든 노트를 정확한 시각에 자동으로 친다. */
    var autoplay = false

    private val autoEvents: List<Triple<Double, Lane, Boolean>> = song.notes.flatMap { n ->
        listOf(
            Triple(n.t, n.lane, true),
            Triple(if (n.isHold) n.endSec else n.t + 0.03, n.lane, false),
        )
    }.sortedBy { it.first }
    private var autoIndex = 0

    fun start() {
        startNanos = System.nanoTime()
        player.prepare()
        reaction?.begin()
    }

    /** 현재 곡 시각(초). 시작 전 준비 구간은 음수. */
    fun songTimeSec(): Double {
        val clockT = (System.nanoTime() - startNanos) / 1e9 - PRE_ROLL_SEC
        if (!playRequested) {
            if (clockT >= 0.0) {
                player.play()
                playRequested = true
                return 0.0
            }
            return clockT
        }
        if (player.isPlaying || player.ended) {
            val pos = player.positionSec
            if (!t0Logged && pos > 0.05) {
                t0Logged = true
                // 곡 시각 0이 해당하는 epoch 시각. 외부 자동 입력 스크립트(tools)가 이 값에 맞춰 입력을 보낸다.
                android.util.Log.i("CARBEAT", "PLAY_T0 epoch=${System.currentTimeMillis() - (pos * 1000).toLong()}")
            }
            // 재생 위치를 그대로 쓰지 않고, 최근 표본으로 맞춘 곡 시계 모델로 읽는다(불규칙한 위치 값을 평활)
            return clock.timeAt(SystemClock.elapsedRealtimeNanos()) ?: pos
        }
        return 0.0
    }

    private var t0Logged = false

    /** 재생 위치 표본으로 맞춘 곡 시계 모델. 임의 시각(예: 입력 이벤트 발생 시각)의 곡 시각을 준다. */
    private val clock = SongClockModel()
    private var lastSyncNanos = 0L
    private var diagFrames = 0
    private var diagLastNanos = 0L

    /** 프레임마다 호출. 곡 시각을 돌려준다. */
    fun tick(): Double {
        if (playRequested && player.isPlaying) clock.addSample(SystemClock.elapsedRealtimeNanos(), player.positionSec)
        val t = songTimeSec()
        // 진단: 1초마다 곡 시각과 프레임 수(초당 프레임)를 남긴다
        diagFrames++
        val now = System.nanoTime()
        // 외부 자동 입력 스크립트(tools)용: 곡 시각 ↔ epoch 대응을 0.25초마다 남긴다.
        // 오디오가 실제 시간보다 느리게 도는 에뮬레이터에서도 곡 시계를 따라가게 하기 위한 것이다.
        if (playRequested && player.isPlaying && now - lastSyncNanos >= 250_000_000L) {
            lastSyncNanos = now
            android.util.Log.i("CARBEAT", "PLAY_SYNC t=${"%.4f".format(t)} epoch=${System.currentTimeMillis()}")
        }
        if (diagLastNanos == 0L) diagLastNanos = now
        if (now - diagLastNanos >= 1_000_000_000L) {
            android.util.Log.i("CARBEAT", "PLAY songT=${"%.2f".format(t)} wall=${(now - startNanos) / 1_000_000 / 1000.0}s fps=$diagFrames")
            diagFrames = 0
            diagLastNanos = now
        }
        if (autoplay) autoStep(t)
        engine.update(t)
        if (!finished && (player.ended || t >= song.durationSec + END_GRACE_SEC)) {
            engine.finish()
            finished = true
            reaction?.complete() // 실내등 연출 뒤 스스로 원상 복구
            val r = engine.result()
            android.util.Log.i("CARBEAT", "PLAY_RESULT song=${song.id} perfect=${r.perfect} good=${r.good} miss=${r.miss} score=${r.score} maxCombo=${r.maxCombo} hitRate=${"%.3f".format(r.hitRate)}")
        }
        return t
    }

    fun press(lane: Lane) {
        pressed[lane.ordinal] = true
        engine.onPress(lane, songTimeSec())
    }

    fun release(lane: Lane) {
        pressed[lane.ordinal] = false
        engine.onRelease(lane, songTimeSec())
    }

    /**
     * 차량 신호에서 변환된 입력. 이벤트 시각(elapsedRealtimeNanos)과 지금의 차이만큼 곡 시각을 되돌려 판정한다.
     * 메인 스레드에서 호출한다.
     */
    fun onVehicleInput(e: InputEvent) {
        val ageSec = (SystemClock.elapsedRealtimeNanos() - e.timeNanos) / 1e9
        val now = songTimeSec()
        // 이벤트가 발생한 시각의 곡 시각을 곡 시계 모델로 바로 구한다(메인 스레드 처리 지연과 무관).
        // 모델이 아직 없으면(곡 시작 직후) 처리 지연만큼 되돌린다.
        val t = clock.timeAt(e.timeNanos) ?: (now - ageSec)
        // 진단: 입력 처리 시점의 곡 시각, 처리 지연, 곡 시계 속도
        android.util.Log.i("CARBEAT", "IN ${e.lane} down=${e.down} songT=${"%.3f".format(t)} procT=${"%.3f".format(now)} age=${"%.0f".format(ageSec * 1000)}ms rate=${"%.2f".format(clock.rate)}")
        pressed[e.lane.ordinal] = e.down
        if (e.down) engine.onPress(e.lane, t) else engine.onRelease(e.lane, t)
    }

    private fun autoStep(t: Double) {
        while (autoIndex < autoEvents.size && autoEvents[autoIndex].first <= t) {
            val (time, lane, down) = autoEvents[autoIndex++]
            pressed[lane.ordinal] = down
            if (down) engine.onPress(lane, time) else engine.onRelease(lane, time)
        }
    }

    private fun onEvent(e: GameEvent) {
        val now = System.nanoTime()
        when (e.type) {
            GameEvent.Type.JUDGED -> e.judgment?.let { reaction?.onJudged(e.note.lane, it, e.comboAfter) }
            GameEvent.Type.HOLD_BROKEN -> reaction?.onComboBroken()
            GameEvent.Type.HOLD_SUCCESS -> {}
        }
        if (e.type == GameEvent.Type.JUDGED) {
            // 진단: 노트별 판정 오차(ms, 음수=일찍). 보정 후의 값이다.
            android.util.Log.i("CARBEAT", "JUDGE lane=${e.note.lane} t=${"%.3f".format(e.note.t)} j=${e.judgment} off=${e.offsetSec?.let { "%.0f".format(it * 1000) }}")
        }
        val lane = e.note.lane
        when (e.type) {
            GameEvent.Type.JUDGED -> effects += Effect(
                when (e.judgment) {
                    Judgment.PERFECT -> Effect.Kind.PERFECT
                    Judgment.GOOD -> Effect.Kind.GOOD
                    else -> Effect.Kind.MISS
                },
                lane, now,
            )
            GameEvent.Type.HOLD_SUCCESS -> effects += Effect(Effect.Kind.HOLD_OK, lane, now)
            GameEvent.Type.HOLD_BROKEN -> effects += Effect(Effect.Kind.HOLD_BROKEN, lane, now)
        }
        val c = e.comboAfter
        if (c != prevCombo) {
            if (c == 0 && prevCombo > 0) {
                lastBrokenCombo = prevCombo
                lastBreakNanos = now
            }
            lastComboChangeNanos = now
            prevCombo = c
        }
        effects.removeAll { now - it.bornNanos > 1_500_000_000L }
    }

    fun result(): GameResult = engine.result()

    fun release() {
        player.release()
        // 완주하지 않고 나가면(중단·주행 전환·앱 종료) 차량 반응을 즉시 되돌린다. 완주했으면 연출이 끝난 뒤 스스로 되돌린다.
        if (!finished) reaction?.abort()
    }
}
