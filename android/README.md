# Force Stop — Native Android Application (APK)

A native Android app built with **Kotlin**, **Jetpack Compose**, and the **Android AccessibilityService API** for stopping background apps without root.

## Core architecture

1. **Automated App Stopping Service** (`com.appcontroller.android.service.AppControllerAccessibilityService`):
   - Automates navigation to Android System App Info (`android.settings.APPLICATION_DETAILS_SETTINGS`) and triggers the "Force stop" action and dialog confirmation without requiring root.
2. **Genuine Kernel Vitals** (`com.appcontroller.android.data.MemoryReader`):
   - Directly parses Linux `/proc/meminfo` (MemTotal, MemAvailable, Active(file), Swap/ZRAM).
3. **Real Running-App Detection** (`com.appcontroller.android.data.ProcessRepository`):
   - Uses `UsageStatsManager` (requires Usage Access permission) to enumerate only apps that have actually been used in the last 5 minutes — so the home screen does not list every installed app.
4. **Safety Baseline Guardrails**:
   - Hardcoded protection against stopping the default launcher, active keyboard/IME, Android SystemUI, or Force Stop itself.
   - User-configurable exceptions list (`ExceptionsRepository`) — any app on this list is silently skipped during stop and shown as "Protected".
5. **Mandatory Permissions Gate**:
   - The app blocks on launch until both Accessibility Service and Usage Access are granted. Re-checks on every `onResume` — if the user later revokes a permission, the gate re-appears.

## How to Build the APK

### Method 1: Android Studio (Recommended)
1. Open **Android Studio** (Hedgehog 2023.1.1 or newer).
2. Select **Open** and choose the `android` folder.
3. Wait for Gradle Sync to complete.
4. Click **Build** > **Build Bundle(s) / APK(s)** > **Build APK(s)**.
5. The generated file will be located at:
   `app/build/outputs/apk/debug/app-debug.apk`

### Method 2: Command Line (Gradle)
```bash
cd android
./gradlew assembleDebug
```
Output:
`app/build/outputs/apk/debug/app-debug.apk`

### Method 3: GitHub Actions (Automated Cloud APK Build)
This repository includes `.github/workflows/build-apk.yml`.
1. Push to `main`.
2. The GitHub Action **Build Android APK** runs automatically.
3. Under the **Actions** tab, click the latest workflow run and download `app-controller-debug-apk` directly to your phone.
