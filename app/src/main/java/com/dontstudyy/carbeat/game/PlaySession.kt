package com.dontstudyy.carbeat.game

import android.content.Context
import com.dontstudyy.carbeat.audio.SongPlayer

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
        return if (player.isPlaying || player.ended) player.positionSec else 0.0
    }

    private var diagFrames = 0
    private var diagLastNanos = 0L

    /** 프레임마다 호출. 곡 시각을 돌려준다. */
    fun tick(): Double {
        val t = songTimeSec()
        // 진단: 1초마다 곡 시각과 프레임 수(초당 프레임)를 남긴다
        diagFrames++
        val now = System.nanoTime()
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

    private fun autoStep(t: Double) {
        while (autoIndex < autoEvents.size && autoEvents[autoIndex].first <= t) {
            val (time, lane, down) = autoEvents[autoIndex++]
            pressed[lane.ordinal] = down
            if (down) engine.onPress(lane, time) else engine.onRelease(lane, time)
        }
    }

    private fun onEvent(e: GameEvent) {
        val now = System.nanoTime()
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

    fun release() = player.release()
}
