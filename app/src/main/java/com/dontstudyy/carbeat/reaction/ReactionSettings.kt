package com.dontstudyy.carbeat.reaction

import android.content.Context

/**
 * 차량 반응 설정(기기에 저장).
 * - 차량 반응(앰비언트·실내등): 기본 켬
 * - 와이퍼 반응: 기본 꺼짐(기획서 기준). 시연·영상에서는 설정에서 켠다.
 */
class ReactionSettings(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("carbeat_settings", Context.MODE_PRIVATE)

    var vehicleReaction: Boolean
        get() = prefs.getBoolean("vehicle_reaction", true)
        set(v) { prefs.edit().putBoolean("vehicle_reaction", v).apply() }

    var wiperReaction: Boolean
        get() = prefs.getBoolean("wiper_reaction", false)
        set(v) { prefs.edit().putBoolean("wiper_reaction", v).apply() }

    /** 튜토리얼 미션("와이퍼를 2단으로") 완료 여부 */
    var tutorialDone: Boolean
        get() = prefs.getBoolean("tutorial_done", false)
        set(v) { prefs.edit().putBoolean("tutorial_done", v).apply() }
}
