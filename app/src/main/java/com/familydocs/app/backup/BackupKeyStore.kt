package com.familydocs.app.backup

import android.content.Context
import android.util.Base64
import com.familydocs.app.security.CryptoManager
import java.security.SecureRandom

/**
 * The backup key: 32 random bytes, kept on this phone wrapped by the Android Keystore, and a copy in the
 * Google account's hidden Drive app folder, so a new phone signed in to the same Google account can restore
 * without a password. [id] is written into every backup file (the format's 16-byte salt field).
 */
class BackupKeyStore(context: Context, private val crypto: CryptoManager) {
    data class Key(val key: ByteArray, val id: ByteArray)

    private val prefs = context.getSharedPreferences("backup", Context.MODE_PRIVATE)

    fun current(): Key? {
        val k = crypto.readSecret(SECRET) ?: return null
        val id = prefs.getString(ID, null)?.let { Base64.decode(it, Base64.NO_WRAP) } ?: return null
        return Key(k, id)
    }

    fun install(k: Key) {
        crypto.storeSecret(SECRET, k.key)
        prefs.edit().putString(ID, Base64.encodeToString(k.id, Base64.NO_WRAP)).apply()
    }

    fun generate(): Key = Key(ByteArray(32).also { random.nextBytes(it) }, ByteArray(16).also { random.nextBytes(it) }).also(::install)

    fun clear() { crypto.deleteSecret(SECRET); prefs.edit().clear().apply() }

    private companion object {
        const val SECRET = "backup_key_v2"; const val ID = "key_id"
        val random = SecureRandom()
    }
}
