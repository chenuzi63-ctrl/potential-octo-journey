package com.example.autoclicker.engine

import android.os.SystemClock
import com.example.autoclicker.model.ClickPoint
import com.example.autoclicker.model.GestureType
import com.example.autoclicker.model.Script
import com.example.autoclicker.model.ScriptAction

/**
 * 录制器：把实际执行的每一次手势按时间顺序记录成脚本。
 * 只记录坐标与时间，不采集任何屏幕内容。
 */
class GestureRecorder {

    private val start = SystemClock.elapsedRealtime()
    private val actions = mutableListOf<ScriptAction>()

    @Volatile
    private var lastAt = start

    fun recordTap(point: ClickPoint, durationMs: Long) {
        record(GestureType.TAP, point.x, point.y, 0f, 0f, durationMs)
    }

    fun recordLongPress(point: ClickPoint, durationMs: Long) {
        record(GestureType.LONG_PRESS, point.x, point.y, 0f, 0f, durationMs)
    }

    fun recordSwipe(point: ClickPoint, durationMs: Long) {
        record(GestureType.SWIPE, point.x, point.y, point.toX, point.toY, durationMs)
    }

    private fun record(type: GestureType, x: Float, y: Float, toX: Float, toY: Float, durationMs: Long) {
        synchronized(actions) {
            val now = SystemClock.elapsedRealtime()
            val delayAfter = (now - lastAt).coerceAtLeast(0)
            lastAt = now
            actions += ScriptAction(type, x, y, toX, toY, durationMs, delayAfter)
        }
    }

    fun build(name: String = "录制 ${System.currentTimeMillis()}"): Script {
        synchronized(actions) {
            return Script(
                id = java.util.UUID.randomUUID().toString(),
                name = name,
                createdAt = System.currentTimeMillis(),
                actions = actions.toList()
            )
        }
    }
}
