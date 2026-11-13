package com.dontstudyy.carbeat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.dontstudyy.carbeat.ui.CarBeatApp
import com.dontstudyy.carbeat.vehicle.VehicleLink

class MainActivity : ComponentActivity() {
    private lateinit var link: VehicleLink

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        link = VehicleLink(applicationContext)
        link.start()
        // debug 전용: adb shell am start ... --es song slow 로 곡을 미리 고른다(자동 검증용)
        val initialSong = if (BuildConfig.DEBUG) intent?.getStringExtra("song") else null
        if (BuildConfig.DEBUG) {
            // --ez auto true: 곡 시작 시 자동 플레이, --ez wiper true/false: 와이퍼 반응 설정, --ez react true/false: 차량 반응 설정
            com.dontstudyy.carbeat.ui.DebugFlags.autoplay = intent?.getBooleanExtra("auto", false) == true
            intent?.takeIf { it.hasExtra("wiper") }?.let { link.settings.wiperReaction = it.getBooleanExtra("wiper", false) }
            intent?.takeIf { it.hasExtra("react") }?.let { link.settings.vehicleReaction = it.getBooleanExtra("react", true) }
        }
        setContent { CarBeatApp(link, initialSong) }
    }

    override fun onDestroy() {
        link.stop()
        super.onDestroy()
    }
}
