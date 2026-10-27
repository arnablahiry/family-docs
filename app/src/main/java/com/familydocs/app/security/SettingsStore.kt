package com.familydocs.app.security

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class VaultView { CARDS, LIST, ICONS }

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class Settings(
    val biometric: Boolean = false,
    val autoLockMs: Long = 60_000,
    val blockScreenshots: Boolean = true,
    val backupEnabled: Boolean = false,
    val lastBackup: Long = 0,
    val vaultView: VaultView = VaultView.CARDS,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    /** Also keep unencrypted copies of scans in Drive so they can be opened there. Off by default. */
    val viewableCopies: Boolean = false,
)

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<Settings> = _state.asStateFlow()
    val value: Settings get() = _state.value

    private fun read() = Settings(
        biometric = prefs.getBoolean("biometric", false),
        autoLockMs = prefs.getLong("autolock", 60_000),
        blockScreenshots = prefs.getBoolean("shots", true),
        backupEnabled = prefs.getBoolean("backup", false),
        lastBackup = prefs.getLong("last_backup", 0),
        vaultView = runCatching { VaultView.valueOf(prefs.getString("view", "CARDS")!!) }.getOrDefault(VaultView.CARDS),
        theme = runCatching { ThemeMode.valueOf(prefs.getString("theme", "SYSTEM")!!) }.getOrDefault(ThemeMode.SYSTEM),
        viewableCopies = prefs.getBoolean("viewable_copies", false),
    )

    fun update(block: (Settings) -> Settings) {
        val s = block(_state.value)
        prefs.edit()
            .putBoolean("biometric", s.biometric).putLong("autolock", s.autoLockMs).putBoolean("shots", s.blockScreenshots)
            .putBoolean("backup", s.backupEnabled).putLong("last_backup", s.lastBackup).putString("view", s.vaultView.name).putString("theme", s.theme.name).putBoolean("viewable_copies", s.viewableCopies)
            .apply()
        _state.value = s
    }

    companion object {
        val AUTO_LOCK_OPTIONS = listOf(0L to "Immediately", 60_000L to "1 min", 300_000L to "5 min", 900_000L to "15 min")
        fun autoLockLabel(ms: Long) = AUTO_LOCK_OPTIONS.firstOrNull { it.first == ms }?.second ?: "${ms / 60_000} min"
    }
}
