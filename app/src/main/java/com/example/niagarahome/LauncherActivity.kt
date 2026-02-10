package com.example.niagarahome

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
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

    // Pull-down-to-notify tracking
    private var pullDownStartY = 0f
    private var pullDownTracking = false
    private val pullDownThresholdPx by lazy { 100 * resources.displayMetrics.density }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_launcher)

        repository = AppRepository(this)

        adapter = AppListAdapter { app -> launchApp(app) }
        layoutManager = LinearLayoutManager(this)

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
                // Position vertically centered on the letter in the strip
                val stripLocation = IntArray(2)
                alphabetStrip.getLocationInWindow(stripLocation)
                val popupLocation = IntArray(2)
                letterPopup.getLocationInWindow(popupLocation)
                val rootLocation = IntArray(2)
                findViewById<View>(R.id.root_layout).getLocationInWindow(rootLocation)
                letterPopup.translationY = stripLocation[1] - rootLocation[1] + y - letterPopup.height / 2f
                letterPopup.alpha = 1f
            } else {
                letterPopup.animate().alpha(0f).setDuration(150).withEndAction {
                    letterPopup.visibility = View.GONE
                }.start()
            }
        }

        repository.apps.observe(this) { apps ->
            // Build interleaved list with section headers
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

        // Pull-down to open notification shade
        val rootLayout = findViewById<View>(R.id.root_layout)
        rootLayout.setOnTouchListener { _, event ->
            handlePullDown(event)
        }

        foldableHelper = FoldableHelper(this)
        foldableHelper.observe(lifecycle, lifecycleScope)

        lifecycleScope.launch {
            foldableHelper.foldState.collect { _ ->
                // Compact mode is handled by resource qualifiers (values vs values-sw600dp)
            }
        }
    }

    private fun smoothScrollTo(position: Int) {
        val firstVisible = layoutManager.findFirstVisibleItemPosition()
        val distance = kotlin.math.abs(position - firstVisible)

        if (distance > 20) {
            // For large jumps, snap close first then smooth-scroll the last few items
            val snapTo = if (position > firstVisible) position - 5 else position + 5
            layoutManager.scrollToPositionWithOffset(snapTo.coerceIn(0, adapter.itemCount - 1), 0)
        }

        val scroller = object : LinearSmoothScroller(this) {
            override fun getVerticalSnapPreference(): Int = SNAP_TO_START
            override fun calculateSpeedPerPixel(displayMetrics: android.util.DisplayMetrics): Float {
                // ~4x faster than default (default is ~25ms/inch ≈ 0.01563 ms/px)
                return 4f / displayMetrics.densityDpi
            }
        }
        scroller.targetPosition = position
        layoutManager.startSmoothScroll(scroller)
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

    override fun onStart() {
        super.onStart()
        repository.register()
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
        // Do nothing — we ARE the home screen
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
