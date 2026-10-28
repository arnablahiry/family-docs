package com.familydocs.app.domain

import org.json.JSONObject
import java.security.MessageDigest

/**
 * Invites for sharing a group between accounts. Pure Kotlin/JVM (unit-tested).
 *
 * An invite is one line of text, "FD1." + base64url(JSON), carrying the group (id, name, code) and where the sender's
 * encrypted share lives in their Drive (the index file's id). Pasting it into Join group adds the group and starts reading
 * the sender's share; the joiner's app then offers its own invite to send back, so the sender reads theirs too.
 * Whoever has the invite can read the shares, so it's meant to be sent privately (like the code).
 */
data class GroupInvite(
    val groupId: String,
    val name: String,
    val code: String,
    /** Drive file id of the sender's encrypted index. */
    val index: String,
    /** The sender's key in the group (their own "you" id) and their name. */
    val from: String,
    val fromName: String,
) {
    fun encode(): String = PREFIX + b64(JSONObject().put("v", 1).put("g", groupId).put("n", name).put("c", code)
        .put("i", index).put("k", from).put("m", fromName).toString().toByteArray())

    /** What gets sent through WhatsApp, Messages…: a sentence for the person and the invite itself. */
    fun message(): String = "$fromName invited you to the group \"$name\" in Lararium. In the app: Join group → paste this whole message.\n\n${encode()}"

    companion object {
        const val PREFIX = "FD1."
        private val PATTERN = Regex("FD1\\.[A-Za-z0-9_-]{20,}")

        private fun b64(b: ByteArray) = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(b)

        /** Finds an invite anywhere in pasted text (the whole message or just the FD1… part); null if there isn't a valid one. */
        fun find(text: String): GroupInvite? = PATTERN.find(text)?.value?.let { token ->
            runCatching {
                val j = JSONObject(String(java.util.Base64.getUrlDecoder().decode(token.removePrefix(PREFIX))))
                GroupInvite(j.getString("g"), j.getString("n"), j.getString("c"), j.getString("i"), j.getString("k"), j.optString("m", "Someone"))
            }.getOrNull()
        }

        /** Salt for the group key: fixed per group, so every member derives the same key from the code. */
        fun salt(groupId: String): ByteArray = MessageDigest.getInstance("SHA-256").digest("family-docs-group:$groupId".toByteArray()).copyOf(16)
    }
}
