package com.appcontroller.android.service

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Manages a full-screen TYPE_ACCESSIBILITY_OVERLAY window that hides the
 * Settings → App Info → Force Stop / Clear Cache dialog sequence from the user.
 *
 * PHASE B v2 — FRESH-ADD + ON-PRE-DRAW AWAIT:
 *
 * The previous "pre-add at service startup + alpha=0" approach had multiple
 * failure modes (from the audit):
 * - onServiceConnected doesn't fire after app update → overlay never pre-added
 * - awaitFrameComposited (one Choreographer frame) is a FALSE signal — fires
 *   on every vsync regardless of whether the overlay actually drew
 * - Stale isPreAdded flag if system silently removes the overlay
 * - startForeground called BEFORE show() — wrong order on Android 15
 *
 * New approach (what SD Maid SE does):
 * - Fresh addView on each show() — no pre-add, no stale state
 * - Await the view's first ACTUAL draw via ViewTreeObserver.OnPreDrawListener
 *   (not Choreographer — that fires on every vsync regardless)
 * - 500ms timeout so we don't hang forever if the view fails to attach
 * - hide() removes the view entirely
 *
 * This is simpler, more robust, and works across OEMs.
 */
class KillOverlayManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: View? = null
    private var titleView: TextView? = null
    private var subtitleView: TextView? = null
    private var progressBar: ProgressBar? = null

    @Volatile
    private var isShowing = false

    /**
     * Show the overlay and wait for its first ACTUAL draw before returning.
     * This is the "overlay is composited" signal that startStoppingQueue
     * awaits before calling startActivity.
     *
     * Fresh-add approach: adds the view via WindowManager.addView, then waits
     * for ViewTreeObserver.OnPreDrawListener to fire (proving the ViewRootImpl
     * is attached and the render thread has the frame queued).
     *
     * 500ms timeout so we don't hang forever if the view fails to attach
     * (e.g. OEM rejects TYPE_ACCESSIBILITY_OVERLAY).
     */
    suspend fun show() {
        if (isShowing) return
        isShowing = true

        // Build the view fresh each time — no stale state.
        val container = buildOverlayView()
        overlayView = container
        titleView = container.getChildAt(0) as? TextView
        subtitleView = container.getChildAt(1) as? TextView
        progressBar = container.getChildAt(2) as? ProgressBar

        val params = buildLayoutParams()

        // Add the view. This is synchronous from the client's perspective but
        // the actual window creation happens via IPC to WindowManagerService.
        try {
            windowManager.addView(container, params)
        } catch (e: Throwable) {
            // Some OEMs may reject TYPE_ACCESSIBILITY_OVERLAY. Silently ignore —
            // the kill will still proceed, just without the overlay.
            isShowing = false
            overlayView = null
            titleView = null
            subtitleView = null
            progressBar = null
            return
        }

        // Wait for the view to actually draw — NOT just one Choreographer frame.
        // OnPreDrawListener fires when the view is about to draw, proving the
        // ViewRootImpl is attached and the render thread has the frame queued.
        // 500ms timeout so we don't hang forever if attach fails.
        withTimeoutOrNull(OVERLAY_DRAW_TIMEOUT_MS) {
            awaitFirstDraw(container)
        }
        // Even if the timeout fires, proceed — the overlay may still appear
        // a frame or two later, which is better than not showing it at all.
    }

    fun update(currentPackage: String, processed: Int, total: Int, action: String = "Force Stop", step: String = "Processing") {
        if (!isShowing) return
        titleView?.text = action
        subtitleView?.text = "$step $processed of $total…\n${
            currentPackage.takeLastWhile { it != '.' }.take(20)
        }"
        progressBar?.let { bar ->
            bar.max = total.coerceAtLeast(1)
            bar.progress = processed.coerceIn(0, bar.max)
        }
    }

    fun hide() {
        if (!isShowing) return
        val view = overlayView
        isShowing = false
        overlayView = null
        titleView = null
        subtitleView = null
        progressBar = null
        if (view != null) {
            try {
                windowManager.removeView(view)
            } catch (e: Throwable) {
                // View may have already been removed by the system.
            }
        }
    }

    /**
     * Suspend until the view's first OnPreDrawListener fires — proving the
     * ViewRootImpl is attached and the render thread has the frame queued.
     *
     * This is a much better signal than Choreographer.postFrameCallback (which
     * fires on every vsync regardless of whether any view actually drew).
     */
    private suspend fun awaitFirstDraw(view: View) {
        if (view.isAttachedToWindow && view.isLaidOut) {
            // Already attached + laid out — likely already drew.
            return
        }
        suspendCancellableCoroutine { cont ->
            val listener = object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    view.viewTreeObserver.removeOnPreDrawListener(this)
                    if (cont.isActive) cont.resume(Unit)
                    return true
                }
            }
            view.viewTreeObserver.addOnPreDrawListener(listener)
            cont.invokeOnCancellation {
                view.viewTreeObserver.removeOnPreDrawListener(listener)
            }
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

    companion object {
        private const val OVERLAY_DRAW_TIMEOUT_MS = 500L
    }
}
