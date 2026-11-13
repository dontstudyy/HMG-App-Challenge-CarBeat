package com.dontstudyy.carbeat.game

/**
 * 곡 시계 모델. 재생 위치를 프레임마다 한 번씩 읽은 표본 (시각, 위치) 중 최근 [windowNanos] 안의 것으로
 * 직선(최소제곱)을 맞춘다. 임의 시각의 곡 시각을 이 직선으로 구하므로:
 *  - 입력 이벤트가 메인 스레드에서 늦게 처리돼도, 이벤트가 발생한 시각의 곡 시각을 바로 알 수 있다.
 *  - 재생 위치 값이 불규칙하게 멈췄다 튀어도(오디오 출력이 모자랄 때) 직선이 평활해 준다.
 *  - 곡 시계가 실제 시간과 다른 속도로 가도(예: 0.75배) 그 속도를 따라간다.
 * 시각은 모두 같은 시계(예: SystemClock.elapsedRealtimeNanos)로 준다.
 */
class SongClockModel(
    private val windowNanos: Long = 2_500_000_000L,
    private val minSamples: Int = 6,
    private val minSpanNanos: Long = 500_000_000L,
) {
    private val ts = ArrayList<Long>()
    private val ps = ArrayList<Double>()
    private var origin = 0L
    private var a = 0.0
    private var b = 1.0
    private var ready = false

    /** 직선의 기울기 = 곡 시계가 실제 시간 대비 얼마나 빨리 가는지(준비 전에는 1.0) */
    val rate: Double get() = if (ready) b else 1.0

    val isReady: Boolean get() = ready

    fun addSample(nanos: Long, positionSec: Double) {
        ts += nanos
        ps += positionSec
        while (ts.size > 1 && nanos - ts[0] > windowNanos) {
            ts.removeAt(0)
            ps.removeAt(0)
        }
        fit()
    }

    private fun fit() {
        val n = ts.size
        if (n < minSamples || ts[n - 1] - ts[0] < minSpanNanos) {
            ready = false
            return
        }
        origin = ts[0]
        var sx = 0.0; var sy = 0.0
        for (i in 0 until n) { sx += (ts[i] - origin) / 1e9; sy += ps[i] }
        val mx = sx / n
        val my = sy / n
        var sxx = 0.0; var sxy = 0.0
        for (i in 0 until n) {
            val dx = (ts[i] - origin) / 1e9 - mx
            sxx += dx * dx
            sxy += dx * (ps[i] - my)
        }
        if (sxx < 1e-9) { ready = false; return }
        b = (sxy / sxx).coerceIn(0.5, 1.5)
        a = my - b * mx
        ready = true
    }

    /** [nanos] 시각의 곡 시각(초). 모델이 아직 준비되지 않았으면 null */
    fun timeAt(nanos: Long): Double? = if (ready) a + b * ((nanos - origin) / 1e9) else null

    fun reset() {
        ts.clear(); ps.clear(); ready = false; b = 1.0; a = 0.0
    }
}
