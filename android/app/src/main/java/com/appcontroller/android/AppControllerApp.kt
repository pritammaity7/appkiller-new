package com.appcontroller.android

import android.app.Application

class AppControllerApp : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: AppControllerApp
            private set
    }
}
