package com.example.autoclicker.shizuku

import android.view.InputDevice
import android.view.InputEvent
import android.view.MotionEvent

/**
 * Shizuku 用户服务（User Service）。
 *
 * 依据 Shizuku 官方 API 文档，这里有三个关键点（也是本项目最初踩坑的地方）：
 * 1. 包名是 `rikka.shizuku`（v11 起从 moe.shizuku.api 改名）；
 * 2. 用户服务类**不是** android.app.Service，也**没有** UserService 基类；
 *    它必须直接继承自己的 AIDL Stub（IBinder），由 Shizuku 在 shell(2000) 权限进程中反射实例化；
 * 3. 该进程不是正常应用进程，拿不到可用的 Context，所以 InputManager 通过隐藏静态方法反射获取；
 *    用户服务进程不受非 SDK 接口限制，可正常使用 injectInputEvent。
 *
 * 注入发生在 AIDL Binder 线程（天然不在主线程），符合“后台线程注入”的要求。
 */
class ShizukuGestureService : IGestureService.Stub() {

    /** 隐藏 API：InputManager.getInstance() */
    private val inputManager: Any? by lazy {
        runCatching {
            Class.forName("android.hardware.input.InputManager")
                .getMethod("getInstance")
                .invoke(null)
        }.getOrNull()
    }

    /** 隐藏 API：InputManager.injectInputEvent(InputEvent, int) */
    private val injectMethod by lazy {
        Class.forName("android.hardware.input.InputManager")
            .getMethod("injectInputEvent", InputEvent::class.java, Integer.TYPE)
    }

    override fun inject(action: Int, x: Float, y: Float, downTime: Long, eventTime: Long): Boolean {
        val manager = inputManager ?: return false
        return runCatching {
            val event = MotionEvent.obtain(downTime, eventTime, action, x, y, 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            val ok = injectMethod.invoke(manager, event, INJECT_MODE_ASYNC) as? Boolean ?: false
            event.recycle()
            ok
        }.getOrDefault(false)
    }

    companion object {
        // InputManager.INJECT_INPUT_EVENT_MODE_ASYNC
        private const val INJECT_MODE_ASYNC = 0
    }
}
