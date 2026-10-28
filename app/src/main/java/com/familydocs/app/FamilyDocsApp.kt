package com.familydocs.app

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner

class FamilyDocsApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(container.appLock)
        // Keeps the daily backup scheduled (and moves older weekly schedules onto it).
        if (container.settings.value.backupEnabled) com.familydocs.app.backup.BackupWorker.schedule(this, true)
    }
}
