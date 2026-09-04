package com.appcontroller.android.service

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.util.TypedValue
import android.view.Choreographer
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Manages a full-screen TYPE_ACCESSIBILITY_OVERLAY window that hides the
 * Settings → App Info → Force Stop dialog sequence from the user.
 *
 * PHASE B — OVERLAY TIMING FIX:
 * Previously, show() posted addView to the Main Looper via Handler.post{},
 * then startStoppingQueue() synchronously called startActivity(AppInfo).
 * The intent left the process BEFORE the overlay view was even attached,
 * let alone composited. On a warm Settings process (mid-batch), App Info's
 * first frame composites before the overlay's — producing the visible flash.
 *
 * Fix (ranked #1 by the audit):
 * 1. PRE-ADD the overlay at service startup (in onServiceConnected via
 *    preAddOverlay()). The Surface is always composited, hidden via alpha=0.
 *    This eliminates the addView latency entirely.
 * 2. show() flips alpha to 1 (instant — Surface already exists) and waits
 *    for ONE Choreographer frame to ensure the new alpha is composited
 *    before returning. This is the "overlay composited" signal.
 * 3. startStoppingQueue() awaits show() (suspend) before calling
 *    startActivity — guarantees the overlay is visible first.
 * 4. Add LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES for notch/punch-hole
 *    coverage (was missing — status bar could peek through).
 *
 * Other design points (from earlier phases):
 * - TYPE_ACCESSIBILITY_OVERLAY floats above EVERYTHING — including the
 *   system AlertDialog that asks "Force stop?".
 * - FLAG_NOT_TOUCHABLE + FLAG_NOT_FOCUSABLE so performAction(ACTION_CLICK)
 *   on the Force Stop button beneath still works.
 * - importantForAccessibility = false on all overlay views so it doesn't
 *   generate accessibility events that would re-enter our own service.
 * - Plain Android Views (not Compose) — Compose requires a ViewRootImpl
 *   with saved state registry, which TYPE_ACCESSIBILITY_OVERLAY windows
 *   don't play well with.
 */
class KillOverlayManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: View? = null
    private var titleView: TextView? = null
    private var subtitleView: TextView? = null
    private var progressBar: ProgressBar? = null

    @Volatile
    private var isPreAdded = false

    @Volatile
    private var isShowing = false

    /**
     * Pre-add the overlay to the WindowManager with alpha=0 (invisible).
     * Call this from onServiceConnected() — the Surface stays composited
     * for the lifetime of the service, so show() only needs to flip alpha.
     *
     * Must be called on the Main thread.
     */
    fun preAddOverlay() {
        if (isPreAdded) return
        // Build the view tree once.
        val container = buildOverlayView()
        overlayView = container
        titleView = container.getChildAt(0) as? TextView
        subtitleView = container.getChildAt(1) as? TextView
        progressBar = container.getChildAt(2) as? ProgressBar

        val params = buildLayoutParams().apply {
            // Start invisible — alpha=0 keeps the Surface composited but
            // transparent. Toggling alpha later is a single-frame relayout,
            // much faster than addView from cold.
            alpha = 0f
        }

        try {
            windowManager.addView(container, params)
            isPreAdded = true
        } catch (e: Throwable) {
            // Some OEMs may reject TYPE_ACCESSIBILITY_OVERLAY if the service
            // was just enabled and isn't fully bound yet. We'll retry on
            // show() — the kill will still proceed, just with addView latency.
            overlayView = null
            titleView = null
            subtitleView = null
            progressBar = null
            isPreAdded = false
        }
    }

    /**
     * Show the overlay and wait for ONE Choreographer frame to ensure the
     * new alpha is composited before returning. This is the "overlay is
     * visible" signal that startStoppingQueue awaits before calling
     * startActivity.
     *
     * Suspends on the Main thread (must be called from a Main coroutine).
     */
    suspend fun show() {
        if (isShowing) return
        // If pre-add failed (or wasn't called), addView now as a fallback.
        if (!isPreAdded) {
            preAddOverlay()
        }
        val view = overlayView ?: return
        isShowing = true

        // Flip alpha to 1 — instant because the Surface is already composited.
        view.alpha = 1f

        // Wait for ONE Choreographer frame to ensure the new alpha is
        // submitted to SurfaceFlinger. This is the heuristic "overlay
        // composited" signal on pre-API-34 (the audit's recommended
        // approach #3).
        awaitFrameComposited()
    }

    fun update(currentPackage: String, processed: Int, total: Int) {
        if (!isShowing) return
        titleView?.text = "Force Stop"
        subtitleView?.text = "Processing $processed of $total…\n${
            currentPackage.takeLastWhile { it != '.' }.take(20)
        }"
        progressBar?.let { bar ->
            bar.max = total.coerceAtLeast(1)
            bar.progress = processed.coerceAtleastIn(0, bar.max)
        }
    }

    fun hide() {
        if (!isShowing) return
        val view = overlayView ?: return
        // Flip alpha back to 0 — keep the Surface composited for next batch.
        view.alpha = 0f
        isShowing = false
    }

    /**
     * Fully remove the overlay (called from onUnbind/onDestroy).
     * After this, preAddOverlay() must be called again before show().
     */
    fun destroy() {
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
        isPreAdded = false
        isShowing = false
    }

    /**
     * Suspend until the next Choreographer frame is submitted. This is the
     * closest heuristic to "overlay is composited" on pre-API-34 devices.
     */
    private suspend fun awaitFrameComposited() {
        suspendCancellableCoroutine { cont ->
            val choreographer = Choreographer.getInstance()
            choreographer.postFrameCallback(object : Choreographer.FrameCallback {
                override fun doFrame(frameTimeNanos: Long) {
                    if (cont.isActive) cont.resume(Unit)
                }
            })
        }
    }

    private fun buildOverlayView(): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(0xFF101417.toInt())
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            isFocusable = false
            isFocusableInTouchMode = false
            // Start invisible — preAddOverlay sets alpha=0.
            alpha = 0f
        }.also { container ->
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
        }
    }

    private fun buildLayoutParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            format = PixelFormat.OPAQUE
            flags = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
            gravity = Gravity.CENTER
            // Cover the notch / punch-hole / status bar area (Phase B fix).
            // Without this, the top ~24-80dp can be uncovered on some devices.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            context.resources.displayMetrics
        ).toInt()
    }
}

// Helper extension to avoid Kotlin's coerceIn name clash on ProgressBar.
private fun Int.coerceAtleastIn(min: Int, max: Int): Int =
    if (this < min) min else if (this > max) max else this
