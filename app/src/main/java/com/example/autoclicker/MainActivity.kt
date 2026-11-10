package com.example.autoclicker

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.autoclicker.databinding.ActivityMainBinding
import com.example.autoclicker.engine.AutoClickEngine
import com.example.autoclicker.engine.ScriptPlayer
import com.example.autoclicker.executor.ExecutorManager
import com.example.autoclicker.model.ClickPoint
import com.example.autoclicker.model.GestureType
import com.example.autoclicker.service.AutoClickForegroundService
import com.example.autoclicker.ui.ClickPointAdapter
import com.example.autoclicker.ui.OverlayController
import com.example.autoclicker.ui.SettingsActivity
import com.example.autoclicker.util.AccessibilityDetector
import com.example.autoclicker.util.AdvancedProtectionDetector
import com.example.autoclicker.util.ConfigBus
import com.example.autoclicker.util.ConfigStore
import com.example.autoclicker.util.ShizukuDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: ClickPointAdapter
    private val scope = CoroutineScope(Dispatchers.Main)

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener { runOnUiThread { refreshStatus() } }
    private val binderDeadListener = Shizuku.OnBinderDeadListener { runOnUiThread { refreshStatus() } }
    private val permissionResultListener =
        Shizuku.OnRequestPermissionResultListener { _, _ -> runOnUiThread { refreshStatus() } }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    private val configListener: () -> Unit = { refreshPointList() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = ClickPointAdapter(
            onChanged = { point -> updatePoint(point) },
            onDelete = { point -> deletePoint(point) }
        )
        binding.rvPoints.layoutManager = LinearLayoutManager(this)
        binding.rvPoints.adapter = adapter

        binding.btnAccessibility.setOnClickListener { openAccessibilitySettings() }
        binding.btnShizuku.setOnClickListener { requestShizuku() }
        binding.btnOverlay.setOnClickListener { requestOverlayPermission() }
        binding.btnBattery.setOnClickListener { requestBatteryOptimization() }
        binding.btnAddPoint.setOnClickListener { OverlayController.addPoint(this) }
        binding.btnStartStop.setOnClickListener { toggleRun() }
        binding.btnToggleBar.setOnClickListener { OverlayController.toggle(this) }
        binding.btnReplay.setOnClickListener { replayLastScript() }
        binding.btnSettings.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }

        ConfigBus.addListener(configListener)
        // Shizuku 未安装/未运行时这些 API 可能抛异常，全部做保护，避免启动闪退
        runCatching { Shizuku.addBinderReceivedListener(binderReceivedListener) }
        runCatching { Shizuku.addBinderDeadListener(binderDeadListener) }
        runCatching { Shizuku.addRequestPermissionResultListener(permissionResultListener) }

        requestNotificationPermissionIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
        refreshPointList()
        updateRunButton()
    }

    override fun onDestroy() {
        ConfigBus.removeListener(configListener)
        runCatching { Shizuku.removeBinderReceivedListener(binderReceivedListener) }
        runCatching { Shizuku.removeBinderDeadListener(binderDeadListener) }
        runCatching { Shizuku.removeRequestPermissionResultListener(permissionResultListener) }
        super.onDestroy()
    }

    // ---------- 状态展示 ----------
    private fun refreshStatus() {
        val a11yGranted = AccessibilityDetector.isPermissionGranted(this)
        val a11yRunning = AccessibilityDetector.isServiceRunning()
        val shizukuInstalled = ShizukuDetector.isInstalled(this)
        val shizukuRunning = ShizukuDetector.isRunning()
        val shizukuAuthorized = ShizukuDetector.isAuthorized()
        val apm = AdvancedProtectionDetector.query(this)

        val sb = StringBuilder()
        sb.append("无障碍权限: ").append(if (a11yGranted) "已授权" else "未授权")
            .append(if (a11yRunning) "（已连接）" else "").append('\n')
        sb.append("Shizuku: ").append(if (shizukuInstalled) "已安装" else "未安装")
            .append(if (shizukuRunning) " · 运行中" else "")
            .append(if (shizukuAuthorized) " · 已授权" else " · 未授权").append('\n')
        sb.append("高级保护模式(APM): ").append(apm.name).append('\n')
        sb.append("推荐后端: ").append(recommendBackend()).append('\n')
        if (apm == AdvancedProtectionDetector.State.ENABLED) {
            sb.append("⚠ APM 已开启，无障碍可能被禁用；请启用 Shizuku 后端。")
        }
        binding.tvStatus.text = sb.toString()
    }

    private fun recommendBackend(): String {
        if (AccessibilityDetector.isServiceRunning()) return "无障碍"
        if (ShizukuDetector.isAvailable(this)) return "Shizuku"
        return "无（需授权）"
    }

    private fun updateRunButton() {
        binding.btnStartStop.text = if (AutoClickEngine.isRunning) "停止" else "开始"
    }

    private fun refreshPointList() {
        val cfg = ConfigStore.loadConfig(this)
        adapter.submit(cfg.points)
        binding.tvEmpty.visibility = if (cfg.points.isEmpty()) View.VISIBLE else View.GONE
    }

    // ---------- 动作 ----------
    private fun toggleRun() {
        if (AutoClickEngine.isRunning) {
            AutoClickForegroundService.stop(this)
            Toast.makeText(this, "已停止", Toast.LENGTH_SHORT).show()
        } else {
            val cfg = ConfigStore.loadConfig(this)
            if (cfg.enabledPoints.isEmpty()) {
                Toast.makeText(this, "请先添加点击点", Toast.LENGTH_SHORT).show()
                return
            }
            if (!AccessibilityDetector.isServiceRunning() && !ShizukuDetector.isAvailable(this)) {
                Toast.makeText(this, "请先授权无障碍或 Shizuku", Toast.LENGTH_LONG).show()
                return
            }
            AutoClickForegroundService.start(this)
            Toast.makeText(this, "已启动", Toast.LENGTH_SHORT).show()
        }
        updateRunButton()
    }

    private fun updatePoint(point: ClickPoint) {
        val cfg = ConfigStore.loadConfig(this)
        ConfigStore.saveConfig(this, cfg.copy(points = cfg.points.map { if (it.id == point.id) point else it }))
    }

    private fun deletePoint(point: ClickPoint) {
        val cfg = ConfigStore.loadConfig(this)
        ConfigStore.saveConfig(this, cfg.copy(points = cfg.points.filterNot { it.id == point.id }))
    }

    private fun replayLastScript() {
        val script = ConfigStore.loadScripts(this).lastOrNull()
        if (script == null) {
            Toast.makeText(this, "暂无录制脚本", Toast.LENGTH_SHORT).show()
            return
        }
        scope.launch {
            val executor = ExecutorManager.resolveBest()
            if (executor == null || !executor.ensureReady()) {
                Toast.makeText(this@MainActivity, "无可用后端", Toast.LENGTH_SHORT).show()
                return@launch
            }
            runCatching { ScriptPlayer.play(script, executor) }
                .onFailure { Toast.makeText(this@MainActivity, "回放失败: ${it.message}", Toast.LENGTH_SHORT).show() }
        }
    }

    // ---------- 权限引导 ----------
    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun requestShizuku() {
        if (!ShizukuDetector.isInstalled(this)) {
            Toast.makeText(this, "请先安装 Shizuku", Toast.LENGTH_LONG).show()
            return
        }
        val granted = runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
        if (!granted) {
            runCatching { Shizuku.requestPermission(REQUEST_SHIZUKU) }
                .onFailure {
                    Toast.makeText(this, "Shizuku 未运行，请先启动 Shizuku 再授权", Toast.LENGTH_LONG).show()
                }
        } else {
            Toast.makeText(this, "Shizuku 已授权", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        } else {
            Toast.makeText(this, "悬浮窗权限已授予", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestBatteryOptimization() {
        try {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
            )
        } catch (t: Exception) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    companion object {
        private const val REQUEST_SHIZUKU = 1000
    }
}
