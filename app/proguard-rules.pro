# 默认不混淆（isMinifyEnabled=false）。如需开启混淆，请保留以下反射与 AIDL 相关规则。

# Shizuku 用户服务通过反射调用 InputManager.injectInputEvent，保留 android.hardware.input
-keep class android.hardware.input.InputManager { *; }
-keep class android.view.InputEvent { *; }
-keep class android.view.MotionEvent { *; }

# AIDL 生成的 Stub 需保留
-keep class com.example.autoclicker.shizuku.IGestureService { *; }
-keep class com.example.autoclicker.shizuku.IGestureService$Stub { *; }

# AdvancedProtectionManager 反射探测
-keep class android.app.admin.AdvancedProtectionManager { *; }
