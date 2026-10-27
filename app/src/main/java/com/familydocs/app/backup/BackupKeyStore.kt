package com.familydocs.app.backup

import android.content.Context
import android.util.Base64
import com.familydocs.app.security.CryptoManager

/**
 * Keeps the PBKDF2-derived backup key (wrapped by the Keystore) so weekly backups run without asking for
 * the password. The salt is stored alongside and written into every backup file.
 */
class BackupKeyStore(context: Context, private val crypto: CryptoManager) {
    private val prefs = context.getSharedPreferences("backup", Context.MODE_PRIVATE)

    val hasPassword: Boolean get() = prefs.contains("salt") && crypto.readSecret("backup_key") != null

    fun setPassword(password: CharArray) {
        val salt = BackupCrypto.newSalt()
        crypto.storeSecret("backup_key", BackupCrypto.deriveKey(password, salt))
        prefs.edit().putString("salt", Base64.encodeToString(salt, Base64.NO_WRAP)).apply()
    }

    fun key(): ByteArray = crypto.readSecret("backup_key") ?: error("Backup password not set")
    fun salt(): ByteArray = Base64.decode(prefs.getString("salt", null) ?: error("Backup password not set"), Base64.NO_WRAP)
}
