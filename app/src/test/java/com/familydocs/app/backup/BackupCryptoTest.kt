package com.familydocs.app.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCryptoTest {
    @Test fun roundTrip_withPassword() {
        val salt = BackupCrypto.newSalt()
        val key = BackupCrypto.deriveKey("correct horse".toCharArray(), salt)
        val plain = "family vault ".repeat(500).toByteArray()
        val sealed = BackupCrypto.seal(plain, key, salt)
        assertArrayEquals(plain, BackupCrypto.open(sealed, "correct horse".toCharArray()))
    }

    @Test fun wrongPassword_fails() {
        val salt = BackupCrypto.newSalt()
        val sealed = BackupCrypto.seal(byteArrayOf(1, 2, 3), BackupCrypto.deriveKey("right".toCharArray(), salt), salt)
        val failed = runCatching { BackupCrypto.open(sealed, "wrong".toCharArray()) }.isFailure
        assertTrue(failed)
    }

    @Test fun tampering_fails() {
        val salt = BackupCrypto.newSalt()
        val key = BackupCrypto.deriveKey("pw".toCharArray(), salt)
        val sealed = BackupCrypto.seal(ByteArray(64) { it.toByte() }, key, salt)
        sealed[sealed.size - 5] = (sealed[sealed.size - 5].toInt() xor 1).toByte()
        assertTrue(runCatching { BackupCrypto.open(sealed, key) }.isFailure)
    }
}
