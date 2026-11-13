package com.dontstudyy.carbeat

import com.dontstudyy.carbeat.game.SongClockModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class SongClockModelTest {
    private fun ms(v: Long) = v * 1_000_000L

    @Test
    fun notReadyWithFewSamples() {
        val m = SongClockModel()
        m.addSample(ms(0), 0.0)
        m.addSample(ms(100), 0.1)
        assertFalse(m.isReady)
        assertNull(m.timeAt(ms(150)))
    }

    @Test
    fun followsRealtimeClock() {
        val m = SongClockModel()
        for (i in 0..20) m.addSample(ms(i * 100L), i * 0.1)
        assertTrue(m.isReady)
        assertEquals(1.0, m.rate, 1e-6)
        assertEquals(1.234, m.timeAt(ms(1234))!!, 1e-6)
        // 마지막 표본 뒤 시각도 직선으로 외삽
        assertEquals(2.35, m.timeAt(ms(2350))!!, 1e-6)
    }

    @Test
    fun followsSlowClock() {
        // 곡 시계가 실제 시간의 0.75배로 간다
        val m = SongClockModel()
        for (i in 0..30) m.addSample(ms(i * 100L), i * 0.1 * 0.75)
        assertEquals(0.75, m.rate, 1e-6)
        assertEquals(2.0 * 0.75, m.timeAt(ms(2000))!!, 1e-6)
    }

    @Test
    fun smoothsIrregularPositions() {
        // 프레임 간격이 불규칙하고 위치 값이 ±60ms 흔들려도 직선이 실제 시계에 가깝다
        val rnd = Random(7)
        val m = SongClockModel()
        var t = 0L
        var maxErr = 0.0
        for (i in 0 until 80) {
            t += 60L + rnd.nextInt(300)
            val noisy = t / 1000.0 + (rnd.nextDouble() - 0.5) * 0.12
            m.addSample(ms(t), noisy)
            if (m.isReady && i > 20) {
                val est = m.timeAt(ms(t))!!
                maxErr = maxOf(maxErr, Math.abs(est - t / 1000.0))
            }
        }
        assertTrue("최대 오차 $maxErr", maxErr < 0.05)
    }

    @Test
    fun timeAtPastEventUsesEventTimeNotProcessingTime() {
        // 처리가 0.4초 늦어도, 이벤트가 발생한 시각의 곡 시각을 바로 얻는다
        val m = SongClockModel()
        for (i in 0..30) m.addSample(ms(i * 100L), i * 0.1)
        val eventNanos = ms(2000)
        val processedAt = ms(2400)
        assertEquals(2.0, m.timeAt(eventNanos)!!, 1e-6)
        assertEquals(2.4, m.timeAt(processedAt)!!, 1e-6)
    }

    @Test
    fun oldSamplesAreDropped() {
        val m = SongClockModel(windowNanos = 1_000_000_000L)
        // 앞 구간은 속도 1.0, 뒤 구간은 속도 0.5로 바뀜 → 창이 지나면 새 속도를 따른다
        for (i in 0..10) m.addSample(ms(i * 100L), i * 0.1)
        for (i in 11..40) m.addSample(ms(i * 100L), 1.0 + (i - 10) * 0.05)
        assertEquals(0.5, m.rate, 0.01)
    }

    @Test
    fun resetClearsModel() {
        val m = SongClockModel()
        for (i in 0..10) m.addSample(ms(i * 100L), i * 0.1)
        assertTrue(m.isReady)
        m.reset()
        assertFalse(m.isReady)
    }
}
