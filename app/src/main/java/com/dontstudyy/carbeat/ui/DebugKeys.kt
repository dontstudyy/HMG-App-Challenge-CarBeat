package com.dontstudyy.carbeat.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import com.dontstudyy.carbeat.BuildConfig
import com.dontstudyy.carbeat.game.Lane

/**
 * debug 빌드에서만 방향키(← ↓ → ↑)를 레인 입력으로 바꾼다. release에서는 아무것도 하지 않는다.
 * 키를 누르고 있어서 반복되는 KeyDown(repeatCount > 0)은 무시한다.
 */
fun Modifier.debugLaneKeys(onDown: (Lane) -> Unit, onUp: (Lane) -> Unit): Modifier {
    if (!BuildConfig.DEBUG) return this
    return this.onPreviewKeyEvent { ev ->
        val lane = when (ev.key) {
            Key.DirectionLeft -> Lane.STEER_LEFT
            Key.DirectionDown -> Lane.BRAKE
            Key.DirectionRight -> Lane.STEER_RIGHT
            Key.DirectionUp -> Lane.WIPER
            else -> return@onPreviewKeyEvent false
        }
        when (ev.type) {
            KeyEventType.KeyDown -> if (ev.nativeKeyEvent.repeatCount == 0) onDown(lane)
            KeyEventType.KeyUp -> onUp(lane)
        }
        true
    }
}
