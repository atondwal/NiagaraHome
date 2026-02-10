package com.example.niagarahome

import android.app.Application

class NiagaraHomeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Settings.init(this)
    }
}
