package es.nspc.voz

import android.app.Application
import es.nspc.voz.core.diagnostics.CrashReporter

class NspcVozApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
        CrashReporter.install(this)
    }
}
