package com.familydocs.app

import kotlinx.coroutines.launch
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
        container.appScope.launch { container.updater.check() } // quiet check for a newer release
        container.appScope.launch { container.settleGroup() }
        com.familydocs.app.backup.BackupWorker.syncShares(this) // shared groups: pick up what the others changed
        com.familydocs.app.backup.BackupWorker.scheduleChecks(this) // shared groups every few hours
    }
}
