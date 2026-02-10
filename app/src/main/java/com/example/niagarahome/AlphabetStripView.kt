package com.example.niagarahome

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import kotlin.math.exp

class AlphabetStripView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var onLetterSelected: ((Char) -> Unit)? = null
    var onLetterPreview: ((Char?, Float) -> Unit)? = null
    var onFineScroll: ((Float) -> Unit)? = null  // 0.0 = top, 1.0 = bottom

    // Dynamic properties (set from Settings via applySettings)
    var highlightScale: Float = Settings.DEF_HIGHLIGHT_SCALE
        set(value) { field = value; invalidate() }

    private var letters: List<Char> = emptyList()
    private var letterPositions: Map<Char, Int> = emptyMap()
    private var selectedIndex = -1
    private var isDragging = false
    private var isFineMode = false
    var fineThresholdPx = Settings.DEF_FINE_SCROLL_THRESHOLD * resources.displayMetrics.density
    var totalItemCount: Int = 0

    // Deformation state
    private var touchY = 0f
    private var pullAmount = 0f  // 0..1, how far pulled
    private var pullDistancePx = 0f  // actual pixel distance pulled
    var bulgeMarginPx = Settings.DEF_BULGE_MARGIN * resources.displayMetrics.density
    var bulgeRadius = Settings.DEF_BULGE_RADIUS.toFloat()
    var touchMarginLeftPx = Settings.DEF_PILL_TOUCH_MARGIN * resources.displayMetrics.density

    private val density = resources.displayMetrics.density

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

        val margin = touchMarginLeftPx
        val centerX = margin + (width - margin) / 2f
        val totalHeight = height - paddingTop - paddingBottom
        val letterHeight = totalHeight.toFloat() / letters.size

        for ((i, letter) in letters.withIndex()) {
            val y = paddingTop + letterHeight * i + letterHeight / 2f

            // Calculate horizontal offset for bulge deformation
            var offsetX = 0f
            var scale = 1f
            if (isDragging && pullAmount > 0f) {
                val distFromTouch = kotlin.math.abs(y - touchY) / letterHeight
                // Gaussian-ish falloff: peaks at touch point, fades over bulgeRadius letters
                val falloff = exp(-(distFromTouch * distFromTouch) / bulgeRadius)
                // Offset = pull distance + margin, so letters appear past the finger
                offsetX = -(pullDistancePx + bulgeMarginPx) * falloff
                // Scale up letters near the touch point
                scale = 1f + (highlightScale - 1f) * pullAmount * falloff
            }

            val drawX = centerX + offsetX
            if (i == selectedIndex && isDragging) {
                highlightPaint.textSize = baseFontSize * scale.coerceAtLeast(highlightScale)
                val metrics = highlightPaint.fontMetrics
                canvas.drawText(letter.toString(), drawX, y - (metrics.ascent + metrics.descent) / 2f, highlightPaint)
            } else {
                paint.textSize = baseFontSize * scale
                val metrics = paint.fontMetrics
                canvas.drawText(letter.toString(), drawX, y - (metrics.ascent + metrics.descent) / 2f, paint)
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
                pullAmount = 0f
                pullDistancePx = 0f
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

        // How far left the finger has pulled from the visual strip
        // x is relative to view; the visual strip starts at touchMarginLeftPx
        val adjustedX = x - touchMarginLeftPx
        val pullDistance = (-adjustedX).coerceAtLeast(0f)
        val fineFraction = (pullDistance / fineThresholdPx).coerceIn(0f, 1f)

        // Update deformation state
        touchY = y
        pullAmount = fineFraction
        pullDistancePx = pullDistance

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
            }
        } else {
            // Snap mode: snap to letter
            val index = ((y - paddingTop) / letterHeight).toInt().coerceIn(0, letters.size - 1)
            if (index != selectedIndex) {
                selectedIndex = index
                val letter = letters[index]
                onLetterSelected?.invoke(letter)
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                val letterY = paddingTop + letterHeight * index + letterHeight / 2f
                onLetterPreview?.invoke(letter, letterY)
            }
        }
        invalidate()
    }
}
