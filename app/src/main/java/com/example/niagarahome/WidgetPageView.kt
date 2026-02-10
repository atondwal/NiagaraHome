package com.example.niagarahome

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
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
    var onWidgetRemove: ((Int) -> Unit)? = null
    var onWidgetResized: ((Int, Int) -> Unit)? = null // widgetId, newHeightPx

    private val density = resources.displayMetrics.density
    private var editingWrapper: WidgetFrame? = null

    init {
        scrollView = ScrollView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            isVerticalScrollBarEnabled = false
            clipToPadding = false
            val bottomPad = (100 * density).toInt()
            val hPad = (16 * density).toInt()
            setPadding(hPad, 0, hPad, bottomPad)
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

    fun addWidgetView(hostView: NHWidgetHostView, widgetId: Int, heightPx: Int = 0) {
        val wrapHeight = if (heightPx > 0) heightPx else (200 * density).toInt()

        val frame = WidgetFrame(context, widgetId, hostView, wrapHeight, density, scrollView,
            onRemove = { id -> onWidgetRemove?.invoke(id) },
            onResized = { id, h -> onWidgetResized?.invoke(id, h) }
        )
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lp.bottomMargin = (8 * density).toInt()
        frame.layoutParams = lp

        // Long-press on the host view enters edit mode
        hostView.isLongClickable = true
        hostView.setOnLongClickListener {
            android.util.Log.d("NHWidget", "OnLongClickListener fired for widget $widgetId")
            enterEditMode(frame)
            true
        }
        android.util.Log.d("NHWidget", "Set long click listener on hostView for widget $widgetId, longClickable=${hostView.isLongClickable}")

        widgetContainer.addView(frame)
        updateEmptyState()
    }

    fun removeWidgetView(widgetId: Int) {
        if (editingWrapper?.widgetId == widgetId) editingWrapper = null
        for (i in 0 until widgetContainer.childCount) {
            val child = widgetContainer.getChildAt(i)
            if (child is WidgetFrame && child.widgetId == widgetId) {
                widgetContainer.removeViewAt(i)
                break
            }
        }
        updateEmptyState()
    }

    fun clearWidgets() {
        editingWrapper = null
        widgetContainer.removeAllViews()
        updateEmptyState()
    }

    private fun enterEditMode(frame: WidgetFrame) {
        editingWrapper?.setEditMode(false)
        editingWrapper = frame
        frame.setEditMode(true)
    }

    fun exitEditMode() {
        editingWrapper?.setEditMode(false)
        editingWrapper = null
    }

    fun isEditingWidget() = editingWrapper != null

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN && editingWrapper != null) {
            // Check if the tap is inside the editing widget frame
            val frame = editingWrapper!!
            val loc = IntArray(2)
            frame.getLocationOnScreen(loc)
            val x = ev.rawX
            val y = ev.rawY
            val inside = x >= loc[0] && x <= loc[0] + frame.width
                    && y >= loc[1] && y <= loc[1] + frame.height
            if (!inside) {
                exitEditMode()
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun updateEmptyState() {
        emptyText.visibility = if (widgetContainer.childCount == 0) View.VISIBLE else View.GONE
    }

    /**
     * Frame that wraps an NHWidgetHostView and shows resize/remove controls.
     */
    @SuppressLint("ClickableViewAccessibility")
    private class WidgetFrame(
        context: Context,
        val widgetId: Int,
        private val hostView: NHWidgetHostView,
        heightPx: Int,
        private val dp: Float,
        private val parentScrollView: ScrollView,
        private val onRemove: (Int) -> Unit,
        private val onResized: (Int, Int) -> Unit
    ) : FrameLayout(context) {

        private val border: View
        private val resizeHandle: View
        private val removeButton: TextView
        private val minHeightPx = (60 * dp).toInt()

        init {
            // Host view fills the frame
            addView(hostView, LayoutParams(LayoutParams.MATCH_PARENT, heightPx))

            // Border overlay
            border = View(context).apply {
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
                background = GradientDrawable().apply {
                    setStroke((2 * dp).toInt(), Color.argb(180, 255, 255, 255))
                    cornerRadius = 8 * dp
                    setColor(Color.TRANSPARENT)
                }
                isClickable = false
                isFocusable = false
                visibility = View.GONE
            }
            addView(border)

            // Resize handle at bottom
            val handleHeight = (24 * dp).toInt()
            resizeHandle = View(context).apply {
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, handleHeight, Gravity.BOTTOM)
                background = GradientDrawable().apply {
                    setColor(Color.argb(120, 255, 255, 255))
                    cornerRadius = 4 * dp
                }
                visibility = View.GONE
            }
            addView(resizeHandle)

            // Remove button at top-right
            val btnSize = (36 * dp).toInt()
            removeButton = TextView(context).apply {
                layoutParams = LayoutParams(btnSize, btnSize, Gravity.TOP or Gravity.END).apply {
                    topMargin = (4 * dp).toInt()
                    marginEnd = (4 * dp).toInt()
                }
                text = "\u00D7"
                setTextColor(Color.WHITE)
                textSize = 20f
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.argb(200, 200, 60, 60))
                }
                visibility = View.GONE
                setOnClickListener { onRemove(widgetId) }
            }
            addView(removeButton)

            // Resize handle drag
            var dragStartY = 0f
            var dragStartHeight = 0
            resizeHandle.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        dragStartY = event.rawY
                        dragStartHeight = hostView.layoutParams.height
                        parentScrollView.requestDisallowInterceptTouchEvent(true)
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val delta = (event.rawY - dragStartY).toInt()
                        val newHeight = (dragStartHeight + delta).coerceAtLeast(minHeightPx)
                        val lp = hostView.layoutParams
                        lp.height = newHeight
                        hostView.layoutParams = lp
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        parentScrollView.requestDisallowInterceptTouchEvent(false)
                        onResized(widgetId, hostView.layoutParams.height)
                        true
                    }
                    else -> false
                }
            }
        }

        fun setEditMode(editing: Boolean) {
            hostView.ignoreTouches = editing
            val vis = if (editing) View.VISIBLE else View.GONE
            border.visibility = vis
            resizeHandle.visibility = vis
            removeButton.visibility = vis
        }
    }
}
