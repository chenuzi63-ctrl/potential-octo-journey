package com.example.autoclicker.util

import android.content.Context
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

/** Shizuku 可用性检测工具类 */
object ShizukuDetector {

    private const val PKG_LEGACY = "moe.shizuku.privileged.api"
    private const val PKG_MANAGER = "moe.shizuku.manager"

    /** Shizuku 应用是否已安装 */
    fun isInstalled(context: Context): Boolean {
        val pm = context.packageManager
        val legacy = runCatching { pm.getPackageInfo(PKG_LEGACY, 0); true }.getOrDefault(false)
        val manager = runCatching { pm.getPackageInfo(PKG_MANAGER, 0); true }.getOrDefault(false)
        return legacy || manager
    }

    /** Shizuku 服务进程是否存活 */
    fun isRunning(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    /** 本应用是否已获得 Shizuku 授权 */
    fun isAuthorized(): Boolean = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED

    /** 是否可直接使用 Shizuku 后端（运行中且已授权） */
    fun isAvailable(context: Context): Boolean = isRunning() && isAuthorized()
}
