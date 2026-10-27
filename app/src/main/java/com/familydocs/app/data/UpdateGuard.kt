package com.familydocs.app.data

import android.content.Context
import android.os.Build
import java.io.File

/**
 * Runs once per app update, before the database is opened:
 * keeps a copy of the (already encrypted) database and its journal, so even a broken migration
 * can't lose anything. The last 3 copies are kept in app-private storage.
 */
object UpdateGuard {
    fun beforeOpen(context: Context) {
        val prefs = context.getSharedPreferences("update_guard", Context.MODE_PRIVATE)
        val current = versionCode(context)
        val last = prefs.getLong("last_version", -1)
        if (last != -1L && last != current) snapshot(context, last)
        if (last != current) prefs.edit().putLong("last_version", current).apply()
    }

    private fun snapshot(context: Context, fromVersion: Long) {
        val db = context.getDatabasePath("vault.db")
        if (!db.exists()) return
        val dir = File(context.noBackupFilesDir, "db-snapshots").apply { mkdirs() }
        val stamp = "v$fromVersion-${System.currentTimeMillis()}"
        listOf("", "-wal", "-shm").forEach { suffix ->
            File(db.path + suffix).takeIf { it.exists() }?.copyTo(File(dir, "vault.db$suffix.$stamp"), overwrite = true)
        }
        dir.listFiles { f -> f.name.startsWith("vault.db.") }?.sortedByDescending { it.lastModified() }?.drop(3)?.forEach { old ->
            val tag = old.name.removePrefix("vault.db.")
            dir.listFiles { f -> f.name.endsWith(tag) }?.forEach { it.delete() }
        }
    }

    private fun versionCode(context: Context): Long {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
    }

}
