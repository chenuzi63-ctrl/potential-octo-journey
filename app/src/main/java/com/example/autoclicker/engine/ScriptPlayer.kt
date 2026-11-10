package com.example.autoclicker.engine

import com.example.autoclicker.executor.GestureExecutor
import com.example.autoclicker.model.GestureType
import com.example.autoclicker.model.Script
import kotlinx.coroutines.delay

/** 脚本回放器：按录制时的时间间隔恢复节奏。 */
object ScriptPlayer {

    suspend fun play(script: Script, executor: GestureExecutor) {
        for (action in script.actions) {
            if (action.delayAfterMs > 0) delay(action.delayAfterMs)
            when (action.type) {
                GestureType.TAP -> executor.tap(action.x, action.y, action.durationMs)
                GestureType.LONG_PRESS -> executor.longPress(action.x, action.y, action.durationMs)
                GestureType.SWIPE -> executor.swipe(action.x, action.y, action.toX, action.toY, action.durationMs)
            }
        }
    }
}
