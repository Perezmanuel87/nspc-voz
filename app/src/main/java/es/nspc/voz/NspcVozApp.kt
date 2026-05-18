package es.nspc.voz

import android.app.Application

class NspcVozApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
    }
}
