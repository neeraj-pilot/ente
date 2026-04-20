package io.ente.entegram

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import io.ente.entegram.core.logging.AppLogger

@HiltAndroidApp
class EnteGramApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLogger.init(this)
    }
}
