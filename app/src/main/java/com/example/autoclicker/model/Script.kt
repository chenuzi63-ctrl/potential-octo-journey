package com.example.autoclicker.model

/** 录制脚本中的一次动作 */
data class ScriptAction(
    val type: GestureType,
    val x: Float,
    val y: Float,
    val toX: Float = 0f,
    val toY: Float = 0f,
    val durationMs: Long,
    /** 相对上一动作开始时刻的间隔（毫秒），回放时据此恢复节奏 */
    val delayAfterMs: Long
)

/** 可回放脚本 */
data class Script(
    val id: String,
    val name: String,
    val createdAt: Long,
    val actions: List<ScriptAction>
)
