package com.mhrsdev.musicplayer

import android.app.Application
import com.mhrsdev.musicplayer.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class MusicPlayerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@MusicPlayerApp)
            modules(appModule)
        }
    }
}
