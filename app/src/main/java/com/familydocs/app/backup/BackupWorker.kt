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
        val asking = c.requests.enabled && (c.requests.pending().isNotEmpty() || c.repo.groupsNow().isNotEmpty())
        // Join requests for your groups (and answers to yours): checked first, they don't need Google Drive.
        if (asking) runCatching { c.requests.checkGroups() }.onSuccess { fresh ->
            fresh.forEach { (g, r) -> GroupNotices.show(applicationContext, "${r.name} asked to join ${g.name}", "Open Group settings to approve or decline", r.id.hashCode()) }
        }
        if (!backup && !sharing && !(asking && c.requests.pending().isNotEmpty())) return Result.success()
        val auth = runCatching { c.backup.authorize() }.getOrElse { return Result.retry() }
        if (auth.hasResolution()) return Result.success() // needs the user; Settings shows "Back up now"
        val token = auth.accessToken ?: return Result.retry()
        // Your own requests: join the groups that approved you (and send your invite back to them).
        if (asking && c.requests.pending().isNotEmpty()) runCatching { c.requests.checkMine(token) }.onSuccess { msgs ->
            msgs.forEach { GroupNotices.show(applicationContext, it, "Lararium", it.hashCode()) }
        }
        // Shared groups: publish your share and read everyone else's (a failure here never fails the backup).
        if (sharing || asking) runCatching { c.share.sync(token) }
        // Groups you joined with an invite: your share goes back to everyone in them automatically.
        if (c.requests.enabled && sharing) runCatching { c.requests.sendBack(token) }
        if (!backup) return Result.success()
        return runCatching { c.backup.backup(token, c.settings.value.viewableCopies) }
            .onSuccess { c.settings.update { s -> s.copy(lastBackup = System.currentTimeMillis()) } }
            .fold({ Result.success() }, { Result.retry() })
    }

    companion object {
        private const val NAME = "familydocs-drive-backup"
        private const val SHARE_ONLY = "share_only"

        /** Every few hours: join requests and shared groups (only does anything when those are in use). */
        fun scheduleChecks(context: Context) {
            val req = PeriodicWorkRequestBuilder<BackupWorker>(3, TimeUnit.HOURS)
                .setInputData(androidx.work.workDataOf(SHARE_ONLY to true))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("$NAME-checks", ExistingPeriodicWorkPolicy.KEEP, req)
        }

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
