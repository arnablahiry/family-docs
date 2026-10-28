package com.familydocs.app

import android.content.Context
import com.familydocs.app.auth.GoogleAccountHelper
import com.familydocs.app.backup.BackupKeyStore
import com.familydocs.app.backup.BackupWorker
import com.familydocs.app.backup.DriveBackup
import com.familydocs.app.data.AppDatabase
import com.familydocs.app.data.FileStore
import com.familydocs.app.data.VaultRepository
import com.familydocs.app.ocr.TextReader
import com.familydocs.app.security.AppLock
import com.familydocs.app.security.CryptoManager
import com.familydocs.app.security.PasscodeManager
import com.familydocs.app.security.SettingsStore

/** Hand-rolled dependency container (no DI framework needed at this size). */
class AppContainer(context: Context) {
    private val app = context.applicationContext
    val crypto = CryptoManager(app)
    val files = FileStore(app, crypto)
    val db: AppDatabase by lazy { AppDatabase.build(app, crypto.databasePassphrase()) }
    val repo: VaultRepository by lazy {
        // Any change to the vault: back up to Drive a couple of minutes later (when backup is on).
        VaultRepository(db.dao(), files).also { r -> r.onChanged = { if (settings.value.backupEnabled) BackupWorker.soon(app) } }
    }
    val passcode = PasscodeManager(app)
    val settings = SettingsStore(app)
    val appLock = AppLock(settings)
    val textReader = TextReader()
    val google = GoogleAccountHelper(app)
    /** Outlives any screen: sign out and delete account keep running after the vault UI disappears. */
    val appScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main.immediate)
    /** In-app updates from the GitHub releases (checked at app start; Settings › About shows and installs them). */
    val updater = com.familydocs.app.update.AppUpdater(app)
    val backup: DriveBackup by lazy { DriveBackup(app, repo, files, BackupKeyStore(app, crypto)) }

    /** Turns the daily Drive backup (plus one after each change) on or off; [now] also queues one right away. */
    fun setBackups(on: Boolean, now: Boolean = false) {
        settings.update { it.copy(backupEnabled = on) }
        BackupWorker.schedule(app, on)
        if (on && now) BackupWorker.runNow(app)
    }

    /** Sign out / delete account: forget Google, then remove everything Family Docs keeps on this phone. */
    suspend fun wipeThisPhone() {
        BackupWorker.schedule(app, false)
        androidx.work.WorkManager.getInstance(app).cancelAllWork() // event reminders too
        runCatching { google.signOut() }
        backup.keys.clear()
        repo.wipe()
        settings.reset()
        passcode.clear() // last: this is what switches the app back to the welcome screen
    }
}
