package com.dontstudyy.carbeat

import com.dontstudyy.carbeat.game.GameEngine
import com.dontstudyy.carbeat.game.GameEvent
import com.dontstudyy.carbeat.game.Judgment
import com.dontstudyy.carbeat.game.Lane
import com.dontstudyy.carbeat.game.Note
import com.dontstudyy.carbeat.game.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameEngineTest {
    private fun song(vararg notes: Note) = Song("t", "t", "t.ogg", 120.0, 60.0, notes.toList())
    private val events = ArrayList<GameEvent>()
    private fun engine(s: Song, delay: (Lane) -> Double = { 0.0 }) = GameEngine(s, delay) { events += it }

    @Test
    fun perfectAndGoodWindowsForBrake() {
        val e = engine(song(Note(1.0, Lane.BRAKE), Note(2.0, Lane.BRAKE), Note(3.0, Lane.BRAKE)))
        e.onPress(Lane.BRAKE, 1.059); e.onRelease(Lane.BRAKE, 1.1)   // 59ms → PERFECT
        e.onPress(Lane.BRAKE, 2.061); e.onRelease(Lane.BRAKE, 2.1)   // 61ms → GOOD
        e.onPress(Lane.BRAKE, 3.121); e.onRelease(Lane.BRAKE, 3.2)   // 121ms → 무시
        assertEquals(1, e.perfectCount)
        assertEquals(1, e.goodCount)
        assertEquals(1000 + 600, e.score)
        e.update(3.2)
        assertEquals(1, e.missCount)
        assertEquals(0, e.combo)
        assertEquals(2, e.maxCombo)
    }

    @Test
    fun steerHasWiderWindows() {
        val e = engine(song(Note(1.0, Lane.STEER_LEFT), Note(2.0, Lane.STEER_RIGHT), Note(3.0, Lane.STEER_LEFT)))
        e.onPress(Lane.STEER_LEFT, 1.099)   // PERFECT (≤100ms)
        e.onPress(Lane.STEER_RIGHT, 2.179)  // GOOD (≤180ms)
        e.onPress(Lane.STEER_LEFT, 3.181)   // 무시
        assertEquals(1, e.perfectCount)
        assertEquals(1, e.goodCount)
        e.update(3.3)
        assertEquals(1, e.missCount)
    }

    @Test
    fun earlyInputIsJudgedToo() {
        val e = engine(song(Note(1.0, Lane.BRAKE)))
        e.onPress(Lane.BRAKE, 0.95)
        assertEquals(1, e.perfectCount)
        assertEquals(-0.05, events.single().offsetSec!!, 1e-9)
    }

    @Test
    fun missWhenNoInputAfterGoodWindow() {
        val e = engine(song(Note(1.0, Lane.BRAKE)))
        e.update(1.119)
        assertEquals(0, e.missCount)
        e.update(1.121)
        assertEquals(1, e.missCount)
        assertEquals(Judgment.MISS, events.single().judgment)
        // 한 번만 MISS
        e.update(5.0)
        assertEquals(1, e.missCount)
    }

    @Test
    fun comboBreaksOnMissAndMaxIsKept() {
        val e = engine(song(Note(1.0, Lane.BRAKE), Note(2.0, Lane.BRAKE), Note(3.0, Lane.BRAKE), Note(4.0, Lane.BRAKE)))
        e.onPress(Lane.BRAKE, 1.0); e.onRelease(Lane.BRAKE, 1.1)
        e.onPress(Lane.BRAKE, 2.0); e.onRelease(Lane.BRAKE, 2.1)
        assertEquals(2, e.combo)
        e.update(3.2) // 3.0 MISS
        assertEquals(0, e.combo)
        e.onPress(Lane.BRAKE, 4.0)
        assertEquals(1, e.combo)
        assertEquals(2, e.maxCombo)
    }

    @Test
    fun calibrationDelayIsSubtracted() {
        // 입력이 항상 80ms 늦게 들어온다 → 지연 0.08을 빼면 PERFECT
        val e = engine(song(Note(1.0, Lane.BRAKE)), delay = { 0.08 })
        e.onPress(Lane.BRAKE, 1.08)
        assertEquals(1, e.perfectCount)
        assertEquals(0.0, events.single().offsetSec!!, 1e-9)
    }

    @Test
    fun delayIsPerLane() {
        val e = engine(song(Note(1.0, Lane.BRAKE), Note(1.0, Lane.STEER_LEFT)), delay = { if (it.isSteer) 0.15 else 0.0 })
        e.onPress(Lane.BRAKE, 1.0)
        e.onPress(Lane.STEER_LEFT, 1.15)
        assertEquals(2, e.perfectCount)
    }

    @Test
    fun simultaneousLanesJudgedIndependently() {
        val e = engine(song(Note(1.0, Lane.BRAKE), Note(1.0, Lane.WIPER)))
        e.onPress(Lane.BRAKE, 1.0)
        assertEquals(1, e.perfectCount)
        e.update(1.5)
        assertEquals(1, e.missCount) // 와이퍼만 MISS
    }

    @Test
    fun holdSuccessWhenHeldUntilTail() {
        val e = engine(song(Note(1.0, Lane.BRAKE, holdSec = 2.0)))
        e.onPress(Lane.BRAKE, 1.0)
        e.update(2.8)
        assertEquals(0, e.holdSuccessCount)
        e.update(2.9) // 끝 3.0 - 0.1
        assertEquals(1, e.holdSuccessCount)
        assertEquals(1000 + 1500, e.score)
        assertEquals(1, e.combo)
        // 이후 뗌은 영향 없음
        e.onRelease(Lane.BRAKE, 3.5)
        assertEquals(1, e.combo)
        assertEquals(1, e.holdSuccessCount)
    }

    @Test
    fun holdSuccessWhenReleasedAfterTail() {
        val e = engine(song(Note(1.0, Lane.BRAKE, holdSec = 2.0)))
        e.onPress(Lane.BRAKE, 1.0)
        e.onRelease(Lane.BRAKE, 2.95)
        assertEquals(1, e.holdSuccessCount)
    }

    @Test
    fun earlyReleaseBreaksComboWithoutBonus() {
        val e = engine(song(Note(1.0, Lane.BRAKE, holdSec = 2.0)))
        e.onPress(Lane.BRAKE, 1.0)
        assertEquals(1, e.combo)
        e.onRelease(Lane.BRAKE, 2.0)
        assertEquals(0, e.combo)
        assertEquals(0, e.holdSuccessCount)
        assertEquals(1000, e.score)
        assertEquals(GameEvent.Type.HOLD_BROKEN, events.last().type)
        // 이후 update로 중복 성공 처리되지 않음
        e.update(3.0)
        assertEquals(0, e.holdSuccessCount)
    }

    @Test
    fun missedHoldStartGivesNoHold() {
        val e = engine(song(Note(1.0, Lane.BRAKE, holdSec = 2.0)))
        e.update(1.2)
        assertEquals(1, e.missCount)
        e.onPress(Lane.BRAKE, 2.0)
        e.update(3.0)
        assertEquals(0, e.holdSuccessCount)
        assertEquals(0, e.score)
    }

    @Test
    fun duplicatePressWithoutReleaseIsIgnored() {
        val e = engine(song(Note(1.0, Lane.BRAKE), Note(1.05, Lane.BRAKE)))
        e.onPress(Lane.BRAKE, 1.0)
        e.onPress(Lane.BRAKE, 1.05) // 떼지 않고 다시 누름 → 무시
        assertEquals(1, e.perfectCount + e.goodCount)
    }

    @Test
    fun nearestUnjudgedNoteIsChosen() {
        val e = engine(song(Note(1.0, Lane.BRAKE), Note(1.1, Lane.BRAKE)))
        e.onPress(Lane.BRAKE, 1.09); e.onRelease(Lane.BRAKE, 1.095)
        assertEquals(1.1, events.last().note.t, 0.0) // 1.1 쪽이 더 가깝다
        e.onPress(Lane.BRAKE, 1.1)
        assertEquals(1.0, events.last().note.t, 0.0) // 남은 노트
    }

    @Test
    fun finishFlushesRemainingNotes() {
        val e = engine(song(Note(1.0, Lane.BRAKE), Note(2.0, Lane.WIPER), Note(3.0, Lane.BRAKE, holdSec = 1.0)))
        e.onPress(Lane.BRAKE, 1.0); e.onRelease(Lane.BRAKE, 1.1)
        e.finish()
        assertEquals(2, e.missCount)
        assertEquals(1, e.perfectCount)
        assertTrue(e.hitRate > 0.33 && e.hitRate < 0.34)
    }

    @Test
    fun scoreAddsUpOnRealSong() {
        // 모든 노트를 정확히 치면 모두 PERFECT + 롱노트 보너스
        val s = Song.parse(java.io.File("src/main/assets/songs/slow.json").readText(Charsets.UTF_8))
        val e = GameEngine(s)
        data class Ev(val t: Double, val lane: Lane, val down: Boolean)
        val evs = s.notes.flatMap { n ->
            listOf(Ev(n.t, n.lane, true), Ev(n.endSec + 0.05, n.lane, false))
        }.sortedWith(compareBy<Ev> { it.t })
        for (ev in evs) {
            e.update(ev.t)
            if (ev.down) e.onPress(ev.lane, ev.t) else e.onRelease(ev.lane, ev.t)
        }
        e.finish()
        assertEquals(s.notes.size, e.perfectCount)
        assertEquals(0, e.missCount)
        assertEquals(s.notes.size, e.maxCombo)
        assertEquals(s.notes.size * 1000 + s.notes.count { it.isHold } * 1500, e.score)
        assertEquals(1.0, e.hitRate, 0.0)
    }
}
