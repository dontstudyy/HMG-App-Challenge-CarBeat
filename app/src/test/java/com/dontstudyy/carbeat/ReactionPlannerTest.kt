package com.dontstudyy.carbeat

import com.dontstudyy.carbeat.game.Judgment
import com.dontstudyy.carbeat.game.Lane
import com.dontstudyy.carbeat.reaction.ReactionPlanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReactionPlannerTest {
    private val colors = ReactionPlanner.LANE_COLORS

    @Test
    fun baseBrightnessByCombo() {
        assertEquals(0, ReactionPlanner.baseBrightness(0))
        assertEquals(0, ReactionPlanner.baseBrightness(9))
        assertEquals(30, ReactionPlanner.baseBrightness(10))
        assertEquals(30, ReactionPlanner.baseBrightness(29))
        assertEquals(55, ReactionPlanner.baseBrightness(30))
        assertEquals(80, ReactionPlanner.baseBrightness(50))
        assertEquals(80, ReactionPlanner.baseBrightness(300))
    }

    @Test
    fun perfectFlashesLaneColorThenReturnsToBase() {
        val p = ReactionPlanner()
        p.onStart(0)
        p.poll(0) // 초기 상태 내보내기
        p.onJudged(Lane.BRAKE, Judgment.PERFECT, 1, 1000)
        val flash = p.poll(1000).ambient!!
        assertTrue(flash.on)
        assertEquals(colors[Lane.BRAKE.ordinal], flash.argb)
        assertEquals(ReactionPlanner.FLASH_BRIGHTNESS, flash.brightness)
        assertTrue(p.poll(1200).isEmpty) // 아직 깜빡이는 중, 변화 없음
        val back = p.poll(1300).ambient!!
        assertEquals(0, back.brightness) // 콤보 1 → 기본 밝기 0
        assertEquals(colors[Lane.BRAKE.ordinal], back.argb) // 색은 마지막 레인 색 유지
    }

    @Test
    fun flashesAreLimitedToOncePerHalfSecond() {
        val p = ReactionPlanner()
        p.onStart(0); p.poll(0)
        p.onJudged(Lane.STEER_LEFT, Judgment.PERFECT, 1, 1000)
        p.poll(1000)
        // 0.3초 뒤 두 번째 PERFECT(다른 레인): 0.5초 안이라 새 깜빡임 없음
        p.onJudged(Lane.WIPER, Judgment.PERFECT, 2, 1300)
        val d = p.poll(1300).ambient
        assertEquals(0, d?.brightness) // 첫 깜빡임이 끝난 상태로 돌아감, 색도 바뀌지 않음
        assertEquals(colors[Lane.STEER_LEFT.ordinal], d?.argb)
        // 0.5초가 지나면 다시 깜빡임
        p.onJudged(Lane.WIPER, Judgment.PERFECT, 3, 1500)
        assertEquals(colors[Lane.WIPER.ordinal], p.poll(1500).ambient?.argb)
    }

    @Test
    fun goodDoesNotFlashButCountsCombo() {
        val p = ReactionPlanner()
        p.onStart(0); p.poll(0)
        p.onJudged(Lane.BRAKE, Judgment.GOOD, 10, 1000)
        val a = p.poll(1000).ambient!!
        assertEquals(30, a.brightness) // 콤보 10 → 기본 30, 깜빡임 없음
    }

    @Test
    fun missTurnsAmbientOffAndNextHitRestores() {
        val p = ReactionPlanner()
        p.onStart(0); p.poll(0)
        p.onJudged(Lane.BRAKE, Judgment.GOOD, 30, 1000)
        assertEquals(55, p.poll(1000).ambient!!.brightness)
        p.onJudged(Lane.BRAKE, Judgment.MISS, 0, 2000)
        val off = p.poll(2000).ambient!!
        assertFalse(off.on)
        p.onJudged(Lane.BRAKE, Judgment.GOOD, 1, 3000)
        val on = p.poll(3000).ambient!!
        assertTrue(on.on)
        assertEquals(0, on.brightness) // 콤보 0에서 다시 시작 → 기본 밝기 0
    }

    @Test
    fun wiperAtCombo10And20OnlyOnceEachAndOnlyWhenEnabled() {
        val on = ReactionPlanner(wiperEnabled = true)
        on.onStart(0); on.poll(0)
        for (c in 1..9) on.onJudged(Lane.BRAKE, Judgment.GOOD, c, c * 100L)
        assertNull(on.poll(900).wiperOn)
        on.onJudged(Lane.BRAKE, Judgment.GOOD, 10, 1000)
        assertEquals(true, on.poll(1000).wiperOn)
        assertNull(on.poll(2000).wiperOn)           // 1.2초 안: 변화 없음
        assertEquals(false, on.poll(2200).wiperOn)  // 1.2초 뒤 끔
        // 콤보가 끊겼다가 다시 10 → 이미 한 번 썼으므로 또 켜지 않음
        on.onJudged(Lane.BRAKE, Judgment.MISS, 0, 3000)
        on.onJudged(Lane.BRAKE, Judgment.GOOD, 10, 4000)
        assertNull(on.poll(4000).wiperOn)
        on.onJudged(Lane.BRAKE, Judgment.GOOD, 20, 5000)
        assertEquals(true, on.poll(5000).wiperOn)
        // 곡당 2회가 끝났으므로 더 이상 없음
        on.poll(6300)
        on.onJudged(Lane.BRAKE, Judgment.GOOD, 10, 7000)
        on.onJudged(Lane.BRAKE, Judgment.GOOD, 20, 7100)
        assertNull(on.poll(7100).wiperOn)

        val off = ReactionPlanner(wiperEnabled = false)
        off.onStart(0)
        off.onJudged(Lane.BRAKE, Judgment.GOOD, 10, 1000)
        assertNull(off.poll(1000).wiperOn)
    }

    @Test
    fun cabinLightBlinksThreeTimesAtHalfSecondSteps() {
        val p = ReactionPlanner()
        p.onStart(0)
        assertNull(p.poll(0).cabinOn)
        assertNull(p.cabinEndMs())
        p.onComplete(10_000)
        assertEquals(13_000L, p.cabinEndMs())
        val seq = ArrayList<Boolean>()
        var t = 10_000L
        while (t <= 13_200L) {
            p.poll(t).cabinOn?.let { seq += it }
            t += 50
        }
        assertEquals(listOf(true, false, true, false, true, false), seq)
    }

    @Test
    fun ambientDisabledSendsNothing() {
        val p = ReactionPlanner(ambientEnabled = false)
        p.onStart(0)
        p.onJudged(Lane.BRAKE, Judgment.PERFECT, 1, 1000)
        assertNull(p.poll(1000).ambient)
    }

    @Test
    fun noRedundantOutput() {
        val p = ReactionPlanner()
        p.onStart(0)
        assertNotNull(p.poll(0).ambient)
        assertTrue(p.poll(100).isEmpty)
        assertTrue(p.poll(200).isEmpty)
    }

    @Test
    fun brokenComboLowersBase() {
        val p = ReactionPlanner()
        p.onStart(0); p.poll(0)
        p.onJudged(Lane.BRAKE, Judgment.GOOD, 30, 1000)
        assertEquals(55, p.poll(1000).ambient!!.brightness)
        p.onComboBroken(1100)
        assertEquals(0, p.poll(1100).ambient!!.brightness)
    }
}
