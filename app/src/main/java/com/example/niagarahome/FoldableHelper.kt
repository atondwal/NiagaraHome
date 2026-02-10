package com.example.niagarahome

import android.app.Activity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class FoldState { COVER_SCREEN, MAIN_SCREEN, UNKNOWN }

class FoldableHelper(private val activity: Activity) {

    private val _foldState = MutableStateFlow(FoldState.UNKNOWN)
    val foldState: StateFlow<FoldState> = _foldState.asStateFlow()

    fun observe(lifecycle: Lifecycle, lifecycleScope: kotlinx.coroutines.CoroutineScope) {
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                WindowInfoTracker.getOrCreate(activity)
                    .windowLayoutInfo(activity)
                    .collect { layoutInfo ->
                        val hasFold = layoutInfo.displayFeatures.any { it is FoldingFeature }
                        _foldState.value = if (hasFold) {
                            FoldState.MAIN_SCREEN
                        } else {
                            evaluateByScreenWidth()
                        }
                    }
            }
        }
    }

    fun reevaluate() {
        if (_foldState.value != FoldState.MAIN_SCREEN) {
            _foldState.value = evaluateByScreenWidth()
        }
    }

    private fun evaluateByScreenWidth(): FoldState {
        val widthDp = activity.resources.configuration.screenWidthDp
        return when {
            widthDp <= 450 -> FoldState.COVER_SCREEN
            widthDp >= 580 -> FoldState.MAIN_SCREEN
            else -> FoldState.UNKNOWN
        }
    }
}
