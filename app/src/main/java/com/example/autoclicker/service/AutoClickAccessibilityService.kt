package com.example.autoclicker.service

import android.accessibilityservice.AccessibilityService
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.example.autoclicker.engine.AutoClickEngine
import com.example.autoclicker.executor.AccessibilityGestureExecutor

/**
 * 无障碍服务（后端 A 的载体）。
 *
 * Android 17 适配说明：
 * - 在 res/xml/accessibility_service_config.xml 中声明 isAccessibilityTool="true" 以争取 APM 豁免；
 *   但 Google 明确不豁免自动化工具，因此 APM 开启时本服务可能被系统撤销。
 * - 此时由 [com.example.autoclicker.executor.ExecutorManager] 自动切换到 Shizuku 后端。
 *
 * 隐私约束：canRetrieveWindowContent=false，不读取任何窗口内容/节点文本，仅注入手势。
 */
class AutoClickAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        AccessibilityGestureExecutor.service = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 不读取窗口内容，此处仅用于满足系统回调；无业务逻辑。
    }

    override fun onInterrupt() {
        // 服务被系统打断（例如 APM 撤销权限）时，执行紧急停止。
        AutoClickEngine.stop()
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        // 多层紧急停止之一：物理音量减键（最可靠通道）。需 flagRequestFilterKeyEvents 已开启。
        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN && event.action == KeyEvent.ACTION_DOWN) {
            AutoClickEngine.stop()
            return true
        }
        return super.onKeyEvent(event)
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        if (AccessibilityGestureExecutor.service === this) AccessibilityGestureExecutor.service = null
        super.onDestroy()
    }

    companion object {
        @Volatile
        var instance: AutoClickAccessibilityService? = null
            private set
    }
}
