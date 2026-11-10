package com.example.autoclicker.util

import android.content.Context
import android.os.Build

/**
 * Android 17（API 37）高级保护模式（Advanced Protection Mode, APM）检测。
 *
 * 决策说明（重要）：
 * - APM 开启后，未被 Google 归类为“无障碍工具”的应用会被禁止/撤销 AccessibilityService 权限，
 *   自动化工具明确不在豁免之列。因此纯无障碍方案的自动点击器在 APM 下会失效。
 * - 本工具类在启动时探测 APM 状态，据此决定是否提示用户切换 Shizuku 后端。
 *
 * 实现说明：
 * AdvancedProtectionManager 是 Android 17 新引入的 API，其公开签名在 Beta 阶段仍可能调整，
 * 故采用“反射 + 多候选签名”做最大兼容，避免在尚未固化的 SDK 上发生编译期绑定失败。
 * 若最终 SDK 固化，可将 try 块替换为直连调用：
 *   val mgr = context.getSystemService(AdvancedProtectionManager::class.java)
 *   val enabled = mgr.isAdvancedProtectionEnabled()
 */
object AdvancedProtectionDetector {

    enum class State { ENABLED, DISABLED, UNKNOWN }

    private const val MANAGER_CLASS = "android.app.admin.AdvancedProtectionManager"

    fun query(context: Context): State {
        // APM 的严格无障碍管控仅在 Android 17+ 生效；更早版本直接视为关闭。
        if (Build.VERSION.SDK_INT < 37) return State.DISABLED
        return try {
            val cls = Class.forName(MANAGER_CLASS)
            val manager = context.getSystemService(cls)
                ?: context.getSystemService("advanced_protection")
                ?: return State.UNKNOWN

            val candidates = listOf(
                "isAdvancedProtectionEnabled",
                "isAdvancedProtectionActive",
                "isEnabled"
            )
            for (name in candidates) {
                val value = runCatching {
                    cls.getMethod(name).invoke(manager) as? Boolean
                }.getOrNull()
                if (value != null) return if (value) State.ENABLED else State.DISABLED
            }
            State.UNKNOWN
        } catch (t: Throwable) {
            State.UNKNOWN
        }
    }

    /** 是否“明确开启”。UNKNOWN 不视为开启，走保守提示路径。 */
    fun isEnabled(context: Context): Boolean = query(context) == State.ENABLED
}
