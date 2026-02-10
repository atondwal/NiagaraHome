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

    private var letters: List<Char> = emptyList()
    private var letterPositions: Map<Char, Int> = emptyMap()
    private var selectedIndex = -1
    private var isDragging = false

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
    }

    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
    }

    private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(26, 255, 255, 255) // 10% white
    }

    private val pillRect = RectF()
    private val pillCornerRadius = 12f * resources.displayMetrics.density

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
        val pillPadding = 4f * resources.displayMetrics.density
        pillRect.set(
            pillPadding,
            paddingTop.toFloat(),
            width - pillPadding,
            height - paddingBottom.toFloat()
        )
        canvas.drawRoundRect(pillRect, pillCornerRadius, pillCornerRadius, pillPaint)

        val centerX = width / 2f
        val totalHeight = height - paddingTop - paddingBottom
        val letterHeight = totalHeight.toFloat() / letters.size

        for ((i, letter) in letters.withIndex()) {
            val y = paddingTop + letterHeight * i + letterHeight / 2f

            if (i == selectedIndex && isDragging) {
                highlightPaint.textSize = baseFontSize * 1.4f
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
                parent?.requestDisallowInterceptTouchEvent(true)
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                updateSelection(event.y)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                updateSelection(event.y)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                selectedIndex = -1
                parent?.requestDisallowInterceptTouchEvent(false)
                onLetterPreview?.invoke(null, 0f)
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun updateSelection(y: Float) {
        if (letters.isEmpty()) return
        val totalHeight = height - paddingTop - paddingBottom
        val letterHeight = totalHeight.toFloat() / letters.size
        val index = ((y - paddingTop) / letterHeight).toInt().coerceIn(0, letters.size - 1)

        if (index != selectedIndex) {
            selectedIndex = index
            val letter = letters[index]
            val position = letterPositions[letter]
            if (position != null) {
                onLetterSelected?.invoke(position)
            }
            // Haptic tick on each letter change
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            // Letter preview callback with Y position for popup placement
            val letterY = paddingTop + letterHeight * index + letterHeight / 2f
            onLetterPreview?.invoke(letter, letterY)
            invalidate()
        }
    }
}
