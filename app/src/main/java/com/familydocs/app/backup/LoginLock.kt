package com.familydocs.app.backup

import android.content.Context
import android.util.Base64
import com.familydocs.app.security.CryptoManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * The backup password for logins (optional). When it's on, every Passwords file in the Drive backup (yours and the
 * Common vault's) is sealed a second time, with a key made from this password (PBKDF2, [BackupCrypto]), inside the
 * normal backup encryption. The password is never stored or uploaded: this phone keeps only the derived key, encrypted
 * with its Android Keystore key, so backups don't ask for it. On a new phone the logins come back only after the
 * password is entered; forgotten, they can't be recovered (the logins on the old phone are unaffected).
 */
class LoginLock(context: Context, private val crypto: CryptoManager) {
    private val prefs = context.getSharedPreferences("login_lock", Context.MODE_PRIVATE)

    val isOn: Boolean get() = prefs.contains(SALT)
    private val _on = MutableStateFlow(isOn)
    val on: StateFlow<Boolean> = _on
    /** Locked logins found by a restore, waiting for the password (JSON [{"member":…,"locked":…}]). */
    private val _waiting = MutableStateFlow(waitingList().isNotEmpty())
    val waiting: StateFlow<Boolean> = _waiting

    /** Called after it's turned on, changed or off (the next backup re-seals the Passwords files). */
    var onChanged: () -> Unit = {}

    /** Changes whenever the password does, so the backup knows the Passwords files must be sealed again. */
    fun stamp(): String = prefs.getString(CHECK, null) ?: "off"

    /** Turns it on, or changes it. Slow (key derivation): call off the main thread. */
    fun set(password: CharArray) {
        val salt = BackupCrypto.newSalt()
        adopt(BackupCrypto.deriveKey(password, salt), salt)
        onChanged()
    }

    /** Whether [password] is the current one. Slow. */
    fun matches(password: CharArray): Boolean {
        val salt = prefs.getString(SALT, null)?.let { Base64.decode(it, Base64.NO_WRAP) } ?: return false
        return check(BackupCrypto.deriveKey(password, salt)) == prefs.getString(CHECK, null)
    }

    /** Turns it off: logins are then backed up with the normal encryption only. */
    fun turnOff() { crypto.deleteSecret(SECRET); prefs.edit().remove(SALT).remove(CHECK).commit(); _on.value = false; onChanged() }

    /** Sign out / delete account. */
    fun clear() { crypto.deleteSecret(SECRET); prefs.edit().clear().commit(); _on.value = false; _waiting.value = false }

    /** Seals a Passwords file's contents with the backup password's key, or null when it's off. */
    fun seal(plain: ByteArray): ByteArray? {
        val key = crypto.readSecret(SECRET) ?: return null
        val salt = prefs.getString(SALT, null)?.let { Base64.decode(it, Base64.NO_WRAP) } ?: return null
        return BackupCrypto.seal(plain, key, salt)
    }

    // ---- restoring ----

    /** A restore found logins sealed with the backup password: kept (still sealed) until it's entered. */
    fun keepWaiting(items: List<Pair<String, ByteArray>>) {
        prefs.edit().putString(WAITING, JSONArray(items.map { (m, b) -> JSONObject().put("member", m).put("locked", Base64.encodeToString(b, Base64.NO_WRAP)) }).toString()).commit()
        _waiting.value = items.isNotEmpty()
    }

    fun waitingList(): List<Pair<String, ByteArray>> = runCatching {
        val a = JSONArray(prefs.getString(WAITING, null) ?: return emptyList())
        (0 until a.length()).map { a.getJSONObject(it).let { o -> o.getString("member") to Base64.decode(o.getString("locked"), Base64.NO_WRAP) } }
    }.getOrDefault(emptyList())

    /**
     * Opens the waiting logins with [password] (throws when it's wrong) and keeps the password's key on this phone, so
     * later backups stay sealed with it. Returns the opened Passwords files' contents. Slow.
     */
    fun unlockWaiting(password: CharArray): List<String> {
        val items = waitingList()
        if (items.isEmpty()) return emptyList()
        val salt = BackupCrypto.saltOf(items.first().second)
        val key = BackupCrypto.deriveKey(password, salt)
        val out = items.map { (_, b) -> String(BackupCrypto.open(b, if (BackupCrypto.saltOf(b).contentEquals(salt)) key else BackupCrypto.deriveKey(password, BackupCrypto.saltOf(b)))) }
        adopt(key, salt)
        prefs.edit().remove(WAITING).commit(); _waiting.value = false
        return out
    }

    private fun adopt(key: ByteArray, salt: ByteArray) {
        crypto.storeSecret(SECRET, key)
        prefs.edit().putString(SALT, Base64.encodeToString(salt, Base64.NO_WRAP)).putString(CHECK, check(key)).commit()
        _on.value = true
    }

    private fun check(key: ByteArray): String =
        Base64.encodeToString(Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, "HmacSHA256")) }.doFinal("lararium-login-lock".toByteArray()), Base64.NO_WRAP)

    private companion object {
        const val SECRET = "login_lock_key"; const val SALT = "salt"; const val CHECK = "check"; const val WAITING = "waiting"
    }
}
