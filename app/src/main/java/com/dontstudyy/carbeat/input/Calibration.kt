package com.dontstudyy.carbeat.input

object Calibration {
    /**
     * 메트로놈 박(첫 박 [firstBeatSec], 간격 60/[bpm]초)과 입력 시각들의 차이를 구해 중앙값을 지연으로 돌려준다.
     * 양수 = 입력이 박보다 늦게 들어온다. 표본이 [minSamples]개 미만이면 null.
     */
    fun delaySec(inputTimesSec: List<Double>, bpm: Double, firstBeatSec: Double = 0.0, minSamples: Int = 3): Double? {
        if (inputTimesSec.size < minSamples) return null
        val period = 60.0 / bpm
        val offsets = inputTimesSec.map { t ->
            var d = (t - firstBeatSec) % period
            if (d < 0) d += period
            if (d >= period / 2) d -= period // 가장 가까운 박과의 차이로 [-p/2, p/2)
            d
        }.sorted()
        val n = offsets.size
        return if (n % 2 == 1) offsets[n / 2] else (offsets[n / 2 - 1] + offsets[n / 2]) / 2
    }
}
