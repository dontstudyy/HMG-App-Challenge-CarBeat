package com.dontstudyy.carbeat.game

enum class Judgment { PERFECT, GOOD, MISS }

/** 판정 폭(초). 핸들은 신호 갱신이 느려 넓게 잡는다(설계서 6장). */
data class JudgeWindows(val perfectSec: Double, val goodSec: Double) {
    companion object {
        fun of(lane: Lane) =
            if (lane.isSteer) JudgeWindows(0.100, 0.180) else JudgeWindows(0.060, 0.120)
    }
}

data class GameEvent(
    val type: Type,
    val note: Note,
    /** JUDGED일 때만 */
    val judgment: Judgment? = null,
    /** 판정 시각 - 노트 시각(초). 음수면 일찍. MISS는 null */
    val offsetSec: Double? = null,
    val comboAfter: Int,
) {
    enum class Type { JUDGED, HOLD_SUCCESS, HOLD_BROKEN }
}

/**
 * 판정·콤보·점수·롱노트 엔진. 시간은 모두 곡 시각(초)이며 재생 위치에서 온다.
 * 입력 시각에서 조작계별 지연(박자 보정)을 뺀 값으로 판정한다.
 *
 * - 입력은 같은 레인에서 아직 판정되지 않은 가장 가까운 노트와 비교한다. GOOD 폭 밖이면 무시(벌점 없음).
 * - GOOD 폭을 지나도록 입력이 없으면 MISS(콤보 0).
 * - 롱노트: 시작을 맞힌 뒤 끝나기 [HOLD_TAIL_SEC] 전까지 누르고 있으면 성공(+[HOLD_BONUS]). 일찍 떼면 콤보 0.
 * - 점수: PERFECT 1000, GOOD 600.
 */
class GameEngine(
    val song: Song,
    private val delaySec: (Lane) -> Double = { 0.0 },
    private val onEvent: (GameEvent) -> Unit = {},
) {
    companion object {
        const val PERFECT_SCORE = 1000
        const val GOOD_SCORE = 600
        const val HOLD_BONUS = 1500
        const val HOLD_TAIL_SEC = 0.100
    }

    private class NoteState(val note: Note) {
        var judged = false
        var holding = false
    }

    private val states = song.notes.map { NoteState(it) }
    private val byLane: Map<Lane, List<NoteState>> = Lane.entries.associateWith { l -> states.filter { it.note.lane == l } }
    private val pressed = BooleanArray(Lane.entries.size)

    var score = 0; private set
    var combo = 0; private set
    var maxCombo = 0; private set
    var perfectCount = 0; private set
    var goodCount = 0; private set
    var missCount = 0; private set
    var holdSuccessCount = 0; private set

    val totalNotes: Int get() = song.notes.size

    /** PERFECT·GOOD 비율(0~1) */
    val hitRate: Double get() = if (totalNotes == 0) 0.0 else (perfectCount + goodCount).toDouble() / totalNotes

    private fun effective(lane: Lane, songTimeSec: Double) = songTimeSec - delaySec(lane)

    fun onPress(lane: Lane, songTimeSec: Double) {
        if (pressed[lane.ordinal]) return // 이미 눌린 상태의 중복 입력
        pressed[lane.ordinal] = true
        val eff = effective(lane, songTimeSec)
        val w = JudgeWindows.of(lane)
        val target = byLane.getValue(lane).filter { !it.judged }.minByOrNull { Math.abs(it.note.t - eff) } ?: return
        val dt = eff - target.note.t
        if (Math.abs(dt) > w.goodSec) return // 너무 먼 입력은 무시
        val j = if (Math.abs(dt) <= w.perfectSec) Judgment.PERFECT else Judgment.GOOD
        target.judged = true
        if (j == Judgment.PERFECT) { perfectCount++; score += PERFECT_SCORE } else { goodCount++; score += GOOD_SCORE }
        addCombo()
        if (target.note.isHold) target.holding = true
        onEvent(GameEvent(GameEvent.Type.JUDGED, target.note, j, dt, combo))
    }

    fun onRelease(lane: Lane, songTimeSec: Double) {
        if (!pressed[lane.ordinal]) return
        pressed[lane.ordinal] = false
        val eff = effective(lane, songTimeSec)
        val h = byLane.getValue(lane).firstOrNull { it.holding } ?: return
        h.holding = false
        if (eff >= h.note.endSec - HOLD_TAIL_SEC) {
            holdSuccess(h.note)
        } else {
            combo = 0
            onEvent(GameEvent(GameEvent.Type.HOLD_BROKEN, h.note, comboAfter = combo))
        }
    }

    /** 프레임마다 호출. 놓친 노트를 MISS로 처리하고, 끝까지 유지한 롱노트를 성공 처리한다. */
    fun update(songTimeSec: Double) {
        for (lane in Lane.entries) {
            val eff = effective(lane, songTimeSec)
            val good = JudgeWindows.of(lane).goodSec
            for (s in byLane.getValue(lane)) {
                if (!s.judged && eff > s.note.t + good) {
                    s.judged = true
                    missCount++
                    combo = 0
                    onEvent(GameEvent(GameEvent.Type.JUDGED, s.note, Judgment.MISS, null, combo))
                } else if (s.holding && pressed[lane.ordinal] && eff >= s.note.endSec - HOLD_TAIL_SEC) {
                    s.holding = false
                    holdSuccess(s.note)
                }
            }
        }
    }

    /** 곡이 끝났을 때 남은 노트를 모두 정리한다. */
    fun finish() = update(Double.MAX_VALUE / 4)

    private fun holdSuccess(note: Note) {
        score += HOLD_BONUS
        holdSuccessCount++
        onEvent(GameEvent(GameEvent.Type.HOLD_SUCCESS, note, comboAfter = combo))
    }

    private fun addCombo() {
        combo++
        if (combo > maxCombo) maxCombo = combo
    }
}
