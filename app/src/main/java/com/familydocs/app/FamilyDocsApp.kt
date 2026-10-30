package com.familydocs.app

import kotlinx.coroutines.launch
import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle

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
        com.familydocs.app.backup.BackupWorker.scheduleChecks(this) // shared groups every 15 minutes in the background
        // While Lararium is open (and unlocked): the others' changes come in within a minute.
        val process = androidx.lifecycle.ProcessLifecycleOwner.get()
        process.lifecycleScope.launch {
            process.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                while (true) {
                    if (!container.appLock.locked.value && container.repo.groupsNow().any { it.peerList.isNotEmpty() })
                        com.familydocs.app.backup.BackupWorker.syncShares(this@FamilyDocsApp)
                    kotlinx.coroutines.delay(60_000)
                }
            }
        }
    }
}
