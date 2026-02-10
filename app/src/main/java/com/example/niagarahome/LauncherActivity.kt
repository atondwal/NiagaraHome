package com.example.niagarahome

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Intent
import android.content.res.Configuration
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch

class LauncherActivity : AppCompatActivity() {

    private lateinit var repository: AppRepository
    private lateinit var adapter: AppListAdapter
    private lateinit var layoutManager: RecyclerView.LayoutManager
    private lateinit var recyclerView: RecyclerView
    private lateinit var alphabetStrip: AlphabetStripView
    private lateinit var foldableHelper: FoldableHelper
    private lateinit var letterPopup: TextView
    private lateinit var searchIndicator: TextView
    private lateinit var hiddenInput: EditText
    private lateinit var searchButton: View
    private lateinit var rootLayout: FrameLayout

    // Search state
    private var searchQuery = ""
    private var fullItems: List<ListItem> = emptyList()
    private var updatingInput = false

    // Mutable settings-driven values
    private var pullDownStartY = 0f
    private var pullDownTracking = false
    private var pullDownThresholdPx = 0f
    private var settingsApplied = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_launcher)

        repository = AppRepository(this)

        adapter = AppListAdapter(
            onClick = { app -> launchApp(app) },
            onLongClick = { app, view -> showAppContextMenu(app, view) }
        )

        rootLayout = findViewById(R.id.root_layout)
        recyclerView = findViewById(R.id.app_list)
        layoutManager = createLayoutManager(FoldState.UNKNOWN)
        recyclerView.layoutManager = layoutManager
        recyclerView.adapter = adapter

        letterPopup = findViewById(R.id.letter_popup)
        searchIndicator = findViewById(R.id.search_indicator)

        hiddenInput = findViewById(R.id.hidden_input)
        hiddenInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (updatingInput) return
                searchQuery = s?.toString() ?: ""
                onSearchQueryChanged()
            }
        })
        hiddenInput.setOnEditorActionListener { _, _, _ ->
            launchFirstMatch()
            true
        }

        searchButton = findViewById(R.id.search_button)
        searchButton.setOnClickListener { showKeyboard() }

        alphabetStrip = findViewById(R.id.alphabet_strip)
        alphabetStrip.onLetterSelected = { position ->
            smoothScrollTo(position)
        }
        alphabetStrip.onFineScroll = { fraction ->
            val totalItems = adapter.itemCount
            if (totalItems > 0) {
                val target = (fraction * totalItems).toInt().coerceIn(0, totalItems - 1)
                smoothScrollTo(target)
            }
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
            fullItems = buildItemList(apps)
            if (searchQuery.isEmpty()) {
                submitItemsWithPositions(fullItems)
            } else {
                applySearchFilter()
            }
        }

        // Settings entry is via double-tap back button (see onBackPressed)

        foldableHelper = FoldableHelper(this)
        foldableHelper.observe(lifecycle, lifecycleScope)

        lifecycleScope.launch {
            foldableHelper.foldState.collect { state ->
                val newManager = createLayoutManager(state)
                if (newManager::class != layoutManager::class) {
                    layoutManager = newManager
                    recyclerView.layoutManager = layoutManager
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        repository.register()
        applySettings(resetAnimations = !settingsApplied)
        settingsApplied = true
        clearSearch()
        recyclerView.requestFocus()
        recyclerView.scrollToPosition(0)
    }

    private fun applySettings(resetAnimations: Boolean = false) {
        val density = resources.displayMetrics.density

        // Alphabet strip margin + width
        val stripMargin = (Settings.stripEndMarginDp * density).toInt()
        val stripLp = alphabetStrip.layoutParams as FrameLayout.LayoutParams
        stripLp.width = (Settings.alphabetStripWidthDp * density).toInt()
        stripLp.marginEnd = stripMargin
        alphabetStrip.layoutParams = stripLp
        val stripPad = (Settings.stripVerticalPaddingDp * density).toInt()
        alphabetStrip.setPadding(0, stripPad, 0, stripPad)

        // RecyclerView padding
        recyclerView.setPadding(
            0,
            (Settings.listTopPaddingDp * density).toInt(),
            (Settings.alphabetStripWidthDp * density + stripMargin).toInt(),
            (Settings.listBottomPaddingDp * density).toInt()
        )

        // Alphabet strip visual properties
        alphabetStrip.pillOpacityPercent = Settings.pillOpacityPercent
        alphabetStrip.pillCornerRadiusDp = Settings.pillCornerRadiusDp
        alphabetStrip.highlightScale = Settings.highlightScale
        alphabetStrip.fineThresholdPx = Settings.fineScrollThresholdDp * density
        alphabetStrip.bulgeMarginPx = Settings.bulgeMarginDp * density
        alphabetStrip.bulgeRadius = Settings.bulgeRadius.toFloat()

        // Adapter properties
        adapter.pressScale = Settings.pressScale
        adapter.enterAnimSlidePx = Settings.enterAnimSlideDp * density
        adapter.enterAnimDurationMs = Settings.enterAnimDurationMs.toLong()
        adapter.itemVerticalPaddingPx = (Settings.itemVerticalPaddingDp * density).toInt()
        adapter.itemHorizontalPaddingPx = (Settings.itemHorizontalPaddingDp * density).toInt()
        adapter.iconTextMarginPx = (Settings.iconTextMarginDp * density).toInt()
        if (resetAnimations) {
            adapter.resetAnimations()
        }

        // Pull-down threshold
        pullDownThresholdPx = Settings.pullDownThresholdDp * density

        // Letter popup margin (position relative to strip)
        val popupMargin = (Settings.alphabetStripWidthDp * density).toInt() + (16 * density).toInt()
        (letterPopup.layoutParams as FrameLayout.LayoutParams).marginEnd = popupMargin

        // Force rebind visible items to pick up new padding/scale
        adapter.notifyDataSetChanged()
    }

    private fun createLayoutManager(state: FoldState): RecyclerView.LayoutManager {
        return if (state == FoldState.MAIN_SCREEN) {
            GridLayoutManager(this, 2).apply {
                spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                    override fun getSpanSize(position: Int): Int {
                        return if (adapter.getItemViewType(position) == AppListAdapter.VIEW_TYPE_HEADER) 2 else 1
                    }
                }
            }
        } else {
            LinearLayoutManager(this)
        }
    }

    private fun smoothScrollTo(position: Int) {
        val lm = layoutManager
        when (lm) {
            is LinearLayoutManager -> lm.scrollToPositionWithOffset(position, 0)
            is GridLayoutManager -> lm.scrollToPositionWithOffset(position, 0)
            else -> recyclerView.scrollToPosition(position)
        }
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
        recyclerView.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
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
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    private fun launchFirstMatch() {
        val first = adapter.currentList.firstOrNull { it is ListItem.AppItem } as? ListItem.AppItem
        if (first != null) launchApp(first.appInfo)
    }

    private fun launchApp(app: AppInfo) {
        clearSearch()
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            component = ComponentName(app.packageName, app.activityName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        }
        startActivity(intent)
    }

    private fun onSearchQueryChanged() {
        if (searchQuery.isEmpty()) {
            searchIndicator.visibility = View.GONE
            alphabetStrip.visibility = View.VISIBLE
            searchButton.visibility = View.VISIBLE
            submitItemsWithPositions(fullItems)
            recyclerView.scrollToPosition(0)
        } else {
            searchIndicator.text = searchQuery
            searchIndicator.visibility = View.VISIBLE
            alphabetStrip.visibility = View.GONE
            searchButton.visibility = View.GONE
            applySearchFilter()
        }
    }

    private fun clearSearch() {
        if (searchQuery.isNotEmpty()) {
            searchQuery = ""
            updatingInput = true
            hiddenInput.text.clear()
            updatingInput = false
            searchIndicator.visibility = View.GONE
            alphabetStrip.visibility = View.VISIBLE
            searchButton.visibility = View.VISIBLE
            submitItemsWithPositions(fullItems)
        }
        hideKeyboard()
        hiddenInput.clearFocus()
        recyclerView.requestFocus()
    }

    private fun showKeyboard() {
        hiddenInput.requestFocus()
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(hiddenInput, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(hiddenInput.windowToken, 0)
    }

    private fun applySearchFilter() {
        val filtered = fullItems.filterIsInstance<ListItem.AppItem>()
            .filter { it.appInfo.label.contains(searchQuery, ignoreCase = true) }
        val items = mutableListOf<ListItem>()
        var lastLetter: Char? = null
        for (appItem in filtered) {
            val letter = appItem.appInfo.sortLetter
            if (letter != lastLetter) {
                items.add(ListItem.HeaderItem(letter))
                lastLetter = letter
            }
            items.add(appItem)
        }
        adapter.submitList(items)
        recyclerView.scrollToPosition(0)
    }

    private fun buildItemList(apps: List<AppInfo>): List<ListItem> {
        val items = mutableListOf<ListItem>()
        var lastLetter: Char? = null
        for (app in apps) {
            val letter = app.sortLetter
            if (letter != lastLetter) {
                items.add(ListItem.HeaderItem(letter))
                lastLetter = letter
            }
            items.add(ListItem.AppItem(app))
        }
        return items
    }

    private fun submitItemsWithPositions(items: List<ListItem>) {
        adapter.submitList(items)
        val positions = mutableMapOf<Char, Int>()
        items.forEachIndexed { index, item ->
            if (item is ListItem.HeaderItem && item.letter !in positions) {
                positions[item.letter] = index
            }
        }
        alphabetStrip.totalItemCount = items.size
        alphabetStrip.setLetterPositions(positions)
    }

    private fun showAppContextMenu(app: AppInfo, anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, 1, 0, "App Info")
        popup.menu.add(0, 2, 1, "Uninstall")
        popup.menu.add(0, 3, 2, "Hide")
        popup.menu.add(0, 4, 3, "Create Shortcut")

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> {
                    val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:${app.packageName}")
                    }
                    startActivity(intent)
                    true
                }
                2 -> {
                    try {
                        val uri = Uri.fromParts("package", app.packageName, null)
                        val intent = Intent(Intent.ACTION_DELETE, uri).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                        }
                        startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(this, "Uninstall failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                    true
                }
                3 -> {
                    Settings.addHiddenApp(app.packageName)
                    repository.reload()
                    Toast.makeText(this, "${app.label} hidden", Toast.LENGTH_SHORT).show()
                    true
                }
                4 -> {
                    createShortcut(app)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun createShortcut(app: AppInfo) {
        val launchIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            component = ComponentName(app.packageName, app.activityName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        }

        val drawable = app.icon
        val bitmap = if (drawable is BitmapDrawable) {
            drawable.bitmap
        } else {
            val bmp = android.graphics.Bitmap.createBitmap(
                drawable.intrinsicWidth.coerceAtLeast(1),
                drawable.intrinsicHeight.coerceAtLeast(1),
                android.graphics.Bitmap.Config.ARGB_8888
            )
            val canvas = android.graphics.Canvas(bmp)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bmp
        }

        val shortcutInfo = ShortcutInfoCompat.Builder(this, "${app.packageName}_${app.activityName}")
            .setShortLabel(app.label)
            .setIcon(IconCompat.createWithBitmap(bitmap))
            .setIntent(launchIntent)
            .build()

        if (ShortcutManagerCompat.isRequestPinShortcutSupported(this)) {
            ShortcutManagerCompat.requestPinShortcut(this, shortcutInfo, null)
        } else {
            Toast.makeText(this, "Shortcuts not supported by launcher", Toast.LENGTH_SHORT).show()
        }
    }
}
