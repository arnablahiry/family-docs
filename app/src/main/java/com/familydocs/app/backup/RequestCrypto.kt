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
 * Crypto for join requests (pure JVM, unit-tested). Nothing readable is stored on the request server:
 *  - a group is found by [lookup] (a hash of its code) and its name is sealed with [codeKey], so only someone who
 *    already has the code can see which group it is;
 *  - the requester's name is sealed with [codeKey] too (members have the code);
 *  - the approval (the invite, with the share's Drive id) is sealed to the requester's own public key ([sealTo]),
 *    so another person who also knows the code can't read it.
 */
object RequestCrypto {
    private fun sha(s: String) = MessageDigest.getInstance("SHA-256").digest(s.toByteArray())
    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }
    fun b64(b: ByteArray): String = java.util.Base64.getEncoder().encodeToString(b)
    fun unb64(s: String): ByteArray = java.util.Base64.getDecoder().decode(s)

    /** Where the group lives on the request server: the same for everyone with the code, and says nothing about it. */
    fun lookup(normalizedCode: String): String = hex(sha("lararium-lookup:$normalizedCode")).take(40)

    private fun codeSalt(normalizedCode: String) = sha("lararium-code-salt:$normalizedCode").copyOf(16)
    fun codeKey(normalizedCode: String): ByteArray = BackupCrypto.deriveKey(normalizedCode.toCharArray(), codeSalt(normalizedCode))

    fun sealText(text: String, key: ByteArray): String = b64(BackupCrypto.seal(text.toByteArray(), key, ByteArray(16)))
    fun openText(sealed: String, key: ByteArray): String = String(BackupCrypto.open(unb64(sealed), key))

    // ---- requester's key pair (P-256) ----
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
