package com.ezequielbrrt.domemory

import android.app.Application

class DoMemoryApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(context = this)
    }
}
