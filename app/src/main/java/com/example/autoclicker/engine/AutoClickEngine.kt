package com.example.autoclicker.engine

import com.example.autoclicker.App
import com.example.autoclicker.executor.ExecutorManager
import com.example.autoclicker.executor.GestureException
import com.example.autoclicker.executor.GestureExecutor
import com.example.autoclicker.model.ClickConfig
import com.example.autoclicker.model.ClickPoint
import com.example.autoclicker.model.GestureType
import com.example.autoclicker.util.ConfigStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * 点击循环引擎（协程驱动，不使用 Thread.sleep）。
 * - 每个启用的点击点一个独立协程，各自维护间隔 / 次数（0 = 无限）。
 * - 后端由 [ExecutorManager] 解析；注入失败短暂退避，避免空转。
 */
object AutoClickEngine {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val pointJobs = ConcurrentHashMap<String, Job>()

    @Volatile
    var isRunning: Boolean = false
        private set

    @Volatile
    var currentExecutor: GestureExecutor? = null
        private set

    @Volatile
    var recorder: GestureRecorder? = null
        private set

    @Volatile
    var totalClicks: Long = 0
        private set

    /** 启动点击循环。返回实际使用的执行后端；无可用后端时返回 null。 */
    suspend fun start(config: ClickConfig, record: Boolean): GestureExecutor? {
        stop()
        val executor = ExecutorManager.resolveBest() ?: return null
        if (!executor.ensureReady()) return null

        currentExecutor = executor
        isRunning = true
        totalClicks = 0
        recorder = if (record) GestureRecorder() else null

        for (point in config.enabledPoints) {
            val job = scope.launch { runPoint(point, executor) }
            pointJobs[point.id] = job
        }
        return executor
    }

    private suspend fun runPoint(point: ClickPoint, executor: GestureExecutor) {
        var executed = 0
        while (isRunning && (point.count == 0 || executed < point.count)) {
            try {
                execute(executor, point)
                executed++
                totalClicks++
                if (point.count != 0 && executed >= point.count) break
                delay(point.intervalMs)
            } catch (e: CancellationException) {
                throw e
            } catch (e: GestureException) {
                delay(100)
            } catch (e: Exception) {
                if (!isRunning) return
                delay(100)
            }
        }
    }

    private suspend fun execute(executor: GestureExecutor, point: ClickPoint) {
        when (point.type) {
            GestureType.TAP -> {
                executor.tap(point.x, point.y, point.durationMs)
                recorder?.recordTap(point, point.durationMs)
            }
            GestureType.LONG_PRESS -> {
                executor.longPress(point.x, point.y, point.durationMs)
                recorder?.recordLongPress(point, point.durationMs)
            }
            GestureType.SWIPE -> {
                executor.swipe(point.x, point.y, point.toX, point.toY, point.durationMs)
                recorder?.recordSwipe(point, point.durationMs)
            }
        }
    }

    /** 紧急停止 / 正常停止：取消所有点击任务并保存录制脚本。 */
    fun stop() {
        isRunning = false
        saveRecorderIfNeeded()
        recorder = null
        currentExecutor = null
        pointJobs.values.forEach { it.cancel() }
        pointJobs.clear()
    }

    private fun saveRecorderIfNeeded() {
        val rec = recorder ?: return
        val script = rec.build()
        if (script.actions.isEmpty()) return
        // 防御式获取 Application 实例（正常情况下 App.onCreate 已先行执行）
        val app = runCatching { App.instance }.getOrNull() ?: return
        ConfigStore.saveScripts(app, ConfigStore.loadScripts(app) + script)
    }
}
