package com.familydocs.app.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256-GCM with a non-exportable key in the Android Keystore.
 * Encrypts document files, photos, the SQLCipher passphrase and the backup key.
 * Blob layout: ivLen (1) | iv | ciphertext+tag
 */
class CryptoManager(context: Context) {
    private val prefs = context.getSharedPreferences("crypto", Context.MODE_PRIVATE)
    private val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }

    @Synchronized
    private fun key(): SecretKey {
        (keyStore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    fun encrypt(plain: ByteArray): ByteArray {
        val c = Cipher.getInstance(TRANSFORM)
        c.init(Cipher.ENCRYPT_MODE, key())
        val iv = c.iv
        return byteArrayOf(iv.size.toByte()) + iv + c.doFinal(plain)
    }

    fun decrypt(blob: ByteArray): ByteArray {
        val ivLen = blob[0].toInt()
        val c = Cipher.getInstance(TRANSFORM)
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, blob, 1, ivLen))
        return c.doFinal(blob, 1 + ivLen, blob.size - 1 - ivLen)
    }

    /** Random 32-byte secret, created once, stored only in encrypted form. */
    fun secret(name: String): ByteArray {
        prefs.getString(name, null)?.let { return decrypt(Base64.decode(it, Base64.NO_WRAP)) }
        val s = ByteArray(32).also { SecureRandom().nextBytes(it) }
        storeSecret(name, s)
        return s
    }

    fun storeSecret(name: String, value: ByteArray) {
        prefs.edit().putString(name, Base64.encodeToString(encrypt(value), Base64.NO_WRAP)).apply()
    }

    fun readSecret(name: String): ByteArray? =
        prefs.getString(name, null)?.let { decrypt(Base64.decode(it, Base64.NO_WRAP)) }

    fun databasePassphrase(): ByteArray = secret("db_passphrase")

    companion object {
        private const val KEYSTORE = "AndroidKeyStore"
        private const val ALIAS = "familydocs_master"
        private const val TRANSFORM = "AES/GCM/NoPadding"
    }
}
