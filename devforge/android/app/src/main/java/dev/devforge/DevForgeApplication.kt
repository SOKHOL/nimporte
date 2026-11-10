package dev.devforge

import android.app.Application

class DevForgeApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.load()
    }
}
