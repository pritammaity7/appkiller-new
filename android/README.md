# App Controller - Native Android Application (APK)

Fully functioning native Android application (APK) built with **Kotlin**, **Jetpack Compose**, **Android AccessibilityService API**, and **Shizuku Binder**.

## Core System Architecture
1. **Automated App Stopping Service** (`com.appcontroller.android.service.AppControllerAccessibilityService`):
   - Automates navigation to Android System App Info (`android.settings.APPLICATION_DETAILS_SETTINGS`) and triggers the "Force stop" action and dialog confirmation without requiring root.
2. **Shizuku Privileged Binder** (`com.appcontroller.android.shizuku.ShizukuController`):
   - Directly dispatches `am force-stop <package>` commands via ADB shell binder when Shizuku is authorized for instant, seamless batch stopping without UI jumps.
3. **Genuine Kernel Vitals** (`com.appcontroller.android.data.MemoryReader`):
   - Directly parses Linux `/proc/meminfo` (MemTotal, MemAvailable, Active(file), Swap/ZRAM).
4. **Safety Baseline Guardrails**:
   - Hardcoded protection against stopping the default launcher, active keyboard/IME, Android SystemUI, or App Controller itself.

---

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
1. In the Google AI Studio menu, click **Export to GitHub**.
2. Push or open the repository on GitHub.
3. The GitHub Action **Build Android APK** will run automatically.
4. Under the **Actions** tab, click the latest workflow run and download `app-controller-debug-apk` directly to your phone!
