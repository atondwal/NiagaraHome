package com.example.niagarahome

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

sealed class ListItem {
    data class HeaderItem(val letter: Char) : ListItem()
    data class AppItem(val appInfo: AppInfo) : ListItem()
}

class AppListAdapter(
    private val onClick: (AppInfo) -> Unit
) : ListAdapter<ListItem, RecyclerView.ViewHolder>(DIFF) {

    private var lastAnimatedPosition = -1

    class AppViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.app_icon)
        val name: TextView = view.findViewById(R.id.app_name)
    }

    class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val letter: TextView = view.findViewById(R.id.section_letter)
    }

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is ListItem.HeaderItem -> VIEW_TYPE_HEADER
        is ListItem.AppItem -> VIEW_TYPE_APP
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_HEADER -> {
                val view = inflater.inflate(R.layout.item_section_header, parent, false)
                HeaderViewHolder(view)
            }
            else -> {
                val view = inflater.inflate(R.layout.item_app, parent, false)
                AppViewHolder(view)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is ListItem.HeaderItem -> {
                (holder as HeaderViewHolder).letter.text = item.letter.toString()
            }
            is ListItem.AppItem -> {
                val app = item.appInfo
                val h = holder as AppViewHolder
                h.icon.setImageDrawable(app.icon)
                h.name.text = app.label
                h.itemView.setOnClickListener { onClick(app) }

                // Press scale animation
                h.itemView.setOnTouchListener { v, event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            v.animate().scaleX(0.97f).scaleY(0.97f).setDuration(100).start()
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start()
                        }
                    }
                    false // don't consume — let click listener work
                }

                // Enter animation (fade + slide up) on first bind only
                if (position > lastAnimatedPosition) {
                    lastAnimatedPosition = position
                    val view = h.itemView
                    view.alpha = 0f
                    view.translationY = 40f
                    val delay = (position * 15L).coerceAtMost(300L)
                    view.animate()
                        .alpha(1f)
                        .translationY(0f)
                        .setDuration(250)
                        .setStartDelay(delay)
                        .setInterpolator(DecelerateInterpolator())
                        .start()
                }
            }
        }
    }

    fun resetAnimations() {
        lastAnimatedPosition = -1
    }

    companion object {
        const val VIEW_TYPE_HEADER = 0
        const val VIEW_TYPE_APP = 1

        private val DIFF = object : DiffUtil.ItemCallback<ListItem>() {
            override fun areItemsTheSame(a: ListItem, b: ListItem): Boolean = when {
                a is ListItem.HeaderItem && b is ListItem.HeaderItem -> a.letter == b.letter
                a is ListItem.AppItem && b is ListItem.AppItem ->
                    a.appInfo.packageName == b.appInfo.packageName &&
                        a.appInfo.activityName == b.appInfo.activityName
                else -> false
            }

            override fun areContentsTheSame(a: ListItem, b: ListItem): Boolean = when {
                a is ListItem.HeaderItem && b is ListItem.HeaderItem -> a.letter == b.letter
                a is ListItem.AppItem && b is ListItem.AppItem ->
                    a.appInfo.label == b.appInfo.label &&
                        a.appInfo.packageName == b.appInfo.packageName
                else -> false
            }
        }
    }
}
