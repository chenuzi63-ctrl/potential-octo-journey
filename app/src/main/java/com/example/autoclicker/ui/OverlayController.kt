package com.example.autoclicker.ui

import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.example.autoclicker.engine.AutoClickEngine
import com.example.autoclicker.model.ClickPoint
import com.example.autoclicker.model.GestureType
import com.example.autoclicker.service.AutoClickForegroundService
import com.example.autoclicker.util.ConfigStore

/**
 * 悬浮窗（控制条 + 点击点浮层）的统一协调者。
 * 控制条按钮动作在此与引擎、配置、点浮层联动。
 */
object OverlayController {

    private var bar: FloatingControlBar? = null
    private var editing = false

    fun show(context: Context) {
        // 统一使用 applicationContext，避免悬浮窗生命周期被 Activity 重建（如旋转屏幕）拖累。
        val app = context.applicationContext
        if (bar == null) {
            val b = FloatingControlBar(app)
            b.callback = object : FloatingControlBar.Callback {
                override fun onToggleStart() {
                    if (AutoClickEngine.isRunning) {
                        AutoClickForegroundService.stop(app)
                    } else {
                        // Android 12+ 后台启动前台服务可能被系统拒绝，失败时引导用户从应用内启动
                        val ok = AutoClickForegroundService.start(app)
                        if (!ok) {
                            Toast.makeText(
                                app,
                                "后台启动被系统限制，请打开应用点「开始」",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }

                override fun onAddPoint() = addPoint(app)

                override fun onRemovePoint() = removePoint(app)

                override fun onToggleEdit() = toggleEdit(app)

                override fun onOpenSettings() {
                    app.startActivity(
                        Intent(app, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }

                override fun onClose() = hide()
            }
            bar = b
        }
        // 先铺圆点、再显示控制条，保证控制条位于最上层且始终可点击，
        // 避免用户进入定位模式后无法退出。
        showPointOverlay(app)
        bar?.show()
        bar?.bringToFront()
        refresh()
    }

    fun hide() {
        bar?.hide()
        bar = null
        PointOverlay.hide()
    }

    fun toggle(context: Context) {
        if (bar?.isShowing() == true) hide() else show(context)
    }

    fun refresh() {
        val b = bar ?: return
        b.applyStyle(ConfigStore.loadBarState(b.context))
        b.setRunning(AutoClickEngine.isRunning)
        PointOverlay.setPoints(ConfigStore.loadConfig(b.context).points)
    }

    fun addPoint(context: Context) {
        val cfg = ConfigStore.loadConfig(context)
        val newPoint = ClickPoint(
            label = "点 ${cfg.points.size + 1}",
            x = cfg.screenWidth / 2f,
            y = cfg.screenHeight / 2f,
            intervalMs = 1000,
            durationMs = 50,
            count = 0,
            type = GestureType.TAP
        )
        ConfigStore.saveConfig(context, cfg.copy(points = cfg.points + newPoint))
        // 同时把控制条显示出来：否则用户进入定位模式后没有入口退出
        show(context)
        setEditing(true)
        Toast.makeText(
            context.applicationContext,
            "定位模式：拖动圆点，完成后点悬浮条「定位」退出",
            Toast.LENGTH_LONG
        ).show()
    }

    fun removePoint(context: Context) {
        val cfg = ConfigStore.loadConfig(context)
        if (cfg.points.isEmpty()) return
        ConfigStore.saveConfig(context, cfg.copy(points = cfg.points.dropLast(1)))
        refresh()
    }

    fun toggleEdit(context: Context) {
        showPointOverlay(context)
        setEditing(!editing)
    }

    fun setEditing(value: Boolean) {
        editing = value
        PointOverlay.setEditing(value)
    }

    private fun showPointOverlay(context: Context) {
        if (!PointOverlay.isShowing()) {
            PointOverlay.show(context, ConfigStore.loadConfig(context).points) { points ->
                val cfg = ConfigStore.loadConfig(context)
                ConfigStore.saveConfig(context, cfg.copy(points = points))
            }
        } else {
            PointOverlay.setPoints(ConfigStore.loadConfig(context).points)
        }
    }
}
