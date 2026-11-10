package com.example.autoclicker.executor

/**
 * 手势执行后端抽象。双后端架构的核心接口：
 * - [AccessibilityGestureExecutor]：AccessibilityService.dispatchGesture（主路径）
 * - [ShizukuGestureExecutor]：Shizuku 用户服务注入 InputEvent（APM / 无障碍不可用时的备选）
 *
 * 每次点击动作以独立配置对象（坐标、时长、手势类型）驱动，便于状态管理与后端切换。
 */
interface GestureExecutor {

    /** 唯一标识（用于日志与状态展示） */
    val id: String

    /** 人类可读名称 */
    val displayName: String

    /** 非阻塞快速探测：后端是否“可能”可用 */
    fun isPotentiallyAvailable(): Boolean

    /** 挂起式就绪：确保后端真正可用（例如等待 Shizuku 用户服务绑定完成） */
    suspend fun ensureReady(): Boolean

    /** 点击（按下 durationMs 后抬起） */
    suspend fun tap(x: Float, y: Float, durationMs: Long)

    /** 长按 */
    suspend fun longPress(x: Float, y: Float, durationMs: Long)

    /** 滑动 */
    suspend fun swipe(fromX: Float, fromY: Float, toX: Float, toY: Float, durationMs: Long)

    companion object {
        const val DEFAULT_TAP_MS = 50L
        /** 点击间隔最小支持 10ms；真机建议不低于 5ms 的 UI 事件间隔，避免被系统忽略 */
        const val MIN_INTERVAL_MS = 10L
    }
}
