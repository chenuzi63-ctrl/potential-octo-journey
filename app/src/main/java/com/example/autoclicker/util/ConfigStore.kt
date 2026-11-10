package com.example.autoclicker.util

import android.content.Context
import com.example.autoclicker.model.ClickConfig
import com.example.autoclicker.model.ClickPoint
import com.example.autoclicker.model.GestureType
import com.example.autoclicker.model.Script
import com.example.autoclicker.model.ScriptAction
import org.json.JSONArray
import org.json.JSONObject

/**
 * 本地持久化：点击点位置、间隔、时长、次数、悬浮窗状态、录制脚本。
 * 仅写入 SharedPreferences，不上传任何数据。
 */
object ConfigStore {

    enum class Orientation { HORIZONTAL, VERTICAL, MINIMAL }

    data class BarState(
        val x: Int = 0,
        val y: Int = 200,
        val orientation: Orientation = Orientation.HORIZONTAL,
        val scale: Float = 1f,
        val opacity: Float = 1f
    )

    private const val PREFS = "autoclicker"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun ensureDefaults(context: Context) {
        val p = prefs(context)
        if (!p.contains("click_config")) {
            saveConfig(context, ClickConfig(emptyList(), screenW(context), screenH(context)))
        }
    }

    private fun screenW(context: Context) = context.resources.displayMetrics.widthPixels
    private fun screenH(context: Context) = context.resources.displayMetrics.heightPixels

    // ---------- 点击配置 ----------
    fun loadConfig(context: Context): ClickConfig {
        val raw = prefs(context).getString("click_config", null)
            ?: return ClickConfig(emptyList(), screenW(context), screenH(context))
        return runCatching { decodeConfig(JSONObject(raw), context) }
            .getOrDefault(ClickConfig(emptyList(), screenW(context), screenH(context)))
    }

    fun saveConfig(context: Context, config: ClickConfig) {
        prefs(context).edit().putString("click_config", encodeConfig(config).toString()).apply()
        ConfigBus.notifyChanged()
    }

    private fun encodeConfig(c: ClickConfig) = JSONObject().apply {
        put("screenWidth", c.screenWidth)
        put("screenHeight", c.screenHeight)
        put("stopOnScreenOff", c.stopOnScreenOff)
        put("recordWhileRunning", c.recordWhileRunning)
        put("points", JSONArray().apply { c.points.forEach { put(encodePoint(it)) } })
    }

    private fun encodePoint(p: ClickPoint) = JSONObject().apply {
        put("id", p.id)
        put("label", p.label)
        put("x", p.x.toDouble())
        put("y", p.y.toDouble())
        put("toX", p.toX.toDouble())
        put("toY", p.toY.toDouble())
        put("intervalMs", p.intervalMs)
        put("durationMs", p.durationMs)
        put("count", p.count)
        put("type", p.type.name)
        put("enabled", p.enabled)
    }

    private fun decodeConfig(o: JSONObject, context: Context): ClickConfig {
        val points = mutableListOf<ClickPoint>()
        o.optJSONArray("points")?.let { arr ->
            for (i in 0 until arr.length()) points += decodePoint(arr.getJSONObject(i))
        }
        return ClickConfig(
            points = points,
            screenWidth = o.optInt("screenWidth", screenW(context)),
            screenHeight = o.optInt("screenHeight", screenH(context)),
            stopOnScreenOff = o.optBoolean("stopOnScreenOff", true),
            recordWhileRunning = o.optBoolean("recordWhileRunning", false)
        )
    }

    private fun decodePoint(o: JSONObject) = ClickPoint(
        id = o.optString("id").ifEmpty { java.util.UUID.randomUUID().toString() },
        label = o.optString("label"),
        x = o.optDouble("x", 0.0).toFloat(),
        y = o.optDouble("y", 0.0).toFloat(),
        toX = o.optDouble("toX", 0.0).toFloat(),
        toY = o.optDouble("toY", 0.0).toFloat(),
        intervalMs = o.optLong("intervalMs", 1000L),
        durationMs = o.optLong("durationMs", 50L),
        count = o.optInt("count", 0),
        type = runCatching { GestureType.valueOf(o.optString("type")) }.getOrDefault(GestureType.TAP),
        enabled = o.optBoolean("enabled", true)
    )

    // ---------- 录制脚本 ----------
    fun loadScripts(context: Context): List<Script> {
        val raw = prefs(context).getString("scripts", null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { decodeScript(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    fun saveScripts(context: Context, scripts: List<Script>) {
        prefs(context).edit().putString(
            "scripts",
            JSONArray().apply { scripts.forEach { put(encodeScript(it)) } }.toString()
        ).apply()
    }

    private fun encodeScript(s: Script) = JSONObject().apply {
        put("id", s.id)
        put("name", s.name)
        put("createdAt", s.createdAt)
        put("actions", JSONArray().apply { s.actions.forEach { put(encodeAction(it)) } })
    }

    private fun encodeAction(a: ScriptAction) = JSONObject().apply {
        put("type", a.type.name)
        put("x", a.x.toDouble())
        put("y", a.y.toDouble())
        put("toX", a.toX.toDouble())
        put("toY", a.toY.toDouble())
        put("durationMs", a.durationMs)
        put("delayAfterMs", a.delayAfterMs)
    }

    private fun decodeScript(o: JSONObject): Script = Script(
        id = o.optString("id"),
        name = o.optString("name"),
        createdAt = o.optLong("createdAt"),
        actions = o.optJSONArray("actions")?.let { arr ->
            (0 until arr.length()).map { i ->
                val a = arr.getJSONObject(i)
                ScriptAction(
                    type = runCatching { GestureType.valueOf(a.optString("type")) }.getOrDefault(GestureType.TAP),
                    x = a.optDouble("x", 0.0).toFloat(),
                    y = a.optDouble("y", 0.0).toFloat(),
                    toX = a.optDouble("toX", 0.0).toFloat(),
                    toY = a.optDouble("toY", 0.0).toFloat(),
                    durationMs = a.optLong("durationMs", 50L),
                    delayAfterMs = a.optLong("delayAfterMs", 0L)
                )
            }
        } ?: emptyList()
    )

    // ---------- 悬浮窗状态 ----------
    fun loadBarState(context: Context): BarState {
        val p = prefs(context)
        return BarState(
            x = p.getInt("bar_x", 0),
            y = p.getInt("bar_y", 200),
            orientation = runCatching {
                Orientation.valueOf(p.getString("bar_orientation", Orientation.HORIZONTAL.name)!!)
            }.getOrDefault(Orientation.HORIZONTAL),
            scale = p.getFloat("bar_scale", 1f),
            opacity = p.getFloat("bar_opacity", 1f)
        )
    }

    fun saveBarState(context: Context, state: BarState) {
        prefs(context).edit()
            .putInt("bar_x", state.x)
            .putInt("bar_y", state.y)
            .putString("bar_orientation", state.orientation.name)
            .putFloat("bar_scale", state.scale)
            .putFloat("bar_opacity", state.opacity)
            .apply()
        ConfigBus.notifyChanged()
    }
}
