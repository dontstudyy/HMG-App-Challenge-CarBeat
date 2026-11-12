package com.dontstudyy.carbeat.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** adb broadcast로 단계 0 실험을 실행한다. 결과는 logcat 태그 CARBEAT. */
class ExperimentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Experiments.get(context.applicationContext)
            .run(intent.getStringExtra("id") ?: "", intent.getStringExtra("arg") ?: "")
    }
}
