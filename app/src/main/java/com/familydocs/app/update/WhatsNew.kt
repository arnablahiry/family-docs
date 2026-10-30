package com.familydocs.app.update

import android.content.Context
import com.familydocs.app.domain.Changelog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The "what's new" note on Home: shown the first time the app opens after an update, until it's closed. A fresh
 * install starts with nothing to show.
 */
class WhatsNew(context: Context) {
    private val prefs = context.getSharedPreferences("whats_new", Context.MODE_PRIVATE)
    private val current: String = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()

    private val _entries = MutableStateFlow(compute(context))
    val entries: StateFlow<List<Changelog.Entry>> = _entries

    private fun compute(context: Context): List<Changelog.Entry> {
        if (current.isEmpty()) return emptyList()
        val seen = prefs.getString(SEEN, null)
        if (seen == null) {
            // First version with this note: an update shows this version's notes, a fresh install shows nothing.
            val info = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
            if (info == null || info.firstInstallTime == info.lastUpdateTime) { prefs.edit().putString(SEEN, current).apply(); return emptyList() }
        }
        if (seen == current) return emptyList()
        val all = runCatching { Changelog.parse(context.assets.open("changelog.txt").bufferedReader().use { it.readText() }) }.getOrDefault(emptyList())
        // unknown last version (updating to the first one with this note): just this version's changes
        return Changelog.between(all, seen, current).let { if (seen == null) it.take(1) else it }
    }

    fun dismiss() { prefs.edit().putString(SEEN, current).apply(); _entries.value = emptyList() }

    private companion object { const val SEEN = "seen" }
}
