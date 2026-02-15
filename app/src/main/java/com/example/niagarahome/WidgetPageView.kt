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
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ScrollView
import android.widget.TextView

class WidgetPageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val widgetContainer: FlowLayout
    private val scrollView: ScrollView
    private val emptyText: TextView
    val addButton: TextView
    private lateinit var addButtonWrapper: FrameLayout

    var onAddWidgetClick: (() -> Unit)? = null
    var onWidgetRemove: ((Int) -> Unit)? = null
    var onWidgetResized: ((Int, Int, Int) -> Unit)? = null // widgetId, widthPx, heightPx
    var onWidgetReorder: ((Int, Int) -> Unit)? = null // widgetId, direction (-1=up, +1=down)
    var onWidgetMarginChanged: ((Int, Int) -> Unit)? = null // widgetId, marginPx

    private val density = resources.displayMetrics.density
    private var editingWrapper: WidgetFrame? = null

    // Pinch-to-resize-gap state
    private var pinching = false
    private var pinchGapIndex = -1
    private var pinchStartSpan = 0f
    private var pinchStartMargin = 0

    init {
        scrollView = ScrollView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            isVerticalScrollBarEnabled = false
            clipToPadding = false
            val bottomPad = (16 * density).toInt()
            val hPad = (16 * density).toInt()
            setPadding(hPad, 0, hPad, bottomPad)
        }

        widgetContainer = FlowLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val btnSize = (56 * density).toInt()
        addButton = TextView(context).apply {
            layoutParams = FrameLayout.LayoutParams(btnSize, btnSize, Gravity.CENTER_HORIZONTAL)
            text = "+"
            setTextColor(Color.WHITE)
            textSize = 28f
            gravity = Gravity.CENTER
            setBackgroundResource(R.drawable.widget_add_button_bg)
            setOnClickListener { onAddWidgetClick?.invoke() }
        }
        addButtonWrapper = FrameLayout(context).apply {
            val lp = ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.topMargin = (16 * density).toInt()
            lp.bottomMargin = (32 * density).toInt()
            layoutParams = lp
            addView(addButton)
        }
        widgetContainer.addView(addButtonWrapper)

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

        updateEmptyState()
    }

    fun addWidgetView(hostView: NHWidgetHostView, widgetId: Int, widthPx: Int = 0, heightPx: Int = 0, marginPx: Int = (8 * density).toInt()) {
        val wrapHeight = if (heightPx > 0) heightPx else (200 * density).toInt()
        val wrapWidth = if (widthPx > 0) widthPx else ViewGroup.LayoutParams.MATCH_PARENT

        val frame = WidgetFrame(context, widgetId, hostView, wrapHeight, density, scrollView,
            onRemove = { id -> onWidgetRemove?.invoke(id) },
            onResized = { id, w, h -> onWidgetResized?.invoke(id, w, h) },
            onMove = { id, dir -> moveWidget(id, dir) }
        )
        val lp = ViewGroup.MarginLayoutParams(wrapWidth, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.bottomMargin = marginPx
        frame.layoutParams = lp

        // Long-press on the host view enters edit mode
        hostView.isLongClickable = true
        hostView.setOnLongClickListener {
            android.util.Log.d("NHWidget", "OnLongClickListener fired for widget $widgetId")
            enterEditMode(frame)
            true
        }
        android.util.Log.d("NHWidget", "Set long click listener on hostView for widget $widgetId, longClickable=${hostView.isLongClickable}")

        // Insert before the add button (last child)
        val insertIndex = (widgetContainer.childCount - 1).coerceAtLeast(0)
        widgetContainer.addView(frame, insertIndex)
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

    fun findWidgetHostView(widgetId: Int): NHWidgetHostView? {
        for (i in 0 until widgetContainer.childCount) {
            val child = widgetContainer.getChildAt(i)
            if (child is WidgetFrame && child.widgetId == widgetId) {
                return child.getChildAt(0) as? NHWidgetHostView
            }
        }
        return null
    }

    fun clearWidgets() {
        editingWrapper = null
        widgetContainer.removeAllViews()
        widgetContainer.addView(addButtonWrapper)
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

    private fun moveWidget(widgetId: Int, direction: Int) {
        val index = (0 until widgetContainer.childCount).firstOrNull { i ->
            val child = widgetContainer.getChildAt(i)
            child is WidgetFrame && child.widgetId == widgetId
        } ?: return
        val targetIndex = index + direction
        if (targetIndex < 0 || targetIndex >= widgetContainer.childCount) return
        val frame = widgetContainer.getChildAt(index)
        widgetContainer.removeViewAt(index)
        widgetContainer.addView(frame, targetIndex)
        onWidgetReorder?.invoke(widgetId, direction)
    }

    fun getWidgetOrder(): List<Int> {
        return (0 until widgetContainer.childCount).mapNotNull { i ->
            (widgetContainer.getChildAt(i) as? WidgetFrame)?.widgetId
        }
    }

    fun canScrollUp() = scrollView.canScrollVertically(-1)
    fun canScrollDown() = scrollView.canScrollVertically(1)

    fun isEditingWidget() = editingWrapper != null
    fun isPinching() = pinching

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN && editingWrapper != null) {
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
        if (handlePinchGesture(ev)) return true
        return super.dispatchTouchEvent(ev)
    }

    private fun handlePinchGesture(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (ev.pointerCount == 2) {
                    val y0 = ev.getY(0)
                    val y1 = ev.getY(1)
                    val gapIndex = findGapWidgetIndex(ev)
                    if (gapIndex >= 0) {
                        pinching = true
                        pinchGapIndex = gapIndex
                        pinchStartSpan = Math.abs(y0 - y1)
                        val frame = widgetContainer.getChildAt(gapIndex) as? WidgetFrame
                        pinchStartMargin = (frame?.layoutParams as? ViewGroup.MarginLayoutParams)?.bottomMargin ?: 0
                        scrollView.requestDisallowInterceptTouchEvent(true)
                        return true
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (pinching && ev.pointerCount >= 2) {
                    val span = Math.abs(ev.getY(0) - ev.getY(1))
                    val delta = (span - pinchStartSpan).toInt()
                    val newMargin = pinchStartMargin + delta
                    val frame = widgetContainer.getChildAt(pinchGapIndex) as? WidgetFrame
                    val lp = frame?.layoutParams as? ViewGroup.MarginLayoutParams
                    if (lp != null) {
                        lp.bottomMargin = newMargin
                        frame.layoutParams = lp
                    }
                    return true
                }
            }
            MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (pinching) {
                    pinching = false
                    scrollView.requestDisallowInterceptTouchEvent(false)
                    val frame = widgetContainer.getChildAt(pinchGapIndex) as? WidgetFrame
                    if (frame != null) {
                        val margin = (frame.layoutParams as? ViewGroup.MarginLayoutParams)?.bottomMargin ?: 0
                        onWidgetMarginChanged?.invoke(frame.widgetId, margin)
                    }
                    return true
                }
            }
        }
        return false
    }

    /** Find the widget whose bottom edge falls between the two fingers. */
    private fun findGapWidgetIndex(ev: MotionEvent): Int {
        // rawY is only available for pointer 0; offset pointer 1 from it
        val rawY0 = ev.rawY
        val rawY1 = ev.rawY - ev.getY(0) + ev.getY(1)
        val topY = minOf(rawY0, rawY1)
        val bottomY = maxOf(rawY0, rawY1)
        val loc = IntArray(2)
        for (i in 0 until widgetContainer.childCount) {
            val child = widgetContainer.getChildAt(i) as? WidgetFrame ?: continue
            child.getLocationOnScreen(loc)
            val childBottom = loc[1] + child.height
            if (childBottom in topY.toInt()..bottomY.toInt()) {
                return i
            }
        }
        return -1
    }

    private fun updateEmptyState() {
        // childCount == 1 means only the add button is present
        emptyText.visibility = if (widgetContainer.childCount <= 1) View.VISIBLE else View.GONE
    }

    /**
     * ViewGroup that lays children out left-to-right, wrapping to a new row
     * when a child doesn't fit. MATCH_PARENT-width children get a solo row.
     */
    private inner class FlowLayout(context: Context) : ViewGroup(context) {
        private val hGap = (8 * density).toInt()

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val maxWidth = MeasureSpec.getSize(widthMeasureSpec)
            var rowX = 0
            var totalHeight = 0
            var rowHeight = 0

            for (i in 0 until childCount) {
                val child = getChildAt(i)
                if (child.visibility == View.GONE) continue

                val lp = child.layoutParams as MarginLayoutParams
                measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0)
                val childW = child.measuredWidth + lp.leftMargin + lp.rightMargin
                val childH = child.measuredHeight + lp.topMargin + lp.bottomMargin

                if (lp.width == LayoutParams.MATCH_PARENT) {
                    if (rowX > 0) {
                        totalHeight += rowHeight
                        rowX = 0
                        rowHeight = 0
                    }
                    totalHeight += childH
                } else {
                    if (rowX > 0 && rowX + hGap + childW > maxWidth) {
                        totalHeight += rowHeight
                        rowX = childW
                        rowHeight = childH
                    } else {
                        if (rowX > 0) rowX += hGap
                        rowX += childW
                        rowHeight = maxOf(rowHeight, childH)
                    }
                }
            }
            totalHeight += rowHeight

            setMeasuredDimension(maxWidth, totalHeight)
        }

        override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
            val maxWidth = r - l
            var rowX = 0
            var rowY = 0
            var rowHeight = 0

            for (i in 0 until childCount) {
                val child = getChildAt(i)
                if (child.visibility == View.GONE) continue

                val lp = child.layoutParams as MarginLayoutParams
                val cw = child.measuredWidth
                val ch = child.measuredHeight
                val totalW = cw + lp.leftMargin + lp.rightMargin
                val totalH = ch + lp.topMargin + lp.bottomMargin

                if (lp.width == LayoutParams.MATCH_PARENT) {
                    if (rowX > 0) {
                        rowY += rowHeight
                        rowX = 0
                        rowHeight = 0
                    }
                    child.layout(
                        lp.leftMargin, rowY + lp.topMargin,
                        lp.leftMargin + cw, rowY + lp.topMargin + ch
                    )
                    rowY += totalH
                } else {
                    if (rowX > 0 && rowX + hGap + totalW > maxWidth) {
                        rowY += rowHeight
                        rowX = 0
                        rowHeight = 0
                    }
                    val x = if (rowX > 0) rowX + hGap else 0
                    child.layout(
                        x + lp.leftMargin, rowY + lp.topMargin,
                        x + lp.leftMargin + cw, rowY + lp.topMargin + ch
                    )
                    rowX = x + totalW
                    rowHeight = maxOf(rowHeight, totalH)
                }
            }
        }

        override fun generateLayoutParams(attrs: AttributeSet?) =
            MarginLayoutParams(context, attrs)
        override fun generateLayoutParams(p: LayoutParams) = MarginLayoutParams(p)
        override fun generateDefaultLayoutParams() =
            MarginLayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        override fun checkLayoutParams(p: LayoutParams) = p is MarginLayoutParams
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
        private val onResized: (Int, Int, Int) -> Unit,
        private val onMove: (Int, Int) -> Unit
    ) : FrameLayout(context) {

        private val border: View
        private val bottomHandle: View
        private val leftHandle: View
        private val rightHandle: View
        private val removeButton: TextView
        private val upButton: TextView
        private val downButton: TextView
        private val minHeightPx = (60 * dp).toInt()
        private val minWidthPx = (60 * dp).toInt()
        private val editViews = mutableListOf<View>()

        init {
            // hostView fills the frame; frame's own layout params control the size
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

            val handleThick = (24 * dp).toInt()

            // Bottom handle
            bottomHandle = View(context).apply {
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, handleThick, Gravity.BOTTOM)
                background = GradientDrawable().apply {
                    setColor(Color.argb(120, 255, 255, 255))
                    cornerRadius = 4 * dp
                }
                visibility = View.GONE
            }
            addView(bottomHandle)

            // Left handle
            leftHandle = View(context).apply {
                layoutParams = LayoutParams(handleThick, LayoutParams.MATCH_PARENT, Gravity.START)
                background = GradientDrawable().apply {
                    setColor(Color.argb(120, 255, 255, 255))
                    cornerRadius = 4 * dp
                }
                visibility = View.GONE
            }
            addView(leftHandle)

            // Right handle
            rightHandle = View(context).apply {
                layoutParams = LayoutParams(handleThick, LayoutParams.MATCH_PARENT, Gravity.END)
                background = GradientDrawable().apply {
                    setColor(Color.argb(120, 255, 255, 255))
                    cornerRadius = 4 * dp
                }
                visibility = View.GONE
            }
            addView(rightHandle)

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

            // Move-up button at top-left
            upButton = TextView(context).apply {
                layoutParams = LayoutParams(btnSize, btnSize, Gravity.TOP or Gravity.START).apply {
                    topMargin = (4 * dp).toInt()
                    marginStart = (4 * dp).toInt()
                }
                text = "\u25B2"
                setTextColor(Color.WHITE)
                textSize = 16f
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.argb(200, 80, 80, 80))
                }
                visibility = View.GONE
                setOnClickListener { onMove(widgetId, -1) }
            }
            addView(upButton)

            // Move-down button below up button
            downButton = TextView(context).apply {
                layoutParams = LayoutParams(btnSize, btnSize, Gravity.TOP or Gravity.START).apply {
                    topMargin = (4 * dp + btnSize).toInt()
                    marginStart = (4 * dp).toInt()
                }
                text = "\u25BC"
                setTextColor(Color.WHITE)
                textSize = 16f
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.argb(200, 80, 80, 80))
                }
                visibility = View.GONE
                setOnClickListener { onMove(widgetId, +1) }
            }
            addView(downButton)

            editViews.addAll(listOf(border, bottomHandle, leftHandle, rightHandle, removeButton, upButton, downButton))

            // Bottom handle drag (vertical resize)
            setupVerticalDrag(bottomHandle)

            // Left/right handle drag (horizontal resize)
            setupHorizontalDrag(leftHandle, fromLeft = true)
            setupHorizontalDrag(rightHandle, fromLeft = false)
        }

        private fun setupVerticalDrag(handle: View) {
            var dragStartY = 0f
            var dragStartHeight = 0
            handle.setOnTouchListener { _, event ->
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
                        onResized(widgetId, this@WidgetFrame.width, hostView.layoutParams.height)
                        true
                    }
                    else -> false
                }
            }
        }

        private fun setupHorizontalDrag(handle: View, fromLeft: Boolean) {
            var dragStartX = 0f
            var dragStartWidth = 0
            handle.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        dragStartX = event.rawX
                        dragStartWidth = this@WidgetFrame.width
                        parentScrollView.requestDisallowInterceptTouchEvent(true)
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val delta = (event.rawX - dragStartX).toInt()
                        val change = if (fromLeft) -delta else delta
                        val newWidth = (dragStartWidth + change).coerceAtLeast(minWidthPx)
                        val lp = this@WidgetFrame.layoutParams as ViewGroup.MarginLayoutParams
                        lp.width = newWidth
                        this@WidgetFrame.layoutParams = lp
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        parentScrollView.requestDisallowInterceptTouchEvent(false)
                        onResized(widgetId, this@WidgetFrame.width, hostView.layoutParams.height)
                        true
                    }
                    else -> false
                }
            }
        }

        fun setEditMode(editing: Boolean) {
            hostView.ignoreTouches = editing
            val vis = if (editing) View.VISIBLE else View.GONE
            for (v in editViews) v.visibility = vis
        }
    }
}
