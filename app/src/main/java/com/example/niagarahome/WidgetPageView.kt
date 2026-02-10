package com.example.niagarahome

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class WidgetPageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val widgetContainer: LinearLayout
    private val scrollView: ScrollView
    private val emptyText: TextView
    val addButton: TextView

    var onAddWidgetClick: (() -> Unit)? = null
    var onWidgetLongPress: ((Int) -> Unit)? = null

    private val density = resources.displayMetrics.density

    init {
        scrollView = ScrollView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            isVerticalScrollBarEnabled = false
            clipToPadding = false
            val topPad = (80 * density).toInt()
            val bottomPad = (100 * density).toInt()
            val hPad = (16 * density).toInt()
            setPadding(hPad, topPad, hPad, bottomPad)
        }

        widgetContainer = LinearLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.VERTICAL
        }

        scrollView.addView(widgetContainer)
        addView(scrollView)

        emptyText = TextView(context).apply {
            layoutParams = LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
            text = "Touch + to add widgets"
            setTextColor(Color.argb(100, 255, 255, 255))
            textSize = 16f
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        }
        addView(emptyText)

        val btnSize = (56 * density).toInt()
        addButton = TextView(context).apply {
            layoutParams = LayoutParams(btnSize, btnSize, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
                bottomMargin = (32 * density).toInt()
            }
            text = "+"
            setTextColor(Color.WHITE)
            textSize = 28f
            gravity = Gravity.CENTER
            setBackgroundResource(R.drawable.widget_add_button_bg)
            setOnClickListener { onAddWidgetClick?.invoke() }
        }
        addView(addButton)

        updateEmptyState()
    }

    fun addWidgetView(hostView: View, widgetId: Int, heightPx: Int = 0) {
        val wrapHeight = if (heightPx > 0) heightPx else (200 * density).toInt()
        val wrapper = WidgetWrapper(context, widgetId) { id ->
            onWidgetLongPress?.invoke(id)
        }
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lp.bottomMargin = (12 * density).toInt()
        wrapper.layoutParams = lp
        wrapper.addView(hostView, LayoutParams(LayoutParams.MATCH_PARENT, wrapHeight))

        widgetContainer.addView(wrapper)
        updateEmptyState()
    }

    fun removeWidgetView(widgetId: Int) {
        for (i in 0 until widgetContainer.childCount) {
            val child = widgetContainer.getChildAt(i)
            if (child is WidgetWrapper && child.widgetId == widgetId) {
                widgetContainer.removeViewAt(i)
                break
            }
        }
        updateEmptyState()
    }

    fun updateWidgetHeight(widgetId: Int, heightPx: Int) {
        for (i in 0 until widgetContainer.childCount) {
            val child = widgetContainer.getChildAt(i)
            if (child is WidgetWrapper && child.widgetId == widgetId && child.childCount > 0) {
                val hostView = child.getChildAt(0)
                val lp = hostView.layoutParams
                lp.height = heightPx
                hostView.layoutParams = lp
                break
            }
        }
    }

    fun clearWidgets() {
        widgetContainer.removeAllViews()
        updateEmptyState()
    }

    private fun updateEmptyState() {
        emptyText.visibility = if (widgetContainer.childCount == 0) View.VISIBLE else View.GONE
    }

    /**
     * Custom wrapper that intercepts long-press even when the child
     * (AppWidgetHostView) consumes touch events.
     */
    private class WidgetWrapper(
        context: Context,
        val widgetId: Int,
        private val onLongPress: (Int) -> Unit
    ) : FrameLayout(context) {

        private val handler = Handler(Looper.getMainLooper())
        private val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()
        private var downX = 0f
        private var downY = 0f
        private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        private val longPressRunnable = Runnable {
            performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            onLongPress(widgetId)
        }

        override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.x
                    downY = ev.y
                    handler.postDelayed(longPressRunnable, longPressTimeout)
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = ev.x - downX
                    val dy = ev.y - downY
                    if (dx * dx + dy * dy > touchSlop * touchSlop) {
                        handler.removeCallbacks(longPressRunnable)
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(longPressRunnable)
                }
            }
            return false // never steal events — just detect long-press on the side
        }
    }
}
