package com.familydocs.app.domain

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.UUID

/**
 * Invites for sharing a group between accounts. Pure Kotlin/JVM (unit-tested).
 *
 * An invite is one short line, "LR." + base64url of a few packed bytes: the group's id and code, and where the sender's
 * encrypted share lives in their Drive (its index file id). Everything else (the group's name, who sent it) is read from
 * that share once it's opened with the code, so the invite stays around 70 characters. Pasting it into Join group adds
 * the group and starts reading the sender's share; the joiner sends their own invite back, so the sender reads theirs.
 * Whoever has the invite can read the shares, so it's meant to be sent privately.
 *
 * Older "FD1." invites (JSON, with names) still work.
 */
data class GroupInvite(
    val groupId: String,
    val code: String,
    /** Drive file id of the sender's encrypted index. */
    val index: String,
    /** The group's name, the sender's key in the group (their own "you" id) and their name: empty until read from their share. */
    val name: String = "",
    val from: String = "",
    val fromName: String = "",
) {
    /** Whether the names are known (a short invite learns them from the share). */
    val complete: Boolean get() = from.isNotEmpty()

    fun encode(): String = PREFIX + b64(pack())

    /** What gets sent through WhatsApp, Messages…: a sentence for the person and the invite itself. */
    fun message(): String = "$fromName invited you to the group \"$name\" in Lararium. In the app: Join group → paste this.\n\n${encode()}"

    private fun pack(): ByteArray {
        val out = ByteArrayOutputStream()
        val c = GroupCodes.normalize(code)
        val packedCode = c.length == GroupCodes.LENGTH && c.all { it in GroupCodes.CHARS }
        val uuid = runCatching { UUID.fromString(groupId) }.getOrNull()?.takeIf { it.toString() == groupId.lowercase() }
        val packedIndex = index.isNotEmpty() && index.length < 256 && index.all { it in B64 }
        out.write((if (uuid != null) 1 else 0) or (if (packedCode) 2 else 0) or (if (packedIndex) 4 else 0))
        if (uuid != null) out.write(ByteBuffer.allocate(16).putLong(uuid.mostSignificantBits).putLong(uuid.leastSignificantBits).array())
        else bytes(out, groupId.toByteArray())
        if (packedCode) out.write(fixed(c.fold(BigInteger.ZERO) { n, ch -> n * RADIX + BigInteger.valueOf(GroupCodes.CHARS.indexOf(ch).toLong()) }, CODE_BYTES))
        else bytes(out, code.toByteArray())
        if (packedIndex) { out.write(index.length); out.write(sixBits(index)) } else bytes(out, index.toByteArray())
        return out.toByteArray()
    }

    companion object {
        const val PREFIX = "LR."
        private const val OLD = "FD1."
        private val PATTERN = Regex("(?<![A-Za-z0-9])(LR\\.|FD1\\.)[A-Za-z0-9_-]{20,}")
        private const val B64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
        private val RADIX = BigInteger.valueOf(GroupCodes.CHARS.length.toLong())
        private const val CODE_BYTES = 6 // 38^9 < 2^48

        private fun b64(b: ByteArray) = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(b)

        private fun bytes(out: ByteArrayOutputStream, b: ByteArray) { out.write(b.size); out.write(b) }
        private fun fixed(n: BigInteger, size: Int): ByteArray {
            val raw = n.toByteArray().dropWhile { it == 0.toByte() }.toByteArray()
            return ByteArray(size - raw.size) + raw
        }
        private fun sixBits(s: String): ByteArray {
            val out = ByteArray((s.length * 6 + 7) / 8)
            s.forEachIndexed { i, ch -> val v = B64.indexOf(ch); for (b in 0 until 6) if (v shr (5 - b) and 1 == 1) { val bit = i * 6 + b; out[bit / 8] = (out[bit / 8].toInt() or (0x80 ushr (bit % 8))).toByte() } }
            return out
        }
        private fun fromSixBits(b: ByteArray, at: Int, count: Int): String = buildString {
            for (i in 0 until count) {
                var v = 0
                for (k in 0 until 6) { val bit = i * 6 + k; v = (v shl 1) or ((b[at + bit / 8].toInt() ushr (7 - bit % 8)) and 1) }
                append(B64[v])
            }
        }

        private fun unpack(b: ByteArray): GroupInvite {
            var at = 0
            fun take(n: Int): ByteArray { require(at + n <= b.size); return b.copyOfRange(at, at + n).also { at += n } }
            fun str() = String(take(b[at++].toInt() and 0xff))
            val flags = b[at++].toInt()
            val gid = if (flags and 1 != 0) ByteBuffer.wrap(take(16)).let { UUID(it.long, it.long).toString() } else str()
            val code = if (flags and 2 != 0) {
                var n = BigInteger(1, take(CODE_BYTES))
                val chars = CharArray(GroupCodes.LENGTH)
                for (i in chars.indices.reversed()) { val (q, r) = n.divideAndRemainder(RADIX); chars[i] = GroupCodes.CHARS[r.toInt()]; n = q }
                GroupCodes.pretty(String(chars))
            } else str()
            val index = if (flags and 4 != 0) {
                val count = b[at++].toInt() and 0xff
                val size = (count * 6 + 7) / 8
                require(at + size <= b.size)
                fromSixBits(b, at, count).also { at += size }
            } else str()
            require(at == b.size && index.isNotEmpty())
            return GroupInvite(gid, code, index)
        }

        /** Finds an invite anywhere in pasted text (the whole message or just the LR… part); null if there isn't a valid one. */
        fun find(text: String): GroupInvite? = PATTERN.find(text)?.value?.let { token ->
            runCatching {
                if (token.startsWith(OLD)) {
                    val j = JSONObject(String(java.util.Base64.getUrlDecoder().decode(token.removePrefix(OLD))))
                    GroupInvite(j.getString("g"), j.getString("c"), j.getString("i"), j.getString("n"), j.getString("k"), j.optString("m", "Someone"))
                } else unpack(java.util.Base64.getUrlDecoder().decode(token.removePrefix(PREFIX)))
            }.getOrNull()
        }

        /** Salt for the group key: fixed per group, so every member derives the same key from the code. */
        fun salt(groupId: String): ByteArray = MessageDigest.getInstance("SHA-256").digest("family-docs-group:$groupId".toByteArray()).copyOf(16)
    }
}
