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
        setContent { CarBeatApp(link) }
    }

    override fun onDestroy() {
        link.stop()
        super.onDestroy()
    }
}
