# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# --- Glide 混淆规则 ---
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep public class * extends com.bumptech.glide.module.AppGlideModule
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
  **[] $VALUES;
  public *;
}

# --- Coroutines 混淆规则 ---
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.android.AndroidExceptionPreHandler {
    <init>();
}

# --- 自定义 View 保持 (防止在 XML 中引用的 View 被混淆) ---
-keep class com.example.mega_photo.ui.custom.** { *; }

# --- 保持数据类 (防止 JSON 解析等出错，虽然本项目没用到 JSON，但保留是个好习惯) ---
-keep class com.example.mega_photo.data.** { *; }