package com.familydocs.app.backup

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.familydocs.app.FamilyDocsApp
import java.util.concurrent.TimeUnit

/** Weekly backup on Wi-Fi. Skips quietly if Drive consent or the backup password is missing. */
class BackupWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val c = (applicationContext as FamilyDocsApp).container
        if (!c.settings.value.backupEnabled || !c.backup.keys.hasPassword) return Result.success()
        val auth = runCatching { c.backup.authorize() }.getOrElse { return Result.retry() }
        if (auth.hasResolution()) return Result.success() // needs the user; Settings shows "Back up now"
        val token = auth.accessToken ?: return Result.retry()
        return runCatching { c.backup.backup(token, c.settings.value.viewableCopies) }
            .onSuccess { c.settings.update { s -> s.copy(lastBackup = System.currentTimeMillis()) } }
            .fold({ Result.success() }, { Result.retry() })
    }

    companion object {
        private const val NAME = "familydocs-drive-backup"
        fun schedule(context: Context, enabled: Boolean) {
            val wm = WorkManager.getInstance(context)
            if (!enabled) { wm.cancelUniqueWork(NAME); return }
            val req = PeriodicWorkRequestBuilder<BackupWorker>(7, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).setRequiresBatteryNotLow(true).build())
                .build()
            wm.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
