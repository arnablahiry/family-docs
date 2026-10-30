package com.familydocs.app.backup

import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.KeyAgreement

/**
 * Each phone's key pair (P-256) and sealing to it (pure JVM, unit-tested): the Common logins in a group share are
 * sealed to every member's phone key, so the group code alone can't open them.
 */
object PhoneCrypto {
    fun b64(b: ByteArray): String = java.util.Base64.getEncoder().encodeToString(b)
    fun unb64(s: String): ByteArray = java.util.Base64.getDecoder().decode(s)

    private fun sealText(text: String, key: ByteArray): String = b64(BackupCrypto.seal(text.toByteArray(), key, ByteArray(16)))
    private fun openText(sealed: String, key: ByteArray): String = String(BackupCrypto.open(unb64(sealed), key))

    fun newKeys(): KeyPair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
    fun publicOf(b64: String): PublicKey = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(unb64(b64)))
    fun privateOf(b64: String): PrivateKey = KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(unb64(b64)))

    private fun agree(mine: PrivateKey, theirs: PublicKey): ByteArray =
        KeyAgreement.getInstance("ECDH").run { init(mine); doPhase(theirs, true); MessageDigest.getInstance("SHA-256").digest(generateSecret()) }

    /** Seals [text] so only the holder of [to]'s private key can open it. Returns (ephemeral public key, box). */
    fun sealTo(text: String, to: PublicKey): Pair<String, String> {
        val eph = newKeys()
        return b64(eph.public.encoded) to sealText(text, agree(eph.private, to))
    }

    fun openFrom(ephemeral: String, box: String, mine: PrivateKey): String = openText(box, agree(mine, publicOf(ephemeral)))
}
