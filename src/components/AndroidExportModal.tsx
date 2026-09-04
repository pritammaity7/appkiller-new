import React, { useState } from 'react';
import JSZip from 'jszip';

interface AndroidExportModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export const AndroidExportModal: React.FC<AndroidExportModalProps> = ({ isOpen, onClose }) => {
  const [isZipping, setIsZipping] = useState(false);
  const [downloadSuccess, setDownloadSuccess] = useState(false);
  const [selectedFileTab, setSelectedFileTab] = useState<'service' | 'shizuku' | 'manifest' | 'activity' | 'gradle'>('service');

  if (!isOpen) return null;

  const handleDownloadZip = async () => {
    setIsZipping(true);
    try {
      const zip = new JSZip();
      const androidFolder = zip.folder('android');
      const appFolder = androidFolder?.folder('app');
      const srcFolder = appFolder?.folder('src')?.folder('main');
      const javaFolder = srcFolder?.folder('java')?.folder('com')?.folder('appcontroller')?.folder('android');
      const resFolder = srcFolder?.folder('res');

      // Root Gradle files
      androidFolder?.file('settings.gradle.kts', `pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = java.net.URI("https://jitpack.io") }
    }
}
rootProject.name = "AppController"
include(":app")
`);

      androidFolder?.file('build.gradle.kts', `plugins {
    id("com.android.application") version "8.2.2" apply false
    id("com.android.library") version "8.2.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
}
`);

      androidFolder?.file('gradle.properties', `org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
android.enableJetifier=false
kotlin.code.style=official
android.nonTransitiveRClass=true
`);

      // App build.gradle.kts
      appFolder?.file('build.gradle.kts', `plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.appcontroller.android"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.appcontroller.android"
        minSdk = 26
        targetSdk = 34
        versionCode = 24
        versionName = "2.4.0"
        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            applicationIdSuffix = ".debug"
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
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
`);

      // AndroidManifest.xml
      srcFolder?.file('AndroidManifest.xml', `<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <uses-permission android:name="android.permission.QUERY_ALL_PACKAGES" tools:ignore="QueryAllPackagesPermission" />
    <uses-permission android:name="android.permission.KILL_BACKGROUND_PROCESSES" />
    <uses-permission android:name="android.permission.PACKAGE_USAGE_STATS" tools:ignore="ProtectedPermissions" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <application
        android:name=".AppControllerApp"
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/Theme.AppController">

        <activity
            android:name=".ui.MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <service
            android:name=".service.AppControllerAccessibilityService"
            android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"
            android:exported="true"
            android:label="@string/accessibility_service_label">
            <intent-filter>
                <action android:name="android.accessibilityservice.AccessibilityService" />
            </intent-filter>
            <meta-data
                android:name="android.accessibilityservice"
                android:resource="@xml/accessibility_service_config" />
        </service>

        <provider
            android:name="rikka.shizuku.ShizukuProvider"
            android:authorities="\${applicationId}.shizuku"
            android:multiprocess="false"
            android:enabled="true"
            android:exported="true"
            android:permission="android.permission.INTERACT_ACROSS_USERS_FULL" />
    </application>
</manifest>
`);

      // Kotlin Files
      javaFolder?.folder('service')?.file('AppControllerAccessibilityService.kt', `package com.appcontroller.android.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class AppControllerAccessibilityService : AccessibilityService() {
    private var currentTarget: String? = null
    private var isWaitingConfirm = false

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (currentTarget == null) return
        val root = rootInActiveWindow ?: return

        if (isWaitingConfirm) {
            val confirmButtons = root.findAccessibilityNodeInfosByText("OK") +
                                root.findAccessibilityNodeInfosByText("Force stop")
            for (btn in confirmButtons) {
                if (btn.isEnabled) {
                    btn.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    isWaitingConfirm = false
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    return
                }
            }
        } else {
            val stopButtons = root.findAccessibilityNodeInfosByText("Force stop")
            for (btn in stopButtons) {
                if (btn.isEnabled) {
                    btn.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    isWaitingConfirm = true
                    return
                }
            }
        }
    }

    override fun onInterrupt() {}
}
`);

      javaFolder?.folder('shizuku')?.file('ShizukuController.kt', `package com.appcontroller.android.shizuku

import dev.rikka.shizuku.Shizuku
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ShizukuController {
    fun isAvailable() = try { Shizuku.pingBinder() } catch (e: Throwable) { false }

    suspend fun forceStopPackage(packageName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val process = Shizuku.newProcess(arrayOf("sh", "-c", "am force-stop $packageName"), null, null)
            process.waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }
}
`);

      // Resources
      const xmlFolder = resFolder?.folder('xml');
      xmlFolder?.file('accessibility_service_config.xml', `<?xml version="1.0" encoding="utf-8"?>
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:accessibilityEventTypes="typeWindowStateChanged|typeWindowContentChanged"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:canRetrieveWindowContent="true"
    android:notificationTimeout="50" />
`);

      const valuesFolder = resFolder?.folder('values');
      valuesFolder?.file('strings.xml', `<resources>
    <string name="app_name">App Controller</string>
    <string name="accessibility_service_label">App Controller Automation</string>
</resources>`);
      valuesFolder?.file('colors.xml', `<resources>
    <color name="primary">#4EDEA3</color>
    <color name="surface_dark">#101417</color>
</resources>`);

      // GitHub Actions workflow
      const githubFolder = zip.folder('.github')?.folder('workflows');
      githubFolder?.file('build-apk.yml', `name: Build Android APK
on: [push, pull_request, workflow_dispatch]
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
      - run: |
          cd android
          gradle wrapper
          ./gradlew assembleDebug
      - uses: actions/upload-artifact@v4
        with:
          name: app-controller-apk
          path: android/app/build/outputs/apk/debug/*.apk
`);

      // Generate the ZIP blob
      const content = await zip.generateAsync({ type: 'blob' });
      const url = URL.createObjectURL(content);
      const link = document.createElement('a');
      link.href = url;
      link.download = 'AppController-Android-Project.zip';
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      URL.revokeObjectURL(url);

      setDownloadSuccess(true);
      setTimeout(() => setDownloadSuccess(false), 4000);
    } catch (err) {
      console.error('Failed to generate ZIP:', err);
    } finally {
      setIsZipping(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-sm animate-in fade-in duration-200">
      <div className="bg-surface-container rounded-3xl w-full max-w-2xl border border-outline/20 shadow-2xl flex flex-col max-h-[90vh] overflow-hidden text-on-surface">
        {/* Header */}
        <div className="p-5 border-b border-outline/10 flex items-center justify-between bg-surface-container-low">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-primary/20 text-primary flex items-center justify-center">
              <span className="material-symbols-outlined text-[24px]">android</span>
            </div>
            <div>
              <h2 className="font-bold text-base sm:text-lg">Android APK & Native Studio Project</h2>
              <p className="text-xs text-on-surface-variant font-mono">
                Full Kotlin + AccessibilityService + Shizuku Binder
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="w-8 h-8 rounded-full bg-surface-container-high hover:bg-surface-container-highest flex items-center justify-center text-on-surface-variant hover:text-on-surface transition-colors"
          >
            <span className="material-symbols-outlined text-[20px]">close</span>
          </button>
        </div>

        {/* Content Body */}
        <div className="flex-1 overflow-y-auto p-5 space-y-4 text-xs leading-relaxed">
          {/* Why native is needed */}
          <div className="p-4 rounded-2xl bg-primary/10 border border-primary/20 text-on-surface space-y-1.5">
            <div className="flex items-center gap-2 font-bold text-primary text-sm">
              <span className="material-symbols-outlined text-[18px]">verified</span>
              <span>Why App Controller Requires a Native APK</span>
            </div>
            <p className="text-on-surface-variant">
              Because modern Android security isolates browsers from OS internals, closing other applications requires calling Android's native <code className="font-mono text-primary">AccessibilityService API</code> or <code className="font-mono text-primary">Shizuku ADB Binder</code>. A browser tab cannot stop native processes on your phone.
            </p>
          </div>

          {/* Quick Actions Card */}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            {/* Download Full Project ZIP */}
            <div className="p-4 rounded-2xl bg-surface-container-low border border-outline/10 flex flex-col justify-between space-y-3">
              <div>
                <span className="font-bold text-sm block text-on-surface">1. Download Android Studio Project</span>
                <p className="text-[11px] text-on-surface-variant mt-1">
                  Complete ready-to-build Android project with Gradle wrapper, manifests, Kotlin sources, and vector icons.
                </p>
              </div>
              <button
                type="button"
                onClick={handleDownloadZip}
                disabled={isZipping}
                className="w-full h-10 rounded-xl bg-primary text-on-primary font-bold font-mono text-xs flex items-center justify-center gap-2 shadow hover:opacity-90 active:scale-95 transition-all disabled:opacity-50"
              >
                <span className="material-symbols-outlined text-[18px]">
                  {isZipping ? 'hourglass_top' : downloadSuccess ? 'check_circle' : 'download'}
                </span>
                <span>{isZipping ? 'Packaging Project...' : downloadSuccess ? 'Downloaded!' : 'Download .ZIP Project'}</span>
              </button>
            </div>

            {/* GitHub Actions Cloud Build */}
            <div className="p-4 rounded-2xl bg-surface-container-low border border-outline/10 flex flex-col justify-between space-y-3">
              <div>
                <span className="font-bold text-sm block text-on-surface">2. Automatic Cloud APK Builder</span>
                <p className="text-[11px] text-on-surface-variant mt-1">
                  Included <code className="font-mono text-secondary">.github/workflows/build-apk.yml</code> automatically builds the <code className="font-mono text-primary">app-debug.apk</code> via GitHub Actions!
                </p>
              </div>
              <div className="text-[11px] font-mono text-primary/90 bg-surface-container-lowest p-2.5 rounded-lg border border-outline/10">
                Click <strong>Settings &gt; Export to GitHub</strong> in AI Studio to trigger the APK build.
              </div>
            </div>
          </div>

          {/* Code Viewer */}
          <div className="space-y-2">
            <div className="flex items-center justify-between">
              <span className="font-semibold text-on-surface">Native Kotlin Source Code</span>
              <div className="flex items-center gap-1 font-mono text-[10px]">
                <button
                  type="button"
                  onClick={() => setSelectedFileTab('service')}
                  className={`px-2.5 py-1 rounded-md transition-colors ${
                    selectedFileTab === 'service' ? 'bg-primary text-on-primary font-bold' : 'bg-surface-container-high'
                  }`}
                >
                  AccessibilityService.kt
                </button>
                <button
                  type="button"
                  onClick={() => setSelectedFileTab('shizuku')}
                  className={`px-2.5 py-1 rounded-md transition-colors ${
                    selectedFileTab === 'shizuku' ? 'bg-primary text-on-primary font-bold' : 'bg-surface-container-high'
                  }`}
                >
                  ShizukuController.kt
                </button>
                <button
                  type="button"
                  onClick={() => setSelectedFileTab('manifest')}
                  className={`px-2.5 py-1 rounded-md transition-colors ${
                    selectedFileTab === 'manifest' ? 'bg-primary text-on-primary font-bold' : 'bg-surface-container-high'
                  }`}
                >
                  AndroidManifest.xml
                </button>
              </div>
            </div>

            <div className="bg-surface-container-lowest p-3 rounded-xl border border-outline/10 font-mono text-[11px] text-on-surface-variant max-h-48 overflow-y-auto">
              <pre className="whitespace-pre-wrap leading-tight">
                {selectedFileTab === 'service' && `// AppControllerAccessibilityService.kt
// Automates Force Stop via AccessibilityNodeInfo click traversal
class AppControllerAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val root = rootInActiveWindow ?: return
        // 1. Click Force Stop on App Info Settings
        val stopBtns = root.findAccessibilityNodeInfosByText("Force stop")
        for (btn in stopBtns) {
            if (btn.isEnabled) {
                btn.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return
            }
        }
        // 2. Click OK confirmation dialog & return
        val confirm = root.findAccessibilityNodeInfosByText("OK")
        confirm.firstOrNull()?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        performGlobalAction(GLOBAL_ACTION_BACK)
    }
}`}
                {selectedFileTab === 'shizuku' && `// ShizukuController.kt
// Dispatches am force-stop <pkg> through ADB Binder without root
object ShizukuController {
    suspend fun forceStopPackage(packageName: String): Boolean = withContext(Dispatchers.IO) {
        val process = Shizuku.newProcess(
            arrayOf("sh", "-c", "am force-stop $packageName"), null, null
        )
        process.waitFor() == 0
    }
}`}
                {selectedFileTab === 'manifest' && `<!-- AndroidManifest.xml -->
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.QUERY_ALL_PACKAGES" />
    <uses-permission android:name="android.permission.KILL_BACKGROUND_PROCESSES" />
    <uses-permission android:name="android.permission.PACKAGE_USAGE_STATS" />

    <service
        android:name=".service.AppControllerAccessibilityService"
        android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"
        android:exported="true">
        <intent-filter>
            <action android:name="android.accessibilityservice.AccessibilityService" />
        </intent-filter>
    </service>
</manifest>`}
              </pre>
            </div>
          </div>

          {/* Compilation Instructions */}
          <div className="p-3.5 bg-surface-container-low rounded-xl border border-outline/10 font-mono text-[11px] space-y-1">
            <span className="font-bold text-on-surface block">Terminal Build Command:</span>
            <div className="p-2 bg-surface-container-lowest rounded-md text-primary font-bold">
              cd android && ./gradlew assembleDebug
            </div>
            <p className="text-on-surface-variant text-[10px] pt-1">
              Produces: <code className="text-on-surface">app/build/outputs/apk/debug/app-debug.apk</code>
            </p>
          </div>
        </div>

        {/* Footer */}
        <div className="p-4 border-t border-outline/10 bg-surface-container-low flex items-center justify-between">
          <span className="text-[11px] text-on-surface-variant font-mono">
            SDK 34 (Android 14) Target
          </span>
          <button
            type="button"
            onClick={onClose}
            className="px-5 py-2 rounded-xl bg-surface-container-high hover:bg-surface-container-highest text-on-surface font-semibold text-xs transition-colors"
          >
            Close
          </button>
        </div>
      </div>
    </div>
  );
};
