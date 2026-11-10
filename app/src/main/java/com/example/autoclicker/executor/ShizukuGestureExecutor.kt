package com.example.autoclicker.executor

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import android.os.SystemClock
import android.view.MotionEvent
import com.example.autoclicker.App
import com.example.autoclicker.BuildConfig
import com.example.autoclicker.shizuku.IGestureService
import com.example.autoclicker.shizuku.ShizukuGestureService
import com.example.autoclicker.util.ShizukuDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku
import kotlin.coroutines.resume

/**
 * 后端 B：Shizuku 用户服务注入（备选路径）。
 * 用于 APM 开启 / 无障碍被撤销时。不是“零配置”：用户需安装 Shizuku 并完成 ADB/无线调试授权。
 *
 * 时序（长按、滑动插值）全部由协程 + delay 控制，不阻塞主线程；
 * 底层单事件注入走 AIDL，由用户服务的 Binder 线程执行。
 */
object ShizukuGestureExecutor : GestureExecutor {

    override val id = "shizuku"
    override val displayName = "Shizuku 注入"

    @Volatile
    private var binder: IGestureService? = null

    @Volatile
    private var connection: ServiceConnection? = null

    override fun isPotentiallyAvailable(): Boolean =
        ShizukuDetector.isAvailable(App.instance)

    override suspend fun ensureReady(): Boolean {
        if (binder != null) return true
        if (!isPotentiallyAvailable()) return false
        return connect()
    }

    private suspend fun connect(): Boolean {
        binder?.let { return true }
        return withTimeoutOrNull(CONNECT_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                val conn = object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                        val iface = service?.let { IGestureService.Stub.asInterface(it) }
                        binder = iface
                        if (cont.isActive) cont.resume(iface != null)
                    }

                    override fun onServiceDisconnected(name: ComponentName?) {
                        binder = null
                    }
                }
                connection = conn
                try {
                    val args = Shizuku.UserServiceArgs(
                        ComponentName(App.instance.packageName, ShizukuGestureService::class.java.name)
                    )
                        .daemon(false)
                        .processNameSuffix("gesture")
                        .version(1)
                        .debuggable(BuildConfig.DEBUG)
                    Shizuku.bindUserService(args, conn)
                    cont.invokeOnCancellation {
                        // 仅清理本地引用；不同 Shizuku 版本的 unbindUserService 签名不一致，
                        // 这里不做远程解绑，用户服务会随进程/授权状态自动结束。
                        if (connection === conn) connection = null
                    }
                } catch (t: Throwable) {
                    if (cont.isActive) cont.resume(false)
                }
            }
        } ?: false
    }

    override suspend fun tap(x: Float, y: Float, durationMs: Long) {
        val iface = ensure() ?: throw GestureException("Shizuku 服务不可用")
        val downTime = SystemClock.uptimeMillis()
        check(inject(iface, MotionEvent.ACTION_DOWN, x, y, downTime, downTime)) { "注入 DOWN 失败" }
        if (durationMs > 0) delay(durationMs)
        check(inject(iface, MotionEvent.ACTION_UP, x, y, downTime, downTime + durationMs)) { "注入 UP 失败" }
    }

    override suspend fun longPress(x: Float, y: Float, durationMs: Long) = tap(x, y, durationMs)

    override suspend fun swipe(fromX: Float, fromY: Float, toX: Float, toY: Float, durationMs: Long) {
        val iface = ensure() ?: throw GestureException("Shizuku 服务不可用")
        val downTime = SystemClock.uptimeMillis()
        check(inject(iface, MotionEvent.ACTION_DOWN, fromX, fromY, downTime, downTime)) { "注入 DOWN 失败" }
        val steps = 10
        val stepDuration = (durationMs / steps).coerceAtLeast(1)
        for (i in 1..steps) {
            delay(stepDuration)
            val t = i.toFloat() / steps
            val x = fromX + (toX - fromX) * t
            val y = fromY + (toY - fromY) * t
            inject(iface, MotionEvent.ACTION_MOVE, x, y, downTime, downTime + stepDuration * i)
        }
        inject(iface, MotionEvent.ACTION_UP, toX, toY, downTime, downTime + durationMs)
    }

    private suspend fun ensure(): IGestureService? {
        binder?.let { return it }
        return if (ensureReady()) binder else null
    }

    private suspend fun inject(
        iface: IGestureService,
        action: Int,
        x: Float,
        y: Float,
        downTime: Long,
        eventTime: Long
    ): Boolean = withContext(Dispatchers.IO) {
        runCatching { iface.inject(action, x, y, downTime, eventTime) }.getOrDefault(false)
    }

    private const val CONNECT_TIMEOUT_MS = 5000L
}
