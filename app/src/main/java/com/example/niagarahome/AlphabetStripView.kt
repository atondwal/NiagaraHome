package com.example.niagarahome

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class AlphabetStripView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var onLetterSelected: ((Int) -> Unit)? = null

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

    private var baseFontSize = 12f

    init {
        baseFontSize = resources.getDimension(R.dimen.app_text_size) * 0.7f
    }

    fun setLetterPositions(positions: Map<Char, Int>) {
        letterPositions = positions
        // Build sorted letter list: # at end, A-Z in order
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
            invalidate()
        }
    }
}
