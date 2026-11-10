package com.example.autoclicker.util

import android.content.Context
import android.view.WindowManager
import com.example.autoclicker.service.AutoClickAccessibilityService

/**
 * 悬浮窗窗口类型选择：
 * - 无障碍服务已连接 → TYPE_ACCESSIBILITY_OVERLAY（无需 SYSTEM_ALERT_WINDOW）
 * - 否则 → TYPE_APPLICATION_OVERLAY（需 SYSTEM_ALERT_WINDOW，主界面引导授权）
 *
 * 注意：TYPE_ACCESSIBILITY_OVERLAY 必须由无障碍服务自身的 WindowManager 添加，
 * 故 windowManager() 会优先返回服务实例的 WindowManager。
 */
object OverlayManager {

    fun windowType(): Int =
        if (AutoClickAccessibilityService.instance != null) {
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        }

    fun windowManager(context: Context): WindowManager {
        val svc = AutoClickAccessibilityService.instance
        return if (svc != null) svc.getSystemService(WindowManager::class.java)
        else context.getSystemService(WindowManager::class.java)
    }
}
