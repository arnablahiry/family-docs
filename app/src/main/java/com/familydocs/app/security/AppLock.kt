package com.familydocs.app.security

import android.os.SystemClock
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Locks the vault when the app has been in the background longer than the auto-lock setting.
 * Registered on ProcessLifecycleOwner. File/camera pickers call [expectExternalActivity] first so
 * choosing a photo doesn't bounce the user to the lock screen.
 */
class AppLock(private val settings: SettingsStore) : DefaultLifecycleObserver {
    private val _locked = MutableStateFlow(true)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()
    private var backgroundAt = 0L
    private var externalUntil = 0L

    private val _unlocking = MutableStateFlow(false)
    /** True while the unlock animation lifts the lock screen away (the vault underneath settles in). */
    val unlocking: StateFlow<Boolean> = _unlocking.asStateFlow()

    fun unlock() { _locked.value = false; _unlocking.value = false }
    fun lock() { _unlocking.value = false; _locked.value = true }
    fun beginUnlock() { _unlocking.value = true }

    fun expectExternalActivity(windowMs: Long = 5 * 60_000) {
        externalUntil = SystemClock.elapsedRealtime() + windowMs
    }

    override fun onStop(owner: LifecycleOwner) {
        backgroundAt = SystemClock.elapsedRealtime()
    }

    override fun onStart(owner: LifecycleOwner) {
        val now = SystemClock.elapsedRealtime()
        val away = now - backgroundAt
        if (backgroundAt != 0L && now > externalUntil && away >= settings.value.autoLockMs) lock()
        externalUntil = 0L
    }
}
