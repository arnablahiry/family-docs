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

/**
 * Drive backup: once a day on any connection, and a couple of minutes after a document (or login, person, event)
 * is added, changed or deleted. Only what changed is uploaded. Skips quietly if Google Drive access needs the user
 * (Settings shows "Back up now").
 */
class BackupWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val c = (applicationContext as FamilyDocsApp).container
        val shareOnly = inputData.getBoolean(SHARE_ONLY, false)
        val backup = c.settings.value.backupEnabled && !shareOnly
        val sharing = c.repo.groupsNow().any { it.shareIndex != null || it.peerList.isNotEmpty() }
        if (!backup && !sharing) return Result.success()
        val auth = runCatching { c.backup.authorize() }.getOrElse { return Result.retry() }
        if (auth.hasResolution()) return Result.success() // needs the user; Settings shows "Back up now"
        val token = auth.accessToken ?: return Result.retry()
        // Shared groups: publish your share and read everyone else's (a failure here never fails the backup).
        if (sharing) runCatching { c.share.sync(token) }
        if (!backup) return Result.success()
        return runCatching { c.backup.backup(token, c.settings.value.viewableCopies) }
            .onSuccess { c.settings.update { s -> s.copy(lastBackup = System.currentTimeMillis()) } }
            .fold({ Result.success() }, { Result.retry() })
    }

    companion object {
        private const val NAME = "familydocs-drive-backup"
        private const val SHARE_ONLY = "share_only"

        /** At app start: read what the other members of shared groups changed (and publish yours). */
        fun syncShares(context: Context) {
            val req = androidx.work.OneTimeWorkRequestBuilder<BackupWorker>()
                .setInputData(androidx.work.workDataOf(SHARE_ONLY to true))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
            WorkManager.getInstance(context).enqueueUniqueWork("$NAME-share", androidx.work.ExistingWorkPolicy.KEEP, req)
        }

        /** One backup as soon as there's a connection (after sign-up, or before signing out). */
        fun runNow(context: Context) {
            val req = androidx.work.OneTimeWorkRequestBuilder<BackupWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
            WorkManager.getInstance(context).enqueueUniqueWork("$NAME-now", androidx.work.ExistingWorkPolicy.REPLACE, req)
        }
        /**
         * After a change: one backup ~2 minutes later. Each new change restarts the wait, so adding several
         * documents in a row ends in a single backup.
         */
        fun soon(context: Context) {
            val req = androidx.work.OneTimeWorkRequestBuilder<BackupWorker>()
                .setInitialDelay(2, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
            WorkManager.getInstance(context).enqueueUniqueWork("$NAME-soon", androidx.work.ExistingWorkPolicy.REPLACE, req)
        }

        /** Daily backup. UPDATE also moves installs that had the old weekly Wi-Fi-only schedule onto this one. */
        fun schedule(context: Context, enabled: Boolean) {
            val wm = WorkManager.getInstance(context)
            if (!enabled) { wm.cancelUniqueWork(NAME); wm.cancelUniqueWork("$NAME-soon"); return }
            val req = PeriodicWorkRequestBuilder<BackupWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresBatteryNotLow(true).build())
                .build()
            wm.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, req)
        }
    }
}
