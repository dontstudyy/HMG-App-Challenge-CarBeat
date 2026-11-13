package com.dontstudyy.carbeat.vehicle

/** 충전 상태 표시용 요약 */
data class ChargeAdvice(
    /** 화면에 보여 줄 한 줄. 예: "충전 완료까지 약 26분(예상), 40곡 정도 할 수 있어요" */
    val message: String,
    /** 충전 완료까지 남은 분(추정). 충전 중이 아니거나 이미 충분하면 null */
    val minutesLeft: Int?,
    /** 그 시간 동안 할 수 있는 곡 수(추정). 위와 같은 조건에서 null */
    val songs: Int?,
)

/**
 * 충전 남은 시간과 "M곡 정도" 추정(설계서 9장).
 * 남은 분 = (목표 [targetPercent]% - 현재 SOC) × [minutesPerPercent]. 가정값이라 화면에 '예상'으로 표기한다.
 * 곡 수 = 남은 시간 ÷ 곡 평균 길이.
 */
object ChargeEstimator {
    const val TARGET_PERCENT = 80
    /** 가정: 급속충전 80%까지 약 48분(서울 급속충전 1회 평균 47.2분 근거) → 1%당 0.6분 */
    const val MINUTES_PER_PERCENT = 0.6

    fun advice(
        soc: Int?,
        charging: Boolean?,
        avgSongSec: Double,
        targetPercent: Int = TARGET_PERCENT,
        minutesPerPercent: Double = MINUTES_PER_PERCENT,
    ): ChargeAdvice {
        if (soc == null || charging == null) return ChargeAdvice("충전 상태를 확인하는 중입니다", null, null)
        if (charging != true) return ChargeAdvice("충전 중이 아니에요 (배터리 $soc%)", null, null)
        if (soc >= targetPercent) return ChargeAdvice("충전이 거의 끝났어요 (배터리 $soc%)", null, null)
        val minutes = Math.ceil((targetPercent - soc) * minutesPerPercent).toInt()
        val songs = if (avgSongSec <= 0.0) 0 else (minutes * 60.0 / avgSongSec).toInt()
        return ChargeAdvice(
            "충전 완료까지 약 ${minutes}분(예상), ${songs}곡 정도 할 수 있어요 (배터리 $soc%)",
            minutes,
            songs,
        )
    }
}
