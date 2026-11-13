package com.dontstudyy.carbeat

import com.dontstudyy.carbeat.game.Lane
import com.dontstudyy.carbeat.input.BrakeConverter
import com.dontstudyy.carbeat.input.Calibration
import com.dontstudyy.carbeat.input.InputEvent
import com.dontstudyy.carbeat.input.SteerConverter
import com.dontstudyy.carbeat.input.WiperConverter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InputConvertersTest {
    private fun feed(c: SteerConverter, vararg angles: Float): List<InputEvent> =
        angles.withIndex().flatMap { (i, a) -> c.onAngle(a, i * 1000L) }

    @Test
    fun brakeEdges() {
        val c = BrakeConverter()
        assertNull(c.onValue(false, 1))
        assertNull(c.onValue(null, 2))
        assertEquals(InputEvent(Lane.BRAKE, true, 3), c.onValue(true, 3))
        assertNull(c.onValue(true, 4))
        assertEquals(InputEvent(Lane.BRAKE, false, 5), c.onValue(false, 5))
    }

    @Test
    fun steerLeftPressAndRelease() {
        val ev = feed(SteerConverter(), 0f, -10f, -14.9f, -15f, -30f, -20f, -9f, -7.9f)
        assertEquals(
            listOf(InputEvent(Lane.STEER_LEFT, true, 3000), InputEvent(Lane.STEER_LEFT, false, 7000)),
            ev,
        )
    }

    @Test
    fun steerRightPressAndRelease() {
        val ev = feed(SteerConverter(), 0f, 15f, 30f, 8f, 7f)
        assertEquals(
            listOf(InputEvent(Lane.STEER_RIGHT, true, 1000), InputEvent(Lane.STEER_RIGHT, false, 4000)),
            ev,
        )
    }

    @Test
    fun steerHysteresisBlocksJitterRepress() {
        // -16에서 -14, -16으로 흔들려도 ±8 안으로 돌아오기 전에는 다시 누르지 않는다
        val ev = feed(SteerConverter(), -16f, -14f, -16f, -12f, -16f)
        assertEquals(1, ev.size)
        assertTrue(ev[0].down)
    }

    @Test
    fun steerCanRepressAfterReturningToCenter() {
        val ev = feed(SteerConverter(), -20f, 0f, -20f)
        assertEquals(listOf(true, false, true), ev.map { it.down })
    }

    @Test
    fun steerSwingStraightToOtherSide() {
        val ev = feed(SteerConverter(), -20f, 20f)
        assertEquals(
            listOf(
                InputEvent(Lane.STEER_LEFT, true, 0),
                InputEvent(Lane.STEER_LEFT, false, 1000),
                InputEvent(Lane.STEER_RIGHT, true, 1000),
            ),
            ev,
        )
    }

    @Test
    fun steerNullAngleIgnored() {
        assertTrue(SteerConverter().onAngle(null, 0).isEmpty())
    }

    @Test
    fun wiperPressOnOffToOn() {
        val c = WiperConverter()
        assertEquals(InputEvent(Lane.WIPER, true, 10), c.onState(true, 10))
        assertNull(c.onState(true, 11))
        assertEquals(InputEvent(Lane.WIPER, false, 12), c.onState(false, 12))
    }

    @Test
    fun wiperIgnoresChangesRightAfterAppAction() {
        val c = WiperConverter(ignoreNanos = 1_500_000_000L)
        c.markAppAction(1_000_000_000L)
        assertNull(c.onState(true, 1_050_000_000L))   // 앱이 켠 것
        assertNull(c.onState(false, 2_250_000_000L))  // 앱이 끈 것 (1.5초 안)
        // 이후 사람이 켠 것은 입력
        assertEquals(Lane.WIPER, c.onState(true, 5_000_000_000L)!!.lane)
    }

    @Test
    fun wiperIgnoreWindowIsBounded() {
        val c = WiperConverter(ignoreNanos = 1_500_000_000L)
        c.markAppAction(0)
        // 1.5초가 지난 뒤의 변화는 입력
        assertEquals(true, c.onState(true, 1_500_000_000L)!!.down)
    }

    @Test
    fun calibrationMedianOfOffsets() {
        // 120BPM → 박 간격 0.5초. 입력이 박보다 0.06~0.10초 늦음
        val times = listOf(1.06, 1.58, 2.10, 2.57, 3.09)
        val d = Calibration.delaySec(times, 120.0)!!
        assertEquals(0.08, d, 0.011) // 중앙값(0.08)
    }

    @Test
    fun calibrationHandlesEarlyInputs() {
        val times = listOf(0.97, 1.47, 1.98, 2.46, 2.97) // 박보다 약 30ms 일찍
        val d = Calibration.delaySec(times, 120.0)!!
        assertEquals(-0.03, d, 0.011)
    }

    @Test
    fun calibrationIsRobustToOutlier() {
        val times = listOf(1.08, 1.58, 2.08, 2.58, 3.30) // 마지막은 엉뚱한 입력
        assertEquals(0.08, Calibration.delaySec(times, 120.0)!!, 0.011)
    }

    @Test
    fun calibrationNeedsEnoughSamples() {
        assertNull(Calibration.delaySec(listOf(1.0, 1.5), 120.0))
    }
}
