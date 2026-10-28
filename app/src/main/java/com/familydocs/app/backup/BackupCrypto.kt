package com.familydocs.app.backup

import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Backup file format (all of it is what lands on Google Drive):
 *
 *   "KFB1" | salt (16) | iv (12) | AES-256-GCM(ciphertext + tag)
 *
 * The key comes from the user's backup password via PBKDF2-HMAC-SHA256, so a backup can be restored
 * on a new phone (the Android Keystore key never leaves the old one). Pure JVM: unit-tested.
 */
object BackupCrypto {
    private val MAGIC = "KFB1".toByteArray()
    const val ITERATIONS = 210_000
    private val random = SecureRandom()

    fun newSalt(): ByteArray = ByteArray(16).also { random.nextBytes(it) }

    fun deriveKey(password: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password, salt, ITERATIONS, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    fun seal(plain: ByteArray, key: ByteArray, salt: ByteArray): ByteArray {
        val iv = ByteArray(12).also { random.nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        c.updateAAD(MAGIC)
        val ct = c.doFinal(plain)
        return ByteBuffer.allocate(4 + 16 + 12 + ct.size).put(MAGIC).put(salt).put(iv).put(ct).array()
    }

    /** Salt is stored in the clear so a restore can re-derive the key from the password. */
    fun saltOf(sealed: ByteArray): ByteArray {
        require(sealed.size > 32 && sealed.copyOfRange(0, 4).contentEquals(MAGIC)) { "Not a Lararium backup" }
        return sealed.copyOfRange(4, 20)
    }

    /** Throws [javax.crypto.AEADBadTagException] when the password is wrong or the file was altered. */
    fun open(sealed: ByteArray, key: ByteArray): ByteArray {
        saltOf(sealed)
        val iv = sealed.copyOfRange(20, 32)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        c.updateAAD(MAGIC)
        return c.doFinal(sealed, 32, sealed.size - 32)
    }

    fun open(sealed: ByteArray, password: CharArray): ByteArray = open(sealed, deriveKey(password, saltOf(sealed)))
}
