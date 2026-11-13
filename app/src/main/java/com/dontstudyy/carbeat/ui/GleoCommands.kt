package com.dontstudyy.carbeat.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Gleo 음성 명령(intent action)을 화면에 전달한다. 같은 명령이 연달아 와도 구분되도록 번호를 붙인다. */
data class GleoCommand(val action: String, val seq: Long)

object GleoCommands {
    const val START_CARBEAT = "START_CARBEAT"
    const val PAUSE_GAME = "PAUSE_GAME"

    private val _latest = MutableStateFlow<GleoCommand?>(null)
    val latest: StateFlow<GleoCommand?> = _latest.asStateFlow()
    private var seq = 0L

    fun post(action: String?) {
        if (action == START_CARBEAT || action == PAUSE_GAME) {
            _latest.value = GleoCommand(action, ++seq)
        }
    }
}
