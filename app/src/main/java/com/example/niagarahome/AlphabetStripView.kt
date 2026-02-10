package com.example.niagarahome

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View

class AlphabetStripView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var onLetterSelected: ((Int) -> Unit)? = null
    var onLetterPreview: ((Char?, Float) -> Unit)? = null
    var onFineScroll: ((Float) -> Unit)? = null  // 0.0 = top, 1.0 = bottom

    // Dynamic properties (set from Settings via applySettings)
    var pillOpacityPercent: Int = Settings.DEF_PILL_OPACITY
        set(value) { field = value; invalidate() }
    var pillCornerRadiusDp: Int = Settings.DEF_PILL_CORNER_RADIUS
        set(value) { field = value; invalidate() }
    var highlightScale: Float = Settings.DEF_HIGHLIGHT_SCALE
        set(value) { field = value; invalidate() }

    private var letters: List<Char> = emptyList()
    private var letterPositions: Map<Char, Int> = emptyMap()
    private var selectedIndex = -1
    private var isDragging = false
    private var isFineMode = false
    var fineThresholdPx = Settings.DEF_FINE_SCROLL_THRESHOLD * resources.displayMetrics.density
    var totalItemCount: Int = 0

    private val density = resources.displayMetrics.density

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
    }

    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
    }

    private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pillRect = RectF()

    private var baseFontSize = 12f

    init {
        baseFontSize = resources.getDimension(R.dimen.app_text_size) * 0.7f
    }

    fun setLetterPositions(positions: Map<Char, Int>) {
        letterPositions = positions
        val sorted = mutableListOf<Char>()
        for (c in 'A'..'Z') {
            if (c in positions) sorted.add(c)
        }
        if ('#' in positions) sorted.add('#')
        letters = sorted
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (letters.isEmpty()) return

        // Draw background pill
        val alpha = (pillOpacityPercent * 255 / 100).coerceIn(0, 255)
        pillPaint.color = Color.argb(alpha, 255, 255, 255)
        val pillPadding = 4f * density
        val cornerRadius = pillCornerRadiusDp * density
        pillRect.set(
            pillPadding,
            paddingTop.toFloat(),
            width - pillPadding,
            height - paddingBottom.toFloat()
        )
        canvas.drawRoundRect(pillRect, cornerRadius, cornerRadius, pillPaint)

        val centerX = width / 2f
        val totalHeight = height - paddingTop - paddingBottom
        val letterHeight = totalHeight.toFloat() / letters.size

        for ((i, letter) in letters.withIndex()) {
            val y = paddingTop + letterHeight * i + letterHeight / 2f

            if (i == selectedIndex && isDragging) {
                highlightPaint.textSize = baseFontSize * highlightScale
                val metrics = highlightPaint.fontMetrics
                canvas.drawText(letter.toString(), centerX, y - (metrics.ascent + metrics.descent) / 2f, highlightPaint)
            } else {
                paint.textSize = baseFontSize
                val metrics = paint.fontMetrics
                canvas.drawText(letter.toString(), centerX, y - (metrics.ascent + metrics.descent) / 2f, paint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isDragging = true
                isFineMode = false
                parent?.requestDisallowInterceptTouchEvent(true)
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                updateSelection(event.x, event.y)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                updateSelection(event.x, event.y)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                isFineMode = false
                selectedIndex = -1
                parent?.requestDisallowInterceptTouchEvent(false)
                onLetterPreview?.invoke(null, 0f)
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun updateSelection(x: Float, y: Float) {
        if (letters.isEmpty()) return
        val totalHeight = height - paddingTop - paddingBottom
        val letterHeight = totalHeight.toFloat() / letters.size

        // How far left the finger has pulled from the strip (x < 0 = pulling left)
        val pullDistance = (-x).coerceAtLeast(0f)
        val fineFraction = (pullDistance / fineThresholdPx).coerceIn(0f, 1f)

        val wasFineMode = isFineMode
        isFineMode = fineFraction > 0.3f

        if (isFineMode && totalItemCount > 0) {
            // Fine scroll mode: vertical position maps to entire list
            val fraction = ((y - paddingTop) / totalHeight).coerceIn(0f, 1f)
            onFineScroll?.invoke(fraction)

            // Update preview letter based on y position
            val index = ((y - paddingTop) / letterHeight).toInt().coerceIn(0, letters.size - 1)
            if (index != selectedIndex || !wasFineMode) {
                selectedIndex = index
                val letterY = paddingTop + letterHeight * index + letterHeight / 2f
                onLetterPreview?.invoke(letters[index], letterY)
                if (index != selectedIndex || !wasFineMode) {
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                }
                invalidate()
            }
        } else {
            // Snap mode: snap to letter
            val index = ((y - paddingTop) / letterHeight).toInt().coerceIn(0, letters.size - 1)
            if (index != selectedIndex) {
                selectedIndex = index
                val letter = letters[index]
                val position = letterPositions[letter]
                if (position != null) {
                    onLetterSelected?.invoke(position)
                }
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                val letterY = paddingTop + letterHeight * index + letterHeight / 2f
                onLetterPreview?.invoke(letter, letterY)
                invalidate()
            }
        }
    }
}
