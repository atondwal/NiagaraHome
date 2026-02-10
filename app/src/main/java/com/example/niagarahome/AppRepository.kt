package com.example.niagarahome

import android.content.Context
import android.content.pm.LauncherApps
import android.os.UserHandle
import android.os.UserManager
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

class AppRepository(private val context: Context) {

    private val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    private val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager

    private val _apps = MutableLiveData<List<AppInfo>>()
    val apps: LiveData<List<AppInfo>> = _apps

    private val _letterPositions = MutableLiveData<Map<Char, Int>>()
    val letterPositions: LiveData<Map<Char, Int>> = _letterPositions

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) = reload()
        override fun onPackageAdded(packageName: String, user: UserHandle) = reload()
        override fun onPackageChanged(packageName: String, user: UserHandle) = reload()
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = reload()
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = reload()
    }

    fun register() {
        launcherApps.registerCallback(callback)
        reload()
    }

    fun unregister() {
        launcherApps.unregisterCallback(callback)
    }

    fun reload() {
        val allApps = mutableListOf<AppInfo>()
        for (profile in userManager.userProfiles) {
            val activities = launcherApps.getActivityList(null, profile)
            for (info in activities) {
                allApps.add(
                    AppInfo(
                        label = info.label.toString(),
                        packageName = info.applicationInfo.packageName,
                        activityName = info.componentName.className,
                        icon = info.getBadgedIcon(0)
                    )
                )
            }
        }
        val hidden = Settings.hiddenApps
        allApps.removeAll { it.packageName in hidden }
        allApps.sortWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })

        val positions = mutableMapOf<Char, Int>()
        for ((index, app) in allApps.withIndex()) {
            val letter = app.sortLetter
            if (letter !in positions) {
                positions[letter] = index
            }
        }

        _apps.postValue(allApps)
        _letterPositions.postValue(positions)
    }
}
