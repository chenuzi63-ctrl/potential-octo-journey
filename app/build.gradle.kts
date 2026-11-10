plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.autoclicker"
    // Android 17（API 37）在 SDK 仓库中拆成了 37.0 / 37.1 / 37.2 三个“次版本”，
    // 当前 AGP 8.7.3 不支持次版本的 compileSdk 写法（需要 AGP 8.13+），
    // 因此先用稳定的 Android 16（API 36）保证能编译出 APK。
    // 应用在 Android 17 设备上仍可正常运行，APM 检测在运行时通过反射完成。
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.autoclicker"
        // 最低支持 Android 7.0（API 24）
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
        // AGP 8.0 起 AIDL 默认关闭，必须显式开启，否则 IGestureService 不会生成
        aidl = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Shizuku：无 Root 的 ADB 级权限通道，作为 APM / 无障碍不可用时的备选后端。
    // 包名为 rikka.shizuku（v11 起由 moe.shizuku.api 改名）。
    implementation("dev.rikka.shizuku:api:12.1.0")
    implementation("dev.rikka.shizuku:provider:12.1.0")
}
