package com.example.niagarahome

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.LabelFormatter
import com.google.android.material.slider.Slider

class SettingsActivity : AppCompatActivity() {

    private lateinit var container: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scrollView = ScrollView(this).apply {
            setBackgroundColor(Color.BLACK)
            isFillViewport = true
        }
        container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(48))
        }
        scrollView.addView(container)
        setContentView(scrollView)

        // Title
        container.addView(TextView(this).apply {
            text = "Settings"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(0, 0, 0, dp(16))
        })

        // --- Spacing ---
        addHeader("Spacing")
        addIntSlider("Vertical Padding", 4f, 24f, 1f, Settings.itemVerticalPaddingDp, "dp")
            { Settings.itemVerticalPaddingDp = it }
        addIntSlider("Horizontal Padding", 8f, 40f, 1f, Settings.itemHorizontalPaddingDp, "dp")
            { Settings.itemHorizontalPaddingDp = it }
        addIntSlider("Icon-Text Margin", 4f, 32f, 1f, Settings.iconTextMarginDp, "dp")
            { Settings.iconTextMarginDp = it }
        addIntSlider("Strip Width", 20f, 56f, 2f, Settings.alphabetStripWidthDp, "dp")
            { Settings.alphabetStripWidthDp = it }
        addIntSlider("List Top Padding", 0f, 32f, 1f, Settings.listTopPaddingDp, "dp")
            { Settings.listTopPaddingDp = it }
        addIntSlider("List Bottom Padding", 0f, 80f, 2f, Settings.listBottomPaddingDp, "dp")
            { Settings.listBottomPaddingDp = it }

        // --- Animation ---
        addHeader("Animation")
        addFloatSlider("Press Scale", 0.90f, 1.00f, 0.01f, Settings.pressScale, "x", "%.2f")
            { Settings.pressScale = it }
        addIntSlider("Slide Distance", 0f, 80f, 2f, Settings.enterAnimSlideDp, "dp")
            { Settings.enterAnimSlideDp = it }
        addIntSlider("Anim Duration", 0f, 500f, 25f, Settings.enterAnimDurationMs, "ms")
            { Settings.enterAnimDurationMs = it }
        // --- Alphabet Strip ---
        addHeader("Alphabet Strip")
        addFloatSlider("Highlight Scale", 1.0f, 2.0f, 0.1f, Settings.highlightScale, "x", "%.1f")
            { Settings.highlightScale = it }
        addIntSlider("Strip Padding", 0f, 48f, 2f, Settings.stripVerticalPaddingDp, "dp")
            { Settings.stripVerticalPaddingDp = it }
        addIntSlider("Strip Edge Margin", 0f, 24f, 1f, Settings.stripEndMarginDp, "dp")
            { Settings.stripEndMarginDp = it }
        addIntSlider("Strip Top Margin", 0f, 300f, 4f, Settings.stripTopMarginDp, "dp")
            { Settings.stripTopMarginDp = it }
        addIntSlider("Strip Bottom Margin", 0f, 300f, 4f, Settings.stripBottomMarginDp, "dp")
            { Settings.stripBottomMarginDp = it }
        addIntSlider("Fine Scroll Pull", 20f, 160f, 5f, Settings.fineScrollThresholdDp, "dp")
            { Settings.fineScrollThresholdDp = it }
        addIntSlider("Bulge Margin", 0f, 120f, 2f, Settings.bulgeMarginDp, "dp")
            { Settings.bulgeMarginDp = it }
        addIntSlider("Bulge Radius", 2f, 26f, 1f, Settings.bulgeRadius, "")
            { Settings.bulgeRadius = it }
        addIntSlider("Touch Margin", 0f, 120f, 2f, Settings.pillTouchMarginDp, "dp")
            { Settings.pillTouchMarginDp = it }

        // --- Gesture ---
        addHeader("Gesture")
        addIntSlider("Pull-Down Threshold", 40f, 200f, 5f, Settings.pullDownThresholdDp, "dp")
            { Settings.pullDownThresholdDp = it }

        // --- Hidden Apps ---
        addHeader("Hidden Apps")
        container.addView(MaterialButton(this).apply {
            text = "Unhide All Apps"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.argb(40, 255, 255, 255))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = dp(8)
            layoutParams = lp
            setOnClickListener {
                val count = Settings.hiddenApps.size
                if (count > 0) {
                    Settings.clearHiddenApps()
                    android.widget.Toast.makeText(
                        this@SettingsActivity,
                        "$count app(s) unhidden",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                } else {
                    android.widget.Toast.makeText(
                        this@SettingsActivity,
                        "No hidden apps",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        })

        // Reset button
        container.addView(MaterialButton(this).apply {
            text = "Reset to Defaults"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.argb(40, 255, 255, 255))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = dp(32)
            layoutParams = lp
            setOnClickListener {
                Settings.resetAll()
                recreate()
            }
        })
    }

    private fun addHeader(title: String) {
        container.addView(TextView(this).apply {
            text = title
            setTextColor(Color.argb(128, 255, 255, 255))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = dp(20)
            lp.bottomMargin = dp(4)
            layoutParams = lp
        })
    }

    private fun addIntSlider(
        label: String, min: Float, max: Float, step: Float,
        current: Int, unit: String, onChanged: (Int) -> Unit
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(8))
        }

        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val labelView = TextView(this).apply {
            text = label
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val valueView = TextView(this).apply {
            text = "$current $unit"
            setTextColor(Color.argb(153, 255, 255, 255))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.MONOSPACE
        }

        headerRow.addView(labelView)
        headerRow.addView(valueView)
        row.addView(headerRow)

        val slider = Slider(this).apply {
            valueFrom = min
            valueTo = max
            stepSize = step
            value = current.toFloat().coerceIn(min, max)
            trackActiveTintList = ColorStateList.valueOf(Color.WHITE)
            trackInactiveTintList = ColorStateList.valueOf(Color.argb(51, 255, 255, 255))
            thumbTintList = ColorStateList.valueOf(Color.WHITE)
            haloTintList = ColorStateList.valueOf(Color.argb(40, 255, 255, 255))
            labelBehavior = LabelFormatter.LABEL_GONE
            addOnChangeListener { _, v, _ ->
                val intVal = v.toInt()
                valueView.text = "$intVal $unit"
                onChanged(intVal)
            }
        }
        row.addView(slider)
        container.addView(row)
    }

    private fun addFloatSlider(
        label: String, min: Float, max: Float, step: Float,
        current: Float, unit: String, format: String, onChanged: (Float) -> Unit
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(8))
        }

        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val labelView = TextView(this).apply {
            text = label
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val valueView = TextView(this).apply {
            text = "${String.format(format, current)} $unit"
            setTextColor(Color.argb(153, 255, 255, 255))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.MONOSPACE
        }

        headerRow.addView(labelView)
        headerRow.addView(valueView)
        row.addView(headerRow)

        val slider = Slider(this).apply {
            valueFrom = min
            valueTo = max
            stepSize = step
            value = current.coerceIn(min, max)
            trackActiveTintList = ColorStateList.valueOf(Color.WHITE)
            trackInactiveTintList = ColorStateList.valueOf(Color.argb(51, 255, 255, 255))
            thumbTintList = ColorStateList.valueOf(Color.WHITE)
            haloTintList = ColorStateList.valueOf(Color.argb(40, 255, 255, 255))
            labelBehavior = LabelFormatter.LABEL_GONE
            addOnChangeListener { _, v, _ ->
                valueView.text = "${String.format(format, v)} $unit"
                onChanged(v)
            }
        }
        row.addView(slider)
        container.addView(row)
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
