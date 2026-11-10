# 自动点击（AutoClicker）

一款 **无 Root** 的自动点击工具，最低支持 **Android 7.0（API 24）**，目标与编译 SDK 对齐 **Android 17（API 37）**。使用 **Kotlin + 协程**，采用 **双执行后端** 架构，专门针对 Android 17 高级保护模式（APM）下无障碍权限被撤销的场景做了降级设计。

> 本项目仅用于个人自动化（游戏辅助、重复操作等），请勿用于任何违规用途。所有配置仅保存在本机，不采集屏幕内容、不读取窗口节点文本、不上传任何数据。

---

## 一、双后端架构

执行点击抽象为接口 `GestureExecutor`，提供两个实现：

| 后端 | 实现类 | 原理 | 适用场景 |
|------|--------|------|----------|
| A（默认） | `AccessibilityGestureExecutor` | `AccessibilityService.dispatchGesture` | 无障碍已开启、APM 未开启 |
| B（备选） | `ShizukuGestureExecutor` | Shizuku 用户服务（shell 权限）内反射调用 `InputManager.injectInputEvent` | APM 开启 / 无障碍被撤销 |

启动时由 `ExecutorManager` 检测可用性：**优先无障碍；无障碍不可用且 Shizuku 可用时，无缝切换至 Shizuku 后端**；两者都不可用时引导用户授权，而非崩溃。

### Android 17 适配要点

- `res/xml/accessibility_service_config.xml` 声明了 `android:isAccessibilityTool="true"`，以争取 APM 豁免；但代码中**明确承认自动化工具可能不被 APM 视为无障碍工具**，因此不把全部逻辑押在无障碍上。
- `AdvancedProtectionDetector`（反射探测 `AdvancedProtectionManager`）在启动时检测 APM 状态，据此提示用户切换 Shizuku。
- APM 开启导致无障碍被系统打断时，`onInterrupt()` 会立即紧急停止。

---

## 二、三种使用场景

### 场景 1：无障碍可用（APM 关闭）——默认，零额外依赖
1. 打开应用 → 点「无障碍设置」→ 在系统设置中开启「自动点击（无障碍手势）」。
2. 点「悬浮窗权限」授予悬浮窗（无障碍已连接时用 `TYPE_ACCESSIBILITY_OVERLAY`，可免此权限）。
3. 点「添加点击点」，在悬浮条点「定位」把圆点拖到目标位置。
4. 点「开始」即可运行；点悬浮条「■」/音量减键/息屏均可停止。

### 场景 2：APM 开启 + Shizuku 可用 —— 自动降级
1. 安装 [Shizuku](https://shizuku.rikka.app/) 并完成 ADB / 无线调试授权（Shizuku 自身是官方支持的无 Root ADB 通道）。
2. 打开本应用 → 点「Shizuku 授权」→ 授权后状态栏显示“已授权”。
3. 由于 APM 下无障碍被撤销，引擎会自动切换到 **Shizuku 注入** 后端（通知栏显示“运行中 · Shizuku 注入”）。
4. 其余操作与场景 1 相同。

> 注意：Shizuku 后端**不是零配置**，需要用户安装 Shizuku 并完成一次 ADB/无线调试授权。

### 场景 3：APM 开启 + 无 Shizuku —— 降级提示（不崩溃）
- 应用会检测到「无障碍不可用 + Shizuku 不可用」，在启动/开始点击时**明确提示**：
  - 引导关闭 APM，或
  - 引导安装并授权 Shizuku。
- 不会直接崩溃；悬浮条与界面仍可正常操作。

---

## 三、核心功能

1. **多点独立点击**：多个点击点，每个点独立设置间隔、时长、次数（0 = 无限）。
2. **拖动定位**：点击点以屏幕圆点形式显示，直接拖到目标位置，所见即所得。
3. **悬浮窗控制条**：开始/停止、添加、减少、定位、设置、关闭；支持横排/竖排/极简、大小与透明度可调、边缘吸附、位置持久化。
4. **紧急停止（多层）**：音量减键（物理通道，最可靠）、息屏自动停止、悬浮条停止按钮。
5. **录制与回放**：录制点击/长按/滑动轨迹生成脚本，可回放（设置中开启“运行时录制”）。
6. **参数持久化**：所有配置重启后保留（SharedPreferences + JSON）。

---

## 四、工程结构

```
app/src/main/
├── AndroidManifest.xml
├── java/com/example/autoclicker/
│   ├── App.kt
│   ├── MainActivity.kt
│   ├── service/
│   │   ├── AutoClickAccessibilityService.kt
│   │   └── AutoClickForegroundService.kt
│   ├── executor/
│   │   ├── GestureExecutor.kt
│   │   ├── AccessibilityGestureExecutor.kt
│   │   ├── ShizukuGestureExecutor.kt
│   │   └── ExecutorManager.kt
│   ├── shizuku/
│   │   └── ShizukuGestureService.kt      (Shizuku 用户服务)
│   ├── engine/
│   │   ├── AutoClickEngine.kt
│   │   ├── GestureRecorder.kt
│   │   └── ScriptPlayer.kt
│   ├── model/
│   │   ├── ClickPoint.kt
│   │   ├── ClickConfig.kt
│   │   ├── GestureType.kt
│   │   └── Script.kt
│   ├── util/
│   │   ├── AdvancedProtectionDetector.kt (APM 检测)
│   │   ├── ShizukuDetector.kt            (Shizuku 可用性)
│   │   ├── AccessibilityDetector.kt      (无障碍可用性)
│   │   ├── ConfigStore.kt
│   │   ├── ConfigBus.kt
│   │   └── OverlayManager.kt
│   └── ui/
│       ├── FloatingControlBar.kt
│       ├── PointOverlayView.kt
│       ├── OverlayController.kt
│       ├── SettingsActivity.kt
│       └── ClickPointAdapter.kt
├── aidl/com/example/autoclicker/shizuku/IGestureService.aidl
└── res/
    ├── xml/accessibility_service_config.xml
    ├── layout/...
    ├── values/...
    └── drawable/...
```

---

## 五、构建说明

- 需要 **JDK 17**。
- 需要 Android SDK 中安装 **Android 17（API 37）** 平台。
- 使用 **AGP 8.7.3 + Kotlin 2.0.21**。若提示 `compileSdk 37 未受支持`，已通过 `gradle.properties` 的 `android.suppressUnsupportedCompileSdk=37` 压制；建议升级到 **AGP 8.9+** 以彻底消除。
- 依赖：AndroidX + Kotlin 协程 + Shizuku（`dev.rikka.shizuku:api:12.1.0` / `provider:12.1.0`）。

```bash
# 用 Android Studio 直接打开本目录，或命令行：
./gradlew assembleDebug   # 需要先生成 gradle wrapper
```

> 若将 Shizuku 升级到 **v13+**，需把代码中的包名 `moe.shizuku.api` 改为 `rikka.shizuku`（其余 API 基本一致）。

### 说明与限制

- 点击间隔最小 10ms；真机建议不要低于 5ms 的 UI 事件间隔，避免被系统忽略。
- 无障碍后端使用屏幕绝对坐标 + 所见即所得拖点定位，规避状态栏/导航栏坐标偏移问题。
- 音量减键紧急停止依赖无障碍的 `flagRequestFilterKeyEvents`（无障碍后端运行时有效）。
- 悬浮窗优先使用 `TYPE_ACCESSIBILITY_OVERLAY`；无障碍未连接时回退到 `TYPE_APPLICATION_OVERLAY`（需 `SYSTEM_ALERT_WINDOW`）。
