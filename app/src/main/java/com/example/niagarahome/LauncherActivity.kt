package com.example.niagarahome

import android.content.ComponentName
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch

class LauncherActivity : AppCompatActivity() {

    private lateinit var repository: AppRepository
    private lateinit var adapter: AppListAdapter
    private lateinit var layoutManager: LinearLayoutManager
    private lateinit var alphabetStrip: AlphabetStripView
    private lateinit var foldableHelper: FoldableHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_launcher)

        repository = AppRepository(this)

        adapter = AppListAdapter { app -> launchApp(app) }
        layoutManager = LinearLayoutManager(this)

        val recyclerView = findViewById<RecyclerView>(R.id.app_list)
        recyclerView.layoutManager = layoutManager
        recyclerView.adapter = adapter

        alphabetStrip = findViewById(R.id.alphabet_strip)
        alphabetStrip.onLetterSelected = { position ->
            layoutManager.scrollToPositionWithOffset(position, 0)
        }

        repository.apps.observe(this) { apps ->
            adapter.submitList(apps)
        }

        repository.letterPositions.observe(this) { positions ->
            alphabetStrip.setLetterPositions(positions)
        }

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
