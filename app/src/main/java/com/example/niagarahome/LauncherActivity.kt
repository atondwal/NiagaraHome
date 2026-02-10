package com.example.niagarahome

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch

class LauncherActivity : AppCompatActivity() {

    private lateinit var repository: AppRepository
    private lateinit var adapter: AppListAdapter
    private lateinit var layoutManager: LinearLayoutManager
    private lateinit var recyclerView: RecyclerView
    private lateinit var alphabetStrip: AlphabetStripView
    private lateinit var foldableHelper: FoldableHelper
    private lateinit var letterPopup: TextView
    private lateinit var rootLayout: FrameLayout

    // Mutable settings-driven values
    private var scrollSpeedFactor = Settings.DEF_SCROLL_SPEED
    private var pullDownStartY = 0f
    private var pullDownTracking = false
    private var pullDownThresholdPx = 0f
    private var lastBackPressTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_launcher)

        repository = AppRepository(this)

        adapter = AppListAdapter { app -> launchApp(app) }
        layoutManager = LinearLayoutManager(this)

        rootLayout = findViewById(R.id.root_layout)
        recyclerView = findViewById(R.id.app_list)
        recyclerView.layoutManager = layoutManager
        recyclerView.adapter = adapter

        letterPopup = findViewById(R.id.letter_popup)

        alphabetStrip = findViewById(R.id.alphabet_strip)
        alphabetStrip.onLetterSelected = { position ->
            smoothScrollTo(position)
        }
        alphabetStrip.onLetterPreview = { letter, y ->
            if (letter != null) {
                letterPopup.text = letter.toString()
                letterPopup.visibility = View.VISIBLE
                val stripLocation = IntArray(2)
                alphabetStrip.getLocationInWindow(stripLocation)
                val rootLocation = IntArray(2)
                rootLayout.getLocationInWindow(rootLocation)
                letterPopup.translationY = stripLocation[1] - rootLocation[1] + y - letterPopup.height / 2f
                letterPopup.alpha = 1f
            } else {
                letterPopup.animate().alpha(0f).setDuration(150).withEndAction {
                    letterPopup.visibility = View.GONE
                }.start()
            }
        }

        repository.apps.observe(this) { apps ->
            val items = mutableListOf<ListItem>()
            val positions = mutableMapOf<Char, Int>()
            var lastLetter: Char? = null

            for (app in apps) {
                val letter = app.sortLetter
                if (letter != lastLetter) {
                    positions[letter] = items.size
                    items.add(ListItem.HeaderItem(letter))
                    lastLetter = letter
                }
                items.add(ListItem.AppItem(app))
            }

            adapter.submitList(items)
            alphabetStrip.setLetterPositions(positions)
        }

        // Settings entry is via double-tap back button (see onBackPressed)

        foldableHelper = FoldableHelper(this)
        foldableHelper.observe(lifecycle, lifecycleScope)

        lifecycleScope.launch {
            foldableHelper.foldState.collect { _ ->
                // Compact mode is handled by resource qualifiers (values vs values-sw600dp)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        repository.register()
        applySettings()
    }

    private fun applySettings() {
        val density = resources.displayMetrics.density

        // RecyclerView padding
        recyclerView.setPadding(
            0,
            (Settings.listTopPaddingDp * density).toInt(),
            (Settings.alphabetStripWidthDp * density).toInt(),
            (Settings.listBottomPaddingDp * density).toInt()
        )

        // Alphabet strip width + padding
        alphabetStrip.layoutParams = (alphabetStrip.layoutParams).apply {
            width = (Settings.alphabetStripWidthDp * density).toInt()
        }
        val stripPad = (Settings.stripVerticalPaddingDp * density).toInt()
        alphabetStrip.setPadding(0, stripPad, 0, stripPad)

        // Alphabet strip visual properties
        alphabetStrip.pillOpacityPercent = Settings.pillOpacityPercent
        alphabetStrip.pillCornerRadiusDp = Settings.pillCornerRadiusDp
        alphabetStrip.highlightScale = Settings.highlightScale

        // Adapter properties
        adapter.pressScale = Settings.pressScale
        adapter.enterAnimSlidePx = Settings.enterAnimSlideDp * density
        adapter.enterAnimDurationMs = Settings.enterAnimDurationMs.toLong()
        adapter.itemVerticalPaddingPx = (Settings.itemVerticalPaddingDp * density).toInt()
        adapter.itemHorizontalPaddingPx = (Settings.itemHorizontalPaddingDp * density).toInt()
        adapter.iconTextMarginPx = (Settings.iconTextMarginDp * density).toInt()
        adapter.resetAnimations()

        // Scroll speed
        scrollSpeedFactor = Settings.scrollSpeed

        // Pull-down threshold
        pullDownThresholdPx = Settings.pullDownThresholdDp * density

        // Letter popup margin (position relative to strip)
        val popupMargin = (Settings.alphabetStripWidthDp * density).toInt() + (16 * density).toInt()
        (letterPopup.layoutParams as FrameLayout.LayoutParams).marginEnd = popupMargin

        // Force rebind visible items to pick up new padding/scale
        adapter.notifyDataSetChanged()
    }

    private fun smoothScrollTo(position: Int) {
        val firstVisible = layoutManager.findFirstVisibleItemPosition()
        val distance = kotlin.math.abs(position - firstVisible)

        if (distance > 20) {
            val snapTo = if (position > firstVisible) position - 5 else position + 5
            layoutManager.scrollToPositionWithOffset(snapTo.coerceIn(0, adapter.itemCount - 1), 0)
        }

        val speed = scrollSpeedFactor
        val scroller = object : LinearSmoothScroller(this) {
            override fun getVerticalSnapPreference(): Int = SNAP_TO_START
            override fun calculateSpeedPerPixel(displayMetrics: android.util.DisplayMetrics): Float {
                return speed / displayMetrics.densityDpi
            }
        }
        scroller.targetPosition = position
        layoutManager.startSmoothScroll(scroller)
    }

    // Intercept ALL touches for pull-down detection (fixes the bug where
    // setOnTouchListener on root never fires because RecyclerView consumes events)
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (handlePullDown(event)) return true
        return super.dispatchTouchEvent(event)
    }

    @SuppressLint("WrongConstant")
    private fun handlePullDown(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (!recyclerView.canScrollVertically(-1)) {
                    pullDownStartY = event.rawY
                    pullDownTracking = true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (pullDownTracking && !recyclerView.canScrollVertically(-1)) {
                    val deltaY = event.rawY - pullDownStartY
                    if (deltaY > pullDownThresholdPx) {
                        pullDownTracking = false
                        expandNotificationShade()
                        return true
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                pullDownTracking = false
            }
        }
        return false
    }

    @SuppressLint("WrongConstant")
    private fun expandNotificationShade() {
        try {
            val sbm = getSystemService("statusbar")
            sbm?.javaClass?.getMethod("expandNotificationsPanel")?.invoke(sbm)
        } catch (_: Exception) {
            // Silently fail if method unavailable
        }
    }

    override fun onStop() {
        super.onStop()
        repository.unregister()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        foldableHelper.reevaluate()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val now = System.currentTimeMillis()
        if (now - lastBackPressTime < 400) {
            startActivity(Intent(this, SettingsActivity::class.java))
            lastBackPressTime = 0L
        } else {
            lastBackPressTime = now
        }
    }

    private fun launchApp(app: AppInfo) {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            component = ComponentName(app.packageName, app.activityName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        }
        startActivity(intent)
    }
}
