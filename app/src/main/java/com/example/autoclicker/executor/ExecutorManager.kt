package com.example.autoclicker.executor

/**
 * 双后端调度器。
 * - 无障碍可用（服务已连接）→ 无障碍（主路径）
 * - 无障碍不可用（APM 开启导致被撤销 / 用户未授权）→ Shizuku（若可用）
 * - 都不可用 → null，由上层提示用户授权或降级
 */
object ExecutorManager {

    fun resolveBest(): GestureExecutor? {
        if (AccessibilityGestureExecutor.isPotentiallyAvailable()) return AccessibilityGestureExecutor
        if (ShizukuGestureExecutor.isPotentiallyAvailable()) return ShizukuGestureExecutor
        return null
    }

    /** 供 UI 展示当前推荐后端 */
    fun recommendedId(): String = resolveBest()?.id ?: "none"
}
