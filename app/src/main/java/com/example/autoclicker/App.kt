package com.example.autoclicker

import android.app.Application
import com.example.autoclicker.util.ConfigStore

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        ConfigStore.ensureDefaults(this)
    }

    companion object {
        lateinit var instance: App
            private set
    }
}
