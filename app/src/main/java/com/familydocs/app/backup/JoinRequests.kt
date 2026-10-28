package com.familydocs.app.backup

import android.content.Context
import com.familydocs.app.BuildConfig
import com.familydocs.app.data.GroupEntity
import com.familydocs.app.data.Peer
import com.familydocs.app.data.VaultDao
import com.familydocs.app.domain.GroupCodes
import com.familydocs.app.domain.GroupInvite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * "Request access" with just a group's code, approved in-app by any member. Uses a small Firebase project (Firestore +
 * anonymous sign-in, free plan) as a mailbox, through its REST API; it never sees anything readable (see [RequestCrypto]):
 *
 *   groups/{lookup}                 n = group name, sealed with the code     (written by members)
 *   groups/{lookup}/requests/{id}   pub = requester's public key, who = their name (sealed with the code), at, uid
 *                                   ok/no, epk + box = the invite sealed to the requester, by = approver's name
 *                                   back = the requester's own invite, sealed with the group key (members read it to add them)
 *
 * Members' phones check for requests at app start and every few hours, and notify. Off when the build has no Firebase
 * settings ([enabled]); joining with an invite works either way.
 */
class JoinRequests(private val context: Context, private val dao: VaultDao, private val share: GroupShare) {
    data class Incoming(val id: String, val name: String, val at: Long)
    data class Pending(val lookup: String, val id: String, val code: String, val group: String, val priv: String)

    val enabled: Boolean get() = BuildConfig.FIREBASE_API_KEY.isNotBlank() && BuildConfig.FIREBASE_PROJECT.isNotBlank()
    /** Requests waiting for you, by group id (for the Home banner and Group settings). */
    val incoming = MutableStateFlow<Map<String, List<Incoming>>>(emptyMap())

    private val prefs = context.getSharedPreferences("join_requests", Context.MODE_PRIVATE)
    private val http = OkHttpClient.Builder().readTimeout(30, TimeUnit.SECONDS).build()
    private val json = "application/json; charset=UTF-8".toMediaType()
    private val base get() = "https://firestore.googleapis.com/v1/projects/${BuildConfig.FIREBASE_PROJECT}/databases/(default)/documents"

    // ---------------- requester ----------------

    /** The group's name if a group with [code] exists (and has a member who turned requests on), else null. */
    suspend fun find(code: String): String? = withContext(Dispatchers.IO) {
        val c = GroupCodes.normalize(code)
        val doc = get("groups/${RequestCrypto.lookup(c)}") ?: return@withContext null
        doc.str("n")?.let { runCatching { RequestCrypto.openText(it, RequestCrypto.codeKey(c)) }.getOrNull() }
    }

    /** Sends a request to join the group with [code] as [myName]; remembered until a member answers. */
    suspend fun request(code: String, groupName: String, myName: String) = withContext(Dispatchers.IO) {
        val c = GroupCodes.normalize(code)
        val lookup = RequestCrypto.lookup(c)
        val keys = RequestCrypto.newKeys()
        val id = java.util.UUID.randomUUID().toString().replace("-", "").take(20)
        val uid = auth().second
        val fields = JSONObject()
            .put("pub", sv(RequestCrypto.b64(keys.public.encoded)))
            .put("who", sv(RequestCrypto.sealText(myName, RequestCrypto.codeKey(c))))
            .put("at", JSONObject().put("integerValue", System.currentTimeMillis().toString()))
            .put("uid", sv(uid))
        call(Request.Builder().url("$base/groups/$lookup/requests?documentId=$id").post(JSONObject().put("fields", fields).toString().toRequestBody(json)))
        savePending(pending() + Pending(lookup, id, c, groupName, RequestCrypto.b64(keys.private.encoded)))
    }

    fun pending(): List<Pending> = runCatching {
        val a = JSONArray(prefs.getString("mine", "[]"))
        (0 until a.length()).map { a.getJSONObject(it).let { o -> Pending(o.getString("l"), o.getString("i"), o.getString("c"), o.getString("g"), o.getString("k")) } }
    }.getOrDefault(emptyList())

    private fun savePending(list: List<Pending>) = prefs.edit().putString("mine", JSONArray(list.map {
        JSONObject().put("l", it.lookup).put("i", it.id).put("c", it.code).put("g", it.group).put("k", it.priv) }).toString()).apply()

    /**
     * Your own requests: approved ones are joined (and, once your share is in Drive, your invite goes back so every member
     * adds you); declined ones are forgotten. Returns what happened, for notifications.
     */
    suspend fun checkMine(accessToken: String?): List<String> = withContext(Dispatchers.IO) {
        val out = mutableListOf<String>()
        val keep = mutableListOf<Pending>()
        pending().forEach { p ->
            val doc = runCatching { get("groups/${p.lookup}/requests/${p.id}") }.getOrNull()
            when {
                doc == null -> keep += p // offline, or not answered yet
                doc.str("no") != null -> out += "Your request to join ${p.group} was declined"
                doc.str("box") != null -> {
                    val inv = runCatching { GroupInvite.find(RequestCrypto.openFrom(doc.str("epk")!!, doc.str("box")!!, RequestCrypto.privateOf(p.priv))) }.getOrNull()
                    if (inv == null) { keep += p; return@forEach }
                    val (g, _) = share.accept(accessToken, inv)
                    // Send your own invite back (needs your share in Drive): members read it and add you.
                    val mine = if (accessToken != null) runCatching { share.invite(accessToken, g.id) }.getOrNull() else null
                    if (mine != null) {
                        patch("groups/${p.lookup}/requests/${p.id}", JSONObject().put("back", sv(RequestCrypto.sealText(mine.encode(), share.groupKey(g)))))
                        out += "You joined ${g.name}"
                    } else { keep += p; out += "You joined ${g.name}" }
                }
                else -> keep += p
            }
        }
        savePending(keep)
        out
    }

    // ---------------- members ----------------

    /** Makes the group findable by its code (name sealed with the code). Once per group and code. */
    suspend fun announce(g: GroupEntity) = withContext(Dispatchers.IO) {
        val c = GroupCodes.normalize(g.code)
        val tag = "announced:${g.id}:${RequestCrypto.lookup(c)}:${g.name}"
        if (prefs.getBoolean(tag, false)) return@withContext
        patch("groups/${RequestCrypto.lookup(c)}", JSONObject().put("n", sv(RequestCrypto.sealText(g.name, RequestCrypto.codeKey(c)))))
        prefs.edit().putBoolean(tag, true).apply()
    }

    /**
     * Requests for your groups: waiting ones fill [incoming] (returned as new ones to notify about); answered ones whose
     * requester sent their invite back are added to the group, so you see their documents too.
     */
    suspend fun checkGroups(): List<Pair<GroupEntity, Incoming>> = withContext(Dispatchers.IO) {
        val fresh = mutableListOf<Pair<GroupEntity, Incoming>>()
        val all = mutableMapOf<String, List<Incoming>>()
        dao.groupsNow().forEach { g ->
            runCatching { announce(g) }
            val c = GroupCodes.normalize(g.code)
            val docs = runCatching { list("groups/${RequestCrypto.lookup(c)}/requests") }.getOrDefault(emptyList())
            val waiting = mutableListOf<Incoming>()
            docs.forEach { (id, d) ->
                val back = d.str("back")
                when {
                    back != null -> runCatching {
                        val inv = GroupInvite.find(RequestCrypto.openText(back, share.groupKey(g))) ?: return@runCatching
                        val now = dao.groupNow(g.id) ?: return@runCatching
                        if (inv.groupId == g.id && now.peerList.none { it.key == inv.from }) share.accept(null, inv)
                    }
                    d.str("ok") == null && d.str("no") == null -> {
                        val name = d.str("who")?.let { runCatching { RequestCrypto.openText(it, RequestCrypto.codeKey(c)) }.getOrNull() } ?: "Someone"
                        val r = Incoming(id, name, d.optJSONObject("fields")?.optJSONObject("at")?.optString("integerValue")?.toLongOrNull() ?: 0L)
                        waiting += r
                        if (!prefs.getBoolean("seen:$id", false)) { fresh += g to r; prefs.edit().putBoolean("seen:$id", true).apply() }
                    }
                }
            }
            if (waiting.isNotEmpty()) all[g.id] = waiting
        }
        incoming.value = all
        fresh
    }

    /** Approves [r]: your invite for the group, sealed to the requester's key. Needs your share in Drive ([accessToken]). */
    suspend fun approve(accessToken: String, g: GroupEntity, r: Incoming) = withContext(Dispatchers.IO) {
        val c = GroupCodes.normalize(g.code)
        val path = "groups/${RequestCrypto.lookup(c)}/requests/${r.id}"
        val doc = get(path) ?: error("That request is gone")
        val pub = RequestCrypto.publicOf(doc.str("pub") ?: error("That request is incomplete"))
        val inv = share.invite(accessToken, g.id)
        val (epk, box) = RequestCrypto.sealTo(inv.encode(), pub)
        val me = dao.accountNow()?.name ?: "A member"
        patch(path, JSONObject().put("ok", sv("1")).put("epk", sv(epk)).put("box", sv(box)).put("by", sv(RequestCrypto.sealText(me, RequestCrypto.codeKey(c)))))
        drop(g.id, r.id)
    }

    suspend fun decline(g: GroupEntity, r: Incoming) = withContext(Dispatchers.IO) {
        patch("groups/${RequestCrypto.lookup(GroupCodes.normalize(g.code))}/requests/${r.id}", JSONObject().put("no", sv("1")))
        drop(g.id, r.id)
    }

    private fun drop(groupId: String, id: String) {
        incoming.value = incoming.value.toMutableMap().apply { this[groupId] = this[groupId].orEmpty().filter { it.id != id }; if (this[groupId].isNullOrEmpty()) remove(groupId) }
    }

    // ---------------- Firebase REST ----------------

    /** Anonymous Firebase sign-in, kept on the phone: (ID token, user id). */
    private fun auth(): Pair<String, String> {
        val key = BuildConfig.FIREBASE_API_KEY
        val token = prefs.getString("id_token", null); val exp = prefs.getLong("id_exp", 0L); val uid = prefs.getString("uid", null)
        if (token != null && uid != null && System.currentTimeMillis() < exp - 60_000) return token to uid
        val refresh = prefs.getString("refresh", null)
        val j = if (refresh != null) runCatching {
            JSONObject(raw(Request.Builder().url("https://securetoken.googleapis.com/v1/token?key=$key")
                .post("grant_type=refresh_token&refresh_token=$refresh".toRequestBody("application/x-www-form-urlencoded".toMediaType()))))
                .let { JSONObject().put("idToken", it.getString("id_token")).put("refreshToken", it.getString("refresh_token")).put("localId", it.getString("user_id")).put("expiresIn", it.getString("expires_in")) }
        }.getOrNull() else null
        val signed = j ?: JSONObject(raw(Request.Builder().url("https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=$key")
            .post(JSONObject().put("returnSecureToken", true).toString().toRequestBody(json))))
        prefs.edit().putString("id_token", signed.getString("idToken")).putString("refresh", signed.getString("refreshToken"))
            .putString("uid", signed.getString("localId")).putLong("id_exp", System.currentTimeMillis() + signed.getString("expiresIn").toLong() * 1000).apply()
        return signed.getString("idToken") to signed.getString("localId")
    }

    private fun raw(req: Request.Builder): String = http.newCall(req.build()).execute().use { r ->
        val body = r.body?.string().orEmpty(); check(r.isSuccessful) { "Request server ${r.code}: ${body.take(160)}" }; body }

    private fun call(req: Request.Builder): String = raw(req.header("Authorization", "Bearer ${auth().first}"))

    /** A document's JSON, or null if it doesn't exist. */
    private fun get(path: String): JSONObject? = http.newCall(Request.Builder().url("$base/$path").header("Authorization", "Bearer ${auth().first}").build()).execute().use { r ->
        if (r.code == 404) return null
        val body = r.body?.string().orEmpty(); check(r.isSuccessful) { "Request server ${r.code}: ${body.take(160)}" }; JSONObject(body)
    }

    private fun list(path: String): List<Pair<String, JSONObject>> {
        val j = JSONObject(call(Request.Builder().url("$base/$path?pageSize=100")))
        val a = j.optJSONArray("documents") ?: return emptyList()
        return (0 until a.length()).map { a.getJSONObject(it) }.map { it.getString("name").substringAfterLast('/') to it }
    }

    /** Creates or updates only the given fields. */
    private fun patch(path: String, fields: JSONObject) {
        val mask = fields.keys().asSequence().joinToString("&") { "updateMask.fieldPaths=$it" }
        call(Request.Builder().url("$base/$path?$mask").patch(JSONObject().put("fields", fields).toString().toRequestBody(json)))
    }

    private fun sv(s: String) = JSONObject().put("stringValue", s)
    private fun JSONObject.str(k: String): String? = optJSONObject("fields")?.optJSONObject(k)?.optString("stringValue")?.ifEmpty { null }

    companion object {
        /** Security rules to paste in Firebase › Firestore › Rules (also in README). */
        /** The Firestore security rules are in README › Join requests. */
        const val README_SECTION = "Join requests"
    }
}
