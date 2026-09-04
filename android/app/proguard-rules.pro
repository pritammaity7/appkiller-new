# Keep Accessibility Service
-keep public class com.appcontroller.android.service.AppControllerAccessibilityService { *; }

# Keep Shizuku Binder classes (Maven groupId is dev.rikka.shizuku
# but the actual Java package inside the AAR is rikka.shizuku)
-keep class rikka.shizuku.** { *; }
-keep interface rikka.shizuku.** { *; }
