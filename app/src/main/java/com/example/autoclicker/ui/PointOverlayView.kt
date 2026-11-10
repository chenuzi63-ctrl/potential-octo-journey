package com.example.autoclicker.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.example.autoclicker.model.ClickPoint
import com.example.autoclicker.util.OverlayManager
import kotlin.math.hypot

/**
 * 点击点浮层：以圆点显示所有点击点，支持拖动定位（所见即所得）。
 * - 非编辑态：窗口 FLAG_NOT_TOUCHABLE，触摸穿透到下层应用，仅作预览。
 * - 编辑态：窗口可触摸，仅命中圆点（含放大抓取半径）时消费事件并拖动。
 */
class PointOverlayView(
    context: Context,
    private val onPointsChanged: (List<ClickPoint>) -> Unit
) : View(context) {

    private var points: List<ClickPoint> = emptyList()
    private var editing = false
    private var activeIndex = -1
    private var lastX = 0f
    private var lastY = 0f

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E53935")
        style = Paint.Style.FILL
    }
    private val selectedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1E88E5")
        style = Paint.Style.FILL
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = dp(12f)
    }

    fun setPoints(list: List<ClickPoint>) {
        points = list
        invalidate()
    }

    fun setEditing(value: Boolean) {
        editing = value
        if (!value) activeIndex = -1
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        points.forEachIndexed { index, p ->
            val paint = if (index == activeIndex) selectedPaint else dotPaint
            val r = dp(14f)
            canvas.drawCircle(p.x, p.y, r, paint)
            canvas.drawCircle(p.x, p.y, r, strokePaint)
            canvas.drawText("${index + 1}", p.x, p.y + dp(4f), textPaint)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!editing) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activeIndex = hitTest(event.x, event.y)
                if (activeIndex < 0) return false
                lastX = event.rawX
                lastY = event.rawY
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (activeIndex < 0) return false
                val dx = event.rawX - lastX
                val dy = event.rawY - lastY
                lastX = event.rawX
                lastY = event.rawY
                val p = points[activeIndex]
                points = points.toMutableList().also {
                    it[activeIndex] = p.copy(x = clamp(p.x + dx), y = clamp(p.y + dy))
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (activeIndex >= 0) onPointsChanged(points)
                activeIndex = -1
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun hitTest(x: Float, y: Float): Int {
        val r = dp(26f)
        for (i in points.indices.reversed()) {
            if (hypot((x - points[i].x).toDouble(), (y - points[i].y).toDouble()) <= r) return i
        }
        return -1
    }

    private fun clamp(v: Float): Float =
        v.coerceIn(0f, width.toFloat()).coerceIn(0f, height.toFloat())

    private fun dp(v: Float): Float = v * resources.displayMetrics.density
}

/** [PointOverlayView] 的窗口管理者。 */
object PointOverlay {

    @Volatile
    private var view: PointOverlayView? = null

    @Volatile
    private var wm: WindowManager? = null

    fun isShowing(): Boolean = view != null

    fun show(context: Context, points: List<ClickPoint>, onPointsChanged: (List<ClickPoint>) -> Unit) {
        hide()
        val v = PointOverlayView(context, onPointsChanged)
        v.setPoints(points)
        val windowManager = OverlayManager.windowManager(context)
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            OverlayManager.windowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        windowManager.addView(v, lp)
        wm = windowManager
        view = v
    }

    fun setPoints(points: List<ClickPoint>) {
        view?.setPoints(points)
    }

    fun setEditing(editing: Boolean) {
        val v = view ?: return
        val windowManager = wm ?: return
        v.setEditing(editing)
        val lp = v.layoutParams as WindowManager.LayoutParams
        lp.flags = if (editing) {
            lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        } else {
            lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        windowManager.updateViewLayout(v, lp)
    }

    fun hide() {
        val v = view ?: return
        runCatching { wm?.removeView(v) }
        view = null
        wm = null
    }
}
