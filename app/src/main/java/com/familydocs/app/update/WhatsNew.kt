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
        // Every version since the one you had, so skipped updates are covered too. Unknown (updating from a version
        // before this note existed): everything since then.
        return Changelog.between(all, seen ?: BEFORE_NOTES, current)
    }

    fun dismiss() { prefs.edit().putString(SEEN, current).apply(); _entries.value = emptyList() }

    private companion object {
        const val SEEN = "seen"
        /** The last version without this note: anyone updating with nothing recorded came from it or older. */
        const val BEFORE_NOTES = "0.8.3"
    }
}
