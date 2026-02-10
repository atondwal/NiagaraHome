package com.example.niagarahome

import android.annotation.SuppressLint
import android.app.Activity
import android.util.Log
import android.appwidget.AppWidgetManager
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

    // Widget hosting
    private lateinit var widgetPage: WidgetPageView
    private lateinit var appPage: FrameLayout
    private lateinit var widgetRepository: WidgetRepository

    // App list visibility
    private var appListVisible = false

    companion object {
        private const val RC_PICK_WIDGET = 1001
        private const val RC_BIND_WIDGET = 1002
        private const val RC_CONFIGURE_WIDGET = 1003
    }

    // Search state
    private var searchQuery = ""
    private var fullItems: List<ListItem> = emptyList()
    private var updatingInput = false
    private var stripFilterActive = false

    // Mutable settings-driven values
    private var pullDownStartY = 0f
    private var pullDownTracking = false
    private var pullDownThresholdPx = 0f
    private var swipeUpStartY = 0f
    private var swipeUpTracking = false
    private var settingsApplied = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_launcher)

        repository = AppRepository(this)
        widgetRepository = WidgetRepository(applicationContext)

        adapter = AppListAdapter(
            onClick = { app -> launchApp(app) },
            onLongClick = { app, view -> showAppContextMenu(app, view) },
            onPlayStoreClick = { query -> searchPlayStore(query) }
        )

        rootLayout = findViewById(R.id.root_layout)
        recyclerView = findViewById(R.id.app_list)
        layoutManager = createLayoutManager(FoldState.UNKNOWN)
        recyclerView.layoutManager = layoutManager
        recyclerView.adapter = adapter

        letterPopup = findViewById(R.id.letter_popup)
        searchIndicator = findViewById(R.id.search_indicator)

        // Widget page + app page
        widgetPage = findViewById(R.id.widget_page)
        appPage = findViewById(R.id.app_page)

        // Start with app page off-screen right (use visibility until we know the width)
        appPage.visibility = View.INVISIBLE
        appPage.post {
            appPage.translationX = appPage.width.toFloat()
            appPage.visibility = View.VISIBLE
        }

        widgetPage.onAddWidgetClick = { startWidgetPicker() }
        widgetPage.onWidgetRemove = { widgetId ->
            widgetPage.removeWidgetView(widgetId)
            widgetRepository.removeWidgetId(widgetId)
            widgetRepository.removeWidgetHeight(widgetId)
        }
        widgetPage.onWidgetResized = { widgetId, heightPx ->
            val heightDp = (heightPx / resources.displayMetrics.density).toInt()
            widgetRepository.setWidgetHeightDp(widgetId, heightDp)
        }

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
        searchButton.visibility = View.GONE // hidden until app list is shown

        alphabetStrip = findViewById(R.id.alphabet_strip)
        alphabetStrip.onLetterSelected = { letter ->
            if (!appListVisible) showAppList()
            filterByLetter(letter)
        }
        alphabetStrip.onFineScroll = { fraction ->
            if (!appListVisible) showAppList()
            if (stripFilterActive) {
                endStripFilter()
            }
            scrollToFraction(fraction)
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
                if (stripFilterActive) {
                    endStripFilter()
                }
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
        widgetRepository.startListening()
        applySettings(resetAnimations = !settingsApplied)
        settingsApplied = true
        clearSearch()
        hideAppList(animate = false)
        recoverPendingWidget()
        restoreSavedWidgets()
        recyclerView.requestFocus()
        recyclerView.scrollToPosition(0)
    }

    override fun onStop() {
        super.onStop()
        repository.unregister()
        widgetRepository.stopListening()
    }

    // --- App list slide animation ---

    private fun showAppList() {
        if (appListVisible) return
        appListVisible = true
        searchButton.visibility = View.VISIBLE
        appPage.animate()
            .translationX(0f)
            .setDuration(200)
            .start()
    }

    private fun hideAppList(animate: Boolean = true) {
        if (!animate) {
            appListVisible = false
            // Use screen width as fallback when appPage hasn't been laid out yet
            val offScreen = if (appPage.width > 0) appPage.width.toFloat()
                else resources.displayMetrics.widthPixels.toFloat()
            appPage.translationX = offScreen
            searchButton.visibility = View.GONE
            clearSearch()
            return
        }
        if (!appListVisible) return
        appListVisible = false
        searchButton.visibility = View.GONE
        clearSearch()
        appPage.animate()
            .translationX(appPage.width.toFloat())
            .setDuration(200)
            .start()
    }

    // --- Widget hosting ---

    @Suppress("DEPRECATION")
    private fun startWidgetPicker() {
        val id = widgetRepository.allocateWidgetId()
        widgetRepository.pendingWidgetId = id
        Log.d("NHWidget", "startWidgetPicker pendingId=$id")
        val pickIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_PICK).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        }
        startActivityForResult(pickIntent, RC_PICK_WIDGET)
    }

    @Suppress("DEPRECATION")
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val pending = widgetRepository.pendingWidgetId
        Log.d("NHWidget", "onActivityResult req=$requestCode result=$resultCode pendingId=$pending")
        when (requestCode) {
            RC_PICK_WIDGET -> handleWidgetPickerResult(resultCode, data)
            RC_BIND_WIDGET -> handleWidgetBindResult(resultCode)
            RC_CONFIGURE_WIDGET -> handleWidgetConfigureResult(resultCode)
        }
    }

    @Suppress("DEPRECATION")
    private fun handleWidgetPickerResult(resultCode: Int, data: Intent?) {
        val pending = widgetRepository.pendingWidgetId
        if (pending == -1) return
        if (resultCode != Activity.RESULT_OK || data == null) {
            widgetRepository.appWidgetHost.deleteAppWidgetId(pending)
            widgetRepository.pendingWidgetId = -1
            return
        }
        val widgetId = data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pending)
        widgetRepository.pendingWidgetId = widgetId

        val info = widgetRepository.getProviderInfo(widgetId)
        Log.d("NHWidget", "Picked widget $widgetId provider=${info?.provider}")
        if (info == null) {
            widgetRepository.appWidgetHost.deleteAppWidgetId(widgetId)
            widgetRepository.pendingWidgetId = -1
            return
        }

        // Picker already bound the widget — proceed directly
        proceedAfterBind(widgetId, info)
    }

    @Suppress("DEPRECATION")
    private fun handleWidgetBindResult(resultCode: Int) {
        val pending = widgetRepository.pendingWidgetId
        Log.d("NHWidget", "Bind result=$resultCode pendingId=$pending")
        if (pending == -1) return
        if (resultCode != Activity.RESULT_OK) {
            widgetRepository.appWidgetHost.deleteAppWidgetId(pending)
            widgetRepository.pendingWidgetId = -1
            return
        }
        val info = widgetRepository.getProviderInfo(pending)
        if (info == null) {
            widgetRepository.appWidgetHost.deleteAppWidgetId(pending)
            widgetRepository.pendingWidgetId = -1
            return
        }
        proceedAfterBind(pending, info)
    }

    @Suppress("DEPRECATION")
    private fun proceedAfterBind(widgetId: Int, info: android.appwidget.AppWidgetProviderInfo) {
        if (info.configure != null) {
            Log.d("NHWidget", "Launching configure: ${info.configure}")
            val configIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                component = info.configure
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            }
            startActivityForResult(configIntent, RC_CONFIGURE_WIDGET)
        } else {
            finishAddWidget(widgetId)
        }
    }

    private fun handleWidgetConfigureResult(resultCode: Int) {
        val pending = widgetRepository.pendingWidgetId
        Log.d("NHWidget", "Configure result=$resultCode pendingId=$pending")
        if (pending == -1) return
        if (resultCode != Activity.RESULT_OK) {
            widgetRepository.appWidgetHost.deleteAppWidgetId(pending)
            widgetRepository.pendingWidgetId = -1
            return
        }
        finishAddWidget(pending)
    }

    /** Recover a pending widget from a previous process death. If the widget is
     *  still bound, save it so restoreSavedWidgets() will display it. The host
     *  will receive updates automatically once the configure activity finishes. */
    private fun recoverPendingWidget() {
        val pending = widgetRepository.pendingWidgetId
        if (pending == -1) return
        // Already saved (onActivityResult handled it before we got here)
        if (pending in widgetRepository.getSavedWidgetIds()) {
            widgetRepository.pendingWidgetId = -1
            return
        }
        val info = widgetRepository.getProviderInfo(pending)
        if (info == null) {
            Log.d("NHWidget", "Cleaning up unbound pending widget $pending")
            widgetRepository.appWidgetHost.deleteAppWidgetId(pending)
        } else {
            Log.d("NHWidget", "Recovering pending widget $pending")
            widgetRepository.addWidgetId(pending)
        }
        widgetRepository.pendingWidgetId = -1
    }

    private fun finishAddWidget(widgetId: Int) {
        Log.d("NHWidget", "finishAddWidget($widgetId)")
        widgetRepository.addWidgetId(widgetId)
        val info = widgetRepository.getProviderInfo(widgetId) ?: return
        val hostView = widgetRepository.createView(widgetId)
        setupWidgetView(hostView, info)
        widgetPage.addWidgetView(hostView, widgetId, widgetHeightPx(widgetId, info))
        widgetRepository.pendingWidgetId = -1
    }

    private fun setupWidgetView(hostView: NHWidgetHostView, info: android.appwidget.AppWidgetProviderInfo) {
        val density = resources.displayMetrics.density
        // Calculate width in dp (full screen minus padding)
        val widthDp = ((resources.displayMetrics.widthPixels - (32 * density).toInt()) / density).toInt()
        val heightDp = if (info.minHeight > 0) info.minHeight else 200
        hostView.updateAppWidgetSize(null, widthDp, heightDp, widthDp, heightDp)
    }

    private fun widgetHeightPx(widgetId: Int, info: android.appwidget.AppWidgetProviderInfo): Int {
        val density = resources.displayMetrics.density
        val savedDp = widgetRepository.getWidgetHeightDp(widgetId)
        if (savedDp > 0) return (savedDp * density).toInt()
        return if (info.minHeight > 0) (info.minHeight * density).toInt()
        else (200 * density).toInt()
    }

    private fun restoreSavedWidgets() {
        val ids = widgetRepository.getSavedWidgetIds()
        Log.d("NHWidget", "Restoring ${ids.size} widgets: $ids")
        widgetPage.clearWidgets()
        for (id in ids) {
            val info = widgetRepository.getProviderInfo(id)
            if (info != null) {
                val hostView = widgetRepository.createView(id)
                setupWidgetView(hostView, info)
                widgetPage.addWidgetView(hostView, id, widgetHeightPx(id, info))
            } else {
                widgetRepository.removeWidgetId(id)
            }
        }
    }


    // --- Settings ---

    private fun applySettings(resetAnimations: Boolean = false) {
        val density = resources.displayMetrics.density

        val stripMargin = (Settings.stripEndMarginDp * density).toInt()
        val touchMarginPx = (Settings.pillTouchMarginDp * density).toInt()
        val visualWidth = (Settings.alphabetStripWidthDp * density).toInt()
        val stripLp = alphabetStrip.layoutParams as FrameLayout.LayoutParams
        stripLp.width = visualWidth + touchMarginPx
        stripLp.marginEnd = stripMargin
        stripLp.topMargin = (Settings.stripTopMarginDp * density).toInt()
        stripLp.bottomMargin = (Settings.stripBottomMarginDp * density).toInt()
        alphabetStrip.layoutParams = stripLp
        alphabetStrip.touchMarginLeftPx = touchMarginPx.toFloat()
        val stripPad = (Settings.stripVerticalPaddingDp * density).toInt()
        alphabetStrip.setPadding(0, stripPad, 0, stripPad)

        recyclerView.setPadding(
            0,
            (Settings.listTopPaddingDp * density).toInt(),
            (visualWidth + stripMargin),
            (Settings.listBottomPaddingDp * density).toInt()
        )

        alphabetStrip.highlightScale = Settings.highlightScale
        alphabetStrip.fineThresholdPx = Settings.fineScrollThresholdDp * density
        alphabetStrip.bulgeMarginPx = Settings.bulgeMarginDp * density
        alphabetStrip.bulgeRadius = Settings.bulgeRadius.toFloat()

        adapter.pressScale = Settings.pressScale
        adapter.enterAnimSlidePx = Settings.enterAnimSlideDp * density
        adapter.enterAnimDurationMs = Settings.enterAnimDurationMs.toLong()
        adapter.itemVerticalPaddingPx = (Settings.itemVerticalPaddingDp * density).toInt()
        adapter.itemHorizontalPaddingPx = (Settings.itemHorizontalPaddingDp * density).toInt()
        adapter.iconTextMarginPx = (Settings.iconTextMarginDp * density).toInt()
        if (resetAnimations) {
            adapter.resetAnimations()
        }

        pullDownThresholdPx = Settings.pullDownThresholdDp * density

        val popupMargin = (Settings.alphabetStripWidthDp * density).toInt() + (16 * density).toInt()
        (letterPopup.layoutParams as FrameLayout.LayoutParams).marginEnd = popupMargin

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

    private fun scrollToFraction(fraction: Float) {
        val totalHeight = recyclerView.computeVerticalScrollRange()
        val viewHeight = recyclerView.computeVerticalScrollExtent()
        val maxScroll = totalHeight - viewHeight
        if (maxScroll <= 0) return
        val targetScroll = (fraction * maxScroll).toInt()
        val currentScroll = recyclerView.computeVerticalScrollOffset()
        recyclerView.scrollBy(0, targetScroll - currentScroll)
    }

    // Intercept ALL touches for pull-down, swipe-up, and outside-tap detection
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (!widgetPage.isEditingWidget()) {
            if (handlePullDown(event)) return true
            if (handleSwipeUp(event)) return true
        }
        // Tap outside app list dismisses it
        if (event.action == MotionEvent.ACTION_DOWN && appListVisible) {
            val x = event.rawX
            val loc = IntArray(2)
            appPage.getLocationOnScreen(loc)
            val appPageLeft = loc[0] + appPage.translationX
            // Also check the strip — don't dismiss if touching the strip
            alphabetStrip.getLocationOnScreen(loc)
            val stripLeft = loc[0].toFloat()
            if (x < appPageLeft && x < stripLeft) {
                hideAppList()
                return true
            }
        }
        return super.dispatchTouchEvent(event)
    }

    @SuppressLint("WrongConstant")
    private fun handlePullDown(event: MotionEvent): Boolean {
        // Only handle pull-down on widget page (when app list is hidden)
        if (appListVisible) return false
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                pullDownStartY = event.rawY
                pullDownTracking = true
            }
            MotionEvent.ACTION_MOVE -> {
                if (pullDownTracking) {
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

    private fun handleSwipeUp(event: MotionEvent): Boolean {
        // Swipe up on widget page opens app list + keyboard for search
        if (appListVisible) return false
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                swipeUpStartY = event.rawY
                swipeUpTracking = true
            }
            MotionEvent.ACTION_MOVE -> {
                if (swipeUpTracking) {
                    val deltaY = swipeUpStartY - event.rawY
                    if (deltaY > pullDownThresholdPx) {
                        swipeUpTracking = false
                        showAppList()
                        showKeyboard()
                        return true
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                swipeUpTracking = false
            }
        }
        return false
    }

    @SuppressLint("WrongConstant")
    private fun expandNotificationShade() {
        rootLayout.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
        try {
            val sbm = getSystemService("statusbar")
            sbm?.javaClass?.getMethod("expandNotificationsPanel")?.invoke(sbm)
        } catch (_: Exception) {}
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        foldableHelper.reevaluate()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (appListVisible) {
            hideAppList()
        } else {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun launchFirstMatch() {
        val first = adapter.currentList.firstOrNull { it is ListItem.AppItem } as? ListItem.AppItem
        if (first != null) launchApp(first.appInfo)
    }

    private fun launchApp(app: AppInfo) {
        clearSearch()
        hideAppList()
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
            searchButton.visibility = if (appListVisible) View.VISIBLE else View.GONE
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
            searchButton.visibility = if (appListVisible) View.VISIBLE else View.GONE
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
        items.add(ListItem.PlayStoreItem(searchQuery))
        adapter.submitList(items)
        recyclerView.scrollToPosition(0)
    }

    private fun searchPlayStore(query: String) {
        clearSearch()
        hideAppList()
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=$query")))
        } catch (_: Exception) {
            startActivity(Intent(Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/search?q=$query")))
        }
    }

    private fun filterByLetter(letter: Char) {
        if (searchQuery.isNotEmpty()) return
        stripFilterActive = true
        val filtered = fullItems.filter { item ->
            when (item) {
                is ListItem.HeaderItem -> item.letter == letter
                is ListItem.AppItem -> item.appInfo.sortLetter == letter
                else -> false
            }
        }
        adapter.submitList(filtered)
        recyclerView.scrollToPosition(0)
    }

    private fun endStripFilter() {
        stripFilterActive = false
        submitItemsWithPositions(fullItems)
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
