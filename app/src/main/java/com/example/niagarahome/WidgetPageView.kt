package com.example.niagarahome

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
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
    var onWidgetLongPress: ((Int) -> Unit)? = null // passes widget ID

    private val density = resources.displayMetrics.density

    init {
        // ScrollView fills the whole page
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

        // Empty state text
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

        // Floating "+" button at bottom center
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
        val wrapHeight = if (heightPx > 0) heightPx
            else (200 * density).toInt() // default fallback
        val wrapper = FrameLayout(context).apply {
            tag = widgetId
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = (12 * density).toInt()
            layoutParams = lp

            addView(hostView, LayoutParams(LayoutParams.MATCH_PARENT, wrapHeight))

            setOnLongClickListener {
                onWidgetLongPress?.invoke(widgetId)
                true
            }
        }
        widgetContainer.addView(wrapper)
        updateEmptyState()
    }

    fun removeWidgetView(widgetId: Int) {
        for (i in 0 until widgetContainer.childCount) {
            val child = widgetContainer.getChildAt(i)
            if (child.tag == widgetId) {
                widgetContainer.removeViewAt(i)
                break
            }
        }
        updateEmptyState()
    }

    fun clearWidgets() {
        widgetContainer.removeAllViews()
        updateEmptyState()
    }

    private fun updateEmptyState() {
        emptyText.visibility = if (widgetContainer.childCount == 0) View.VISIBLE else View.GONE
    }
}
