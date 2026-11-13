package com.dontstudyy.carbeat

import com.dontstudyy.carbeat.vehicle.ChargeEstimator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChargeEstimatorTest {
    @Test
    fun unknownWhileNotRead() {
        assertNull(ChargeEstimator.advice(null, true, 30.0).minutesLeft)
        assertNull(ChargeEstimator.advice(50, null, 30.0).minutesLeft)
    }

    @Test
    fun notChargingGivesNoEstimate() {
        val a = ChargeEstimator.advice(40, false, 30.0)
        assertNull(a.minutesLeft)
        assertNull(a.songs)
        assertTrue(a.message.contains("충전 중이 아니에요"))
    }

    @Test
    fun enoughChargedGivesNoEstimate() {
        assertNull(ChargeEstimator.advice(80, true, 30.0).minutesLeft)
        assertNull(ChargeEstimator.advice(95, true, 30.0).songs)
    }

    @Test
    fun estimateFromSocAndSongLength() {
        // 40% → 목표 80%까지 40% × 0.6 = 24분, 곡 평균 30초 → 48곡
        val a = ChargeEstimator.advice(40, true, 30.0)
        assertEquals(24, a.minutesLeft)
        assertEquals(48, a.songs)
        assertTrue(a.message.contains("24분(예상)"))
        assertTrue(a.message.contains("48곡"))
    }

    @Test
    fun lowerSocMeansMoreTime() {
        val high = ChargeEstimator.advice(70, true, 30.0).minutesLeft!!
        val low = ChargeEstimator.advice(20, true, 30.0).minutesLeft!!
        assertTrue(low > high)
        // 올림: 1% 차이도 0.6분 → 1분
        assertEquals(1, ChargeEstimator.advice(79, true, 30.0).minutesLeft)
    }

    @Test
    fun changesWithSocAndChargeSwitch() {
        // 통과 기준: SOC와 충전 스위치를 바꾸면 추천 문구가 바뀐다
        val a = ChargeEstimator.advice(30, true, 30.0).message
        val b = ChargeEstimator.advice(60, true, 30.0).message
        val c = ChargeEstimator.advice(60, false, 30.0).message
        assertTrue(a != b)
        assertTrue(b != c)
    }
}
