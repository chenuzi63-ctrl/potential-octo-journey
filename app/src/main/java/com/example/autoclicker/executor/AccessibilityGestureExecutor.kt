package com.example.autoclicker.executor

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import com.example.autoclicker.service.AutoClickAccessibilityService
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 后端 A：AccessibilityService.dispatchGesture（主路径，默认）。
 * 优点：无需 Root、无需额外进程、覆盖大多数未开启 APM 的设备。
 * 缺点：Android 17 APM 下可能被系统禁止/撤销，因此不能作为唯一依赖。
 *
 * 坐标说明：dispatchGesture 使用屏幕绝对坐标；真机上状态栏/导航栏可能造成偏移，
 * 本应用采用“绝对坐标 + 所见即所得拖点定位”规避偏移问题（用户直接把点拖到目标位置）。
 */
object AccessibilityGestureExecutor : GestureExecutor {

    override val id = "accessibility"
    override val displayName = "无障碍手势"

    /** 由 [AutoClickAccessibilityService.onServiceConnected] 注入的实例 */
    @Volatile
    var service: AutoClickAccessibilityService? = null

    override fun isPotentiallyAvailable(): Boolean = service != null

    override suspend fun ensureReady(): Boolean = service != null

    override suspend fun tap(x: Float, y: Float, durationMs: Long) {
        dispatch(x, y, x, y, durationMs)
    }

    override suspend fun longPress(x: Float, y: Float, durationMs: Long) {
        tap(x, y, durationMs)
    }

    override suspend fun swipe(fromX: Float, fromY: Float, toX: Float, toY: Float, durationMs: Long) {
        dispatch(fromX, fromY, toX, toY, durationMs)
    }

    private suspend fun dispatch(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long) {
        suspendCancellableCoroutine<Unit> { cont ->
            val svc = service
            if (svc == null) {
                cont.resumeWithException(GestureException("无障碍服务未连接"))
                return@suspendCancellableCoroutine
            }
            val path = Path().apply {
                moveTo(startX, startY)
                if (startX != endX || startY != endY) lineTo(endX, endY)
            }
            val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()

            val accepted = svc.dispatchGesture(
                gesture,
                object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(g: GestureDescription?) {
                        if (cont.isActive) cont.resume(Unit)
                    }

                    override fun onCancelled(g: GestureDescription?) {
                        if (cont.isActive) cont.resumeWithException(GestureException("手势被系统取消"))
                    }
                },
                null
            )
            if (!accepted && cont.isActive) {
                cont.resumeWithException(GestureException("dispatchGesture 返回 false"))
            }
        }
    }
}

class GestureException(message: String) : Exception(message)
