// 顶层构建脚本：仅声明插件版本，不在此处应用。
// Android 17 对应 compileSdk 37；建议使用较新的 AGP（8.9+）以彻底支持。
// 这里使用兼容性良好的 AGP 8.7.3 + Kotlin 2.0.21 组合，并在 gradle.properties 中
// 通过 android.suppressUnsupportedCompileSdk=37 压制“未测试 SDK”提示。
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
}
