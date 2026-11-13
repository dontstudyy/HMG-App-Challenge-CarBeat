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
        setContent { CarBeatApp(link, initialSong) }
    }

    override fun onDestroy() {
        link.stop()
        super.onDestroy()
    }
}
