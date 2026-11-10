package com.example.autoclicker.util

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import com.example.autoclicker.service.AutoClickAccessibilityService

/** 无障碍可用性检测工具类 */
object AccessibilityDetector {

    /** 是否已在系统设置中授予无障碍权限（可能尚未连接） */
    fun isPermissionGranted(context: Context): Boolean {
        val expected = ComponentName(context, AutoClickAccessibilityService::class.java)
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == expected }
    }

    /** 服务是否已真正连接（可立即注入手势） */
    fun isServiceRunning(): Boolean = AutoClickAccessibilityService.instance != null
}
