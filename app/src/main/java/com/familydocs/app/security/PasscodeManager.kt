package com.familydocs.app.security

import android.content.Context
import android.os.SystemClock
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Custom 4/6-digit passcode stored as a salted PBKDF2 hash, with escalating lockout after 5 wrong tries. */
class PasscodeManager(context: Context) {
    private val prefs = context.getSharedPreferences("passcode", Context.MODE_PRIVATE)

    sealed interface Check {
        data object Ok : Check
        data class Wrong(val triesLeft: Int) : Check
        data class LockedOut(val millisLeft: Long) : Check
    }

    val isSet: Boolean get() = prefs.contains(HASH)
    val length: Int get() = prefs.getInt(LEN, 6)

    fun set(code: String) {
        require(code.length in 4..6 && code.all(Char::isDigit))
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString(SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(HASH, Base64.encodeToString(hash(code, salt), Base64.NO_WRAP))
            .putInt(LEN, code.length)
            .putInt(FAILS, 0).putLong(UNTIL, 0)
            .apply()
    }

    fun lockoutLeft(): Long {
        val until = prefs.getLong(UNTIL, 0)
        val now = SystemClock.elapsedRealtime()
        return if (until > now) until - now else 0
    }

    fun check(code: String): Check {
        lockoutLeft().takeIf { it > 0 }?.let { return Check.LockedOut(it) }
        val salt = Base64.decode(prefs.getString(SALT, "") ?: "", Base64.NO_WRAP)
        val expected = Base64.decode(prefs.getString(HASH, "") ?: "", Base64.NO_WRAP)
        if (MessageDigest.isEqual(hash(code, salt), expected)) {
            prefs.edit().putInt(FAILS, 0).putLong(UNTIL, 0).apply()
            return Check.Ok
        }
        val fails = prefs.getInt(FAILS, 0) + 1
        val edit = prefs.edit().putInt(FAILS, fails)
        if (fails % MAX_TRIES == 0) {
            // 30 s, then 60 s, 120 s … per block of five wrong tries
            val wait = 30_000L shl ((fails / MAX_TRIES) - 1).coerceAtMost(6)
            edit.putLong(UNTIL, SystemClock.elapsedRealtime() + wait).apply()
            return Check.LockedOut(wait)
        }
        edit.apply()
        return Check.Wrong(MAX_TRIES - fails % MAX_TRIES)
    }

    private fun hash(code: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(code.toCharArray(), salt, 120_000, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded.also { spec.clearPassword() }
    }

    private companion object {
        const val HASH = "hash"; const val SALT = "salt"; const val LEN = "len"; const val FAILS = "fails"; const val UNTIL = "until"
        const val MAX_TRIES = 5
    }
}
