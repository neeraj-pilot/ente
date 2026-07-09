package io.ente.ensu

import android.app.Application
import android.content.Context
import io.ente.ensu.bindings.Transcriber

class EnsuApplication : Application() {
    val transcriber by lazy {
        Transcriber(getDir("ensu_transcription_models", Context.MODE_PRIVATE).absolutePath)
    }
}
