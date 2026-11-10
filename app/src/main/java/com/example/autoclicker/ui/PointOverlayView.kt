package com.example.autoclicker.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.example.autoclicker.model.ClickPoint
import com.example.autoclicker.util.OverlayManager

/**
 * 单个点击点的圆点视图。
 * 每个圆点是一个**独立的小窗口**（64dp），因此：
 * - 只有圆点这一小块区域会接收触摸；
 * - 屏幕其它区域的触摸会正常穿透到下层应用（这是全屏浮层做不到的）。
 */
class PointDotView(context: Context) : View(context) {

    private var index: Int = 1
    private var active: Boolean = false

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E53935")
        style = Paint.Style.FILL
    }
    private val activePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
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
        textSize = dp(14f)
    }

    fun setIndex(value: Int) {
        if (index != value) {
            index = value
            invalidate()
        }
    }

    fun setActive(value: Boolean) {
        if (active != value) {
            active = value
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = (minOf(width, height) / 2f) - dp(6f)
        if (radius <= 0f) return
        canvas.drawCircle(cx, cy, radius, if (active) activePaint else fillPaint)
        canvas.drawCircle(cx, cy, radius, strokePaint)
        canvas.drawText(index.toString(), cx, cy + dp(5f), textPaint)
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}

/**
 * 点击点浮层管理者。
 *
 * 设计要点（相对旧实现的修复）：
 * - 旧实现用一个全屏 View 承载所有圆点；全屏窗口一旦可触摸，就会拦截整块屏幕的触摸，
 *   导致“只能拖圆点、无法点击其它地方”。
 * - 现改为「一个圆点 = 一个小窗口」：非编辑态窗口设 FLAG_NOT_TOUCHABLE（只显示不拦截），
 *   编辑态只放开每个小窗口自身的触摸区域，屏幕其余部分依然穿透到下层应用。
 */
object PointOverlay {

    private const val WINDOW_SIZE_DP = 64f

    private var wm: WindowManager? = null
    private var appContext: Context? = null
    private var editing: Boolean = false
    private var onPointsChanged: ((List<ClickPoint>) -> Unit)? = null

    /** 当前点的内存副本（拖动过程中实时更新，抬起时回调持久化） */
    private val points = mutableListOf<ClickPoint>()

    private class Entry(
        val view: PointDotView,
        val params: WindowManager.LayoutParams,
        var point: ClickPoint
    )

    /** 按点击点 id 维护窗口，保证增删改时不重建已存在的窗口 */
    private val entries = LinkedHashMap<String, Entry>()

    fun isShowing(): Boolean = wm != null

    fun show(context: Context, list: List<ClickPoint>, onChanged: (List<ClickPoint>) -> Unit) {
        hide()
        appContext = context.applicationContext
        onPointsChanged = onChanged
        wm = OverlayManager.windowManager(context)
        points.clear()
        points.addAll(list)
        rebuild()
        applyTouchability()
    }

    fun setPoints(list: List<ClickPoint>) {
        if (wm == null) return
        points.clear()
        points.addAll(list)
        rebuild()
    }

    fun setEditing(value: Boolean) {
        editing = value
        applyTouchability()
    }

    fun hide() {
        val manager = wm
        entries.values.forEach { entry -> runCatching { manager?.removeView(entry.view) } }
        entries.clear()
        points.clear()
        wm = null
        appContext = null
        editing = false
        onPointsChanged = null
    }

    private fun windowSize(context: Context): Int =
        (WINDOW_SIZE_DP * context.resources.displayMetrics.density).toInt()

    private fun rebuild() {
        val manager = wm ?: return
        val ctx = appContext ?: return
        val size = windowSize(ctx)
        val aliveIds = points.map { it.id }.toHashSet()

        // 1) 移除已删除的点
        val iterator = entries.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.key !in aliveIds) {
                runCatching { manager.removeView(entry.value.view) }
                iterator.remove()
            }
        }

        // 2) 新增 / 更新
        points.forEachIndexed { index, point ->
            val existing = entries[point.id]
            if (existing == null) {
                val dot = PointDotView(ctx)
                dot.setIndex(index + 1)
                val params = WindowManager.LayoutParams(
                    size,
                    size,
                    OverlayManager.windowType(),
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.START
                    x = (point.x - size / 2f).toInt()
                    y = (point.y - size / 2f).toInt()
                }
                attachDrag(dot, params, point, size)
                runCatching { manager.addView(dot, params) }
                entries[point.id] = Entry(dot, params, point)
            } else {
                existing.point = point
                existing.view.setIndex(index + 1)
                existing.params.x = (point.x - size / 2f).toInt()
                existing.params.y = (point.y - size / 2f).toInt()
                runCatching { manager.updateViewLayout(existing.view, existing.params) }
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attachDrag(
        dot: PointDotView,
        params: WindowManager.LayoutParams,
        point: ClickPoint,
        size: Int
    ) {
        val manager = wm ?: return
        val metrics = dot.resources.displayMetrics

        var downRawX = 0f
        var downRawY = 0f
        var startX = 0
        var startY = 0

        dot.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = params.x
                    startY = params.y
                    dot.setActive(true)
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downRawX).toInt()
                    val dy = (event.rawY - downRawY).toInt()
                    params.x = (startX + dx).coerceIn(0, (metrics.widthPixels - size).coerceAtLeast(0))
                    params.y = (startY + dy).coerceIn(0, (metrics.heightPixels - size).coerceAtLeast(0))
                    runCatching { manager.updateViewLayout(dot, params) }
                    updatePoint(point.id, params.x + size / 2f, params.y + size / 2f, persist = false)
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    dot.setActive(false)
                    updatePoint(point.id, params.x + size / 2f, params.y + size / 2f, persist = true)
                    true
                }

                else -> false
            }
        }
    }

    private fun updatePoint(id: String, x: Float, y: Float, persist: Boolean) {
        val index = points.indexOfFirst { it.id == id }
        if (index < 0) return
        val metricsW = appContext?.resources?.displayMetrics?.widthPixels ?: 0
        val metricsH = appContext?.resources?.displayMetrics?.heightPixels ?: 0
        val updated = points[index].copy(
            x = x.coerceIn(0f, metricsW.toFloat()),
            y = y.coerceIn(0f, metricsH.toFloat())
        )
        points[index] = updated
        entries[id]?.point = updated
        if (persist) onPointsChanged?.invoke(points.toList())
    }

    /** 编辑态：只放开圆点小窗口的触摸；非编辑态：全部不可触摸（穿透） */
    private fun applyTouchability() {
        val manager = wm ?: return
        entries.values.forEach { entry ->
            entry.params.flags = if (editing) {
                entry.params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            } else {
                entry.params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            }
            runCatching { manager.updateViewLayout(entry.view, entry.params) }
        }
    }
}
