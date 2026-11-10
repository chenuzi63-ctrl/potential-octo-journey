package com.example.autoclicker.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import com.example.autoclicker.R
import com.example.autoclicker.util.ConfigStore
import com.example.autoclicker.util.OverlayManager
import kotlin.math.abs

/**
 * 悬浮窗控制条：开始/停止、添加、减少、定位、设置、关闭；支持拖动与边缘吸附、
 * 横排/竖排/极简排列、大小与透明度可调。
 *
 * 触摸穿透：本窗口仅占用控制条自身大小，控制条以外的屏幕区域触摸自然落到下层应用，
 * 因此无需额外处理“覆盖区域外的穿透”。
 */
class FloatingControlBar(val context: Context) {

    interface Callback {
        fun onToggleStart()
        fun onAddPoint()
        fun onRemovePoint()
        fun onToggleEdit()
        fun onOpenSettings()
        fun onClose()
    }

    var callback: Callback? = null

    private var wm: WindowManager? = null
    private var view: View? = null

    fun isShowing(): Boolean = view != null

    fun show() {
        if (view != null) return
        val v = LayoutInflater.from(context).inflate(R.layout.view_floating_bar, null)
        view = v

        val state = ConfigStore.loadBarState(context)
        val windowManager = OverlayManager.windowManager(context)
        this.wm = windowManager

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            OverlayManager.windowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = state.x
        params.y = state.y

        applyStyle(state)
        wireButtons(v)
        setupDrag(v, params, windowManager)

        windowManager.addView(v, params)
    }

    private fun wireButtons(v: View) {
        v.findViewById<Button>(R.id.btnBarStart).setOnClickListener { callback?.onToggleStart() }
        v.findViewById<Button>(R.id.btnBarAdd).setOnClickListener { callback?.onAddPoint() }
        v.findViewById<Button>(R.id.btnBarRemove).setOnClickListener { callback?.onRemovePoint() }
        v.findViewById<Button>(R.id.btnBarEdit).setOnClickListener { callback?.onToggleEdit() }
        v.findViewById<Button>(R.id.btnBarSettings).setOnClickListener { callback?.onOpenSettings() }
        v.findViewById<Button>(R.id.btnBarClose).setOnClickListener { callback?.onClose() }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupDrag(v: View, params: WindowManager.LayoutParams, windowManager: WindowManager) {
        val handle: View = v.findViewById(R.id.barDragHandle)
        var downRawX = 0f
        var downRawY = 0f
        var startX = 0
        var startY = 0
        var dragging = false
        handle.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = params.x
                    startY = params.y
                    dragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downRawX).toInt()
                    val dy = (event.rawY - downRawY).toInt()
                    if (abs(dx) > 4 || abs(dy) > 4) dragging = true
                    if (dragging) {
                        params.x = startX + dx
                        params.y = startY + dy
                        windowManager.updateViewLayout(v, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (dragging) snapToEdge(params, windowManager, v)
                    persistPosition(params)
                    true
                }
                else -> false
            }
        }
    }

    private fun snapToEdge(params: WindowManager.LayoutParams, windowManager: WindowManager, v: View) {
        val dm = context.resources.displayMetrics
        val barW = v.width.coerceAtLeast(1)
        val left = params.x
        val right = dm.widthPixels - barW - params.x
        params.x = if (left < right) 0 else dm.widthPixels - barW
        windowManager.updateViewLayout(v, params)
    }

    private fun persistPosition(params: WindowManager.LayoutParams) {
        val state = ConfigStore.loadBarState(context)
        ConfigStore.saveBarState(context, state.copy(x = params.x, y = params.y))
    }

    fun applyStyle(state: ConfigStore.BarState) {
        val v = view ?: return
        val root = v.findViewById<LinearLayout>(R.id.barRoot)
        root.orientation = when (state.orientation) {
            ConfigStore.Orientation.HORIZONTAL -> LinearLayout.HORIZONTAL
            ConfigStore.Orientation.VERTICAL -> LinearLayout.VERTICAL
            ConfigStore.Orientation.MINIMAL -> LinearLayout.HORIZONTAL
        }
        val minimal = state.orientation == ConfigStore.Orientation.MINIMAL
        v.findViewById<View>(R.id.btnBarAdd).visibility = if (minimal) View.GONE else View.VISIBLE
        v.findViewById<View>(R.id.btnBarRemove).visibility = if (minimal) View.GONE else View.VISIBLE
        v.findViewById<View>(R.id.btnBarEdit).visibility = if (minimal) View.GONE else View.VISIBLE
        v.findViewById<View>(R.id.btnBarSettings).visibility = if (minimal) View.GONE else View.VISIBLE
        v.findViewById<View>(R.id.btnBarClose).visibility = if (minimal) View.GONE else View.VISIBLE

        root.alpha = state.opacity
        root.scaleX = state.scale
        root.scaleY = state.scale
    }

    fun setRunning(running: Boolean) {
        view?.findViewById<Button>(R.id.btnBarStart)?.text = if (running) "■" else "▶"
    }

    fun hide() {
        val v = view ?: return
        runCatching { wm?.removeView(v) }
        view = null
        wm = null
    }
}
