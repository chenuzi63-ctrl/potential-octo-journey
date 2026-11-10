package com.example.autoclicker.model

/**
 * 全局点击配置。所有用户配置仅保存在本机，不上传任何数据。
 */
data class ClickConfig(
    val points: List<ClickPoint>,
    val screenWidth: Int = 1080,
    val screenHeight: Int = 1920,
    /** 多层紧急停止之一：息屏自动停止 */
    val stopOnScreenOff: Boolean = true,
    /** 运行时是否录制脚本 */
    val recordWhileRunning: Boolean = false
) {
    val enabledPoints: List<ClickPoint> get() = points.filter { it.enabled }
}
