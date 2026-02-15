package com.example.niagarahome

import android.appwidget.AppWidgetHostView
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup

/**
 * Custom AppWidgetHostView that intercepts long-press before the widget's
 * own children can consume it. Based on AOSP Launcher3's approach.
 */
class NHWidgetHostView(context: Context) : AppWidgetHostView(context) {

    private var hasLongPressed = false
    var ignoreTouches = false

    private val handler = Handler(Looper.getMainLooper())
    private var longPressRunnable: Runnable? = null
    private var downX = 0f
    private var downY = 0f
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    // Fire at 75% of system timeout (like Launcher3) to beat the widget's own handler
    private val longPressTimeout = (ViewConfiguration.getLongPressTimeout() * 0.75f).toLong()

    override fun getDescendantFocusability(): Int = FOCUS_BLOCK_DESCENDANTS

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (ignoreTouches) return true

        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                Log.d("NHWidget", "HostView onIntercept DOWN")
                hasLongPressed = false
                downX = ev.x
                downY = ev.y
                longPressRunnable = Runnable {
                    hasLongPressed = true
                    val result = performLongClick()
                    Log.d("NHWidget", "HostView long-press fired! performLongClick=$result longClickable=$isLongClickable")
                }
                handler.postDelayed(longPressRunnable!!, longPressTimeout)
                // If the widget has scrollable content, prevent the parent
                // ScrollView from intercepting vertical drags
                if (hasScrollableDescendant(this)) {
                    parent.requestDisallowInterceptTouchEvent(true)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = ev.x - downX
                val dy = ev.y - downY
                if (dx * dx + dy * dy > touchSlop * touchSlop) {
                    cancelPendingLongPress()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                cancelPendingLongPress()
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return hasLongPressed
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (ignoreTouches) return true
        return true
    }

    override fun cancelLongPress() {
        super.cancelLongPress()
        cancelPendingLongPress()
    }

    private fun cancelPendingLongPress() {
        longPressRunnable?.let { handler.removeCallbacks(it) }
        longPressRunnable = null
    }

    private fun hasScrollableDescendant(view: View): Boolean {
        if (view.canScrollVertically(1) || view.canScrollVertically(-1)) return true
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                if (hasScrollableDescendant(view.getChildAt(i))) return true
            }
        }
        return false
    }
}
