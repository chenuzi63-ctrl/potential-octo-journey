package com.example.autoclicker.model

import java.util.UUID

/**
 * 单个点击点（一个独立的点击任务）。
 * 每个点独立设置间隔、时长、次数；count == 0 表示无限次。
 */
data class ClickPoint(
    val id: String = UUID.randomUUID().toString(),
    var label: String = "",
    /** 屏幕绝对坐标 X */
    var x: Float = 0f,
    /** 屏幕绝对坐标 Y */
    var y: Float = 0f,
    /** 滑动终点（GestureType.SWIPE 时有效） */
    var toX: Float = 0f,
    var toY: Float = 0f,
    /** 两次动作之间的间隔（毫秒），最小 10ms */
    var intervalMs: Long = 1000L,
    /** 按下持续时间（毫秒）；TAP 一般 30~80ms */
    var durationMs: Long = 50L,
    /** 执行次数；0 表示无限次 */
    var count: Int = 0,
    var type: GestureType = GestureType.TAP,
    var enabled: Boolean = true
)
