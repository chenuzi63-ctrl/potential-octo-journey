package com.example.autoclicker.util

import java.util.concurrent.CopyOnWriteArrayList

/** 轻量级配置变更总线：ConfigStore 写盘后通知订阅者刷新 UI。 */
object ConfigBus {
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    fun addListener(l: () -> Unit) {
        listeners.add(l)
    }

    fun removeListener(l: () -> Unit) {
        listeners.remove(l)
    }

    fun notifyChanged() {
        listeners.forEach { runCatching { it() } }
    }
}
