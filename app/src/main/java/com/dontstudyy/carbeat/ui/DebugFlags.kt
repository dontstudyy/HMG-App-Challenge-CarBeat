package com.dontstudyy.carbeat.ui

/** debug 빌드에서 실행 인텐트로 켜는 자동 검증용 플래그. release에서는 쓰이지 않는다. */
object DebugFlags {
    /** 곡을 시작하면 자동 플레이를 켠다 */
    @Volatile
    var autoplay = false
}
