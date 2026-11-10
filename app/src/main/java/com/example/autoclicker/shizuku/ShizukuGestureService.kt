package com.example.autoclicker.shizuku

import android.content.Context
import android.content.Intent
import android.hardware.input.InputManager
import android.os.IBinder
import android.view.InputDevice
import android.view.InputEvent
import android.view.MotionEvent
import moe.shizuku.api.UserService

/**
 * Shizuku 用户服务：运行在由 Shizuku 重启的、拥有 shell(2000) 权限的私有进程内。
 * 在此进程内通过反射调用 InputManager.injectInputEvent（hidden API）注入触摸事件，
 * 相当于拥有 ADB 级输入注入能力，全程不需要 Root。
 *
 * 注入在 AIDL Binder 线程执行（天然不在主线程），符合“事件注入需在后台线程”的要求。
 */
class ShizukuGestureService : UserService() {

    private val binder = object : IGestureService.Stub() {

        private val injectMethod by lazy {
            InputManager::class.java.getMethod(
                "injectInputEvent",
                InputEvent::class.java,
                Int::class.javaPrimitiveType
            )
        }

        override fun inject(action: Int, x: Float, y: Float, downTime: Long, eventTime: Long): Boolean {
            return runCatching {
                val inputManager = getSystemService(Context.INPUT_SERVICE) as InputManager
                val event = MotionEvent.obtain(downTime, eventTime, action, x, y, 0)
                event.source = InputDevice.SOURCE_TOUCHSCREEN
                val ok = injectMethod.invoke(inputManager, event, INJECT_MODE_ASYNC) as? Boolean ?: false
                event.recycle()
                ok
            }.getOrDefault(false)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = binder

    override fun onDestroy() {
        super.onDestroy()
    }

    companion object {
        // InputManager.INJECT_INPUT_EVENT_MODE_ASYNC
        private const val INJECT_MODE_ASYNC = 0
    }
}
