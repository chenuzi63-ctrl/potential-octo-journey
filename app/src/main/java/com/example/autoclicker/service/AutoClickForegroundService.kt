package com.example.autoclicker.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.autoclicker.MainActivity
import com.example.autoclicker.R
import com.example.autoclicker.engine.AutoClickEngine
import com.example.autoclicker.util.ConfigStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 前台服务：承载点击循环、息屏停止监听与通知栏保活。
 * AccessibilityService 本身是系统绑定服务，但为确保定时点击/循环脚本在后台持续运行，
 * 额外增加此前台服务，在通知栏显示“自动点击运行中”，降低被系统回收的概率。
 */
class AutoClickForegroundService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                // 多层紧急停止之二：息屏自动停止
                AutoClickEngine.stop()
                updateNotification("已停止（息屏）")
                stopSelf()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        registerScreenOffReceiver()
    }

    private fun registerScreenOffReceiver() {
        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        if (Build.VERSION.SDK_INT >= 33) {
            ContextCompat.registerReceiver(this, screenOffReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(screenOffReceiver, filter)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            AutoClickEngine.stop()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        startForegroundCompat()
        scope.launch { startEngine() }
        return START_NOT_STICKY
    }

    private suspend fun startEngine() {
        val config = ConfigStore.loadConfig(this)
        val executor = AutoClickEngine.start(config, config.recordWhileRunning)
        if (executor == null) {
            updateNotification("无可用执行后端，请授权无障碍或 Shizuku")
            AutoClickEngine.stop()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        } else {
            updateNotification("运行中 · ${executor.displayName}")
        }
    }

    private fun startForegroundCompat() {
        val notification = buildNotification("准备启动…")
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "自动点击", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification {
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, AutoClickForegroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("自动点击运行中")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .addAction(0, "停止", stopIntent)
            .build()
    }

    override fun onDestroy() {
        AutoClickEngine.stop()
        runCatching { unregisterReceiver(screenOffReceiver) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "autoclicker_fg"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.example.autoclicker.action.START"
        const val ACTION_STOP = "com.example.autoclicker.action.STOP"

        fun start(context: Context) {
            val i = Intent(context, AutoClickForegroundService::class.java).setAction(ACTION_START)
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(i)
            else context.startService(i)
        }

        fun stop(context: Context) {
            AutoClickEngine.stop()
            runCatching { context.stopService(Intent(context, AutoClickForegroundService::class.java)) }
        }
    }
}
