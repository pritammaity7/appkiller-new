package com.appcontroller.android.service

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.appcontroller.android.R

/**
 * Manages a full-screen TYPE_ACCESSIBILITY_OVERLAY window that hides the
 * Settings → App Info → Force Stop dialog sequence from the user.
 *
 * Key design points (from the audit):
 *
 * - TYPE_ACCESSIBILITY_OVERLAY floats above EVERYTHING — including the system
 *   AlertDialog that asks "Force stop?". This is the only way to hide the
 *   dialog without SYSTEM_ALERT_WINDOW (which Play restricts).
 *
 * - The overlay MUST be FLAG_NOT_TOUCHABLE + FLAG_NOT_FOCUSABLE, otherwise
 *   performAction(ACTION_CLICK) on the Force Stop button beneath FAILS
 *   (Stack Overflow #44972366 — Android blocks touches that pass through
 *   an overlay from a *different* app, but programmatic accessibility clicks
 *   bypass the touch pipeline only if the overlay doesn't consume focus).
 *
 * - importantForAccessibility = false on the overlay root so it doesn't
 *   generate accessibility events that would re-enter our own service.
 *
 * - The overlay is NOT a Compose view — Compose requires a ViewRootImpl
 *   with a saved state registry, and TYPE_ACCESSIBILITY_OVERLAY windows
 *   don't play well with Compose's lifecycle. Classic Android Views are
 *   simpler and more reliable here.
 *
 * - Thread-safe: show() / update() / hide() can be called from any thread;
 *   WindowManager.addView must be called from the Main thread, so we post.
 */
class KillOverlayManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: View? = null
    private var titleView: TextView? = null
    private var subtitleView: TextView? = null
    private var progressBar: ProgressBar? = null

    @Volatile
    private var isShowing = false

    fun show() {
        synchronized(this) {
            if (isShowing) return
            isShowing = true
        }
        // WindowManager.addView must be called on the Main thread.
        val mainHandler = android.os.Handler(context.mainLooper)
        mainHandler.post { showInternal() }
    }

    fun update(currentPackage: String, processed: Int, total: Int) {
        val mainHandler = android.os.Handler(context.mainLooper)
        mainHandler.post {
            if (!isShowing) return@post
            titleView?.text = "Force Stop"
            subtitleView?.text = "Processing $processed of $total…\n${
                currentPackage.takeLastWhile { it != '.' }.take(20)
            }"
            progressBar?.let { bar ->
                bar.max = total.coerceAtLeast(1)
                bar.progress = processed.coerceIn(0, bar.max)
            }
        }
    }

    fun hide() {
        synchronized(this) {
            if (!isShowing) return
            isShowing = false
        }
        val mainHandler = android.os.Handler(context.mainLooper)
        mainHandler.post { hideInternal() }
    }

    private fun showInternal() {
        if (overlayView != null) return // already shown

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(0xFF101417.toInt())
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            isFocusable = false
            isFocusableInTouchMode = false
        }

        val pad = dp(24)
        container.setPadding(pad, pad, pad, pad)

        val title = TextView(context).apply {
            text = "Force Stop"
            setTextColor(0xFFE0E3E7.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            gravity = Gravity.CENTER
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        container.addView(title)

        val subtitle = TextView(context).apply {
            text = "Preparing…"
            setTextColor(0xFFBBABAF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(16))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        container.addView(subtitle)

        val progress = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1
            progress = 0
            isIndeterminate = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val progressParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(8)
        )
        container.addView(progress, progressParams)

        val hint = TextView(context).apply {
            text = "Don't close the app — this takes a few seconds."
            setTextColor(0xFF86948A.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, 0)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        container.addView(hint)

        overlayView = container
        titleView = title
        subtitleView = subtitle
        progressBar = progress

        val params = WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            format = PixelFormat.OPAQUE
            flags = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
            gravity = Gravity.CENTER
        }

        try {
            windowManager.addView(container, params)
        } catch (e: Throwable) {
            // Some OEMs may reject TYPE_ACCESSIBILITY_OVERLAY if the service
            // was just enabled and isn't fully bound yet. Silently ignore —
            // the kill will still proceed, just without the overlay.
            overlayView = null
            isShowing = false
        }
    }

    private fun hideInternal() {
        val view = overlayView ?: return
        try {
            windowManager.removeView(view)
        } catch (e: Throwable) {
            // View may have already been removed by the system.
        }
        overlayView = null
        titleView = null
        subtitleView = null
        progressBar = null
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            context.resources.displayMetrics
        ).toInt()
    }
}
