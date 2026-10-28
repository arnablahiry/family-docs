package com.familydocs.app.backup

import android.content.Context
import com.familydocs.app.data.Attachment
import com.familydocs.app.data.DisplayMode
import com.familydocs.app.data.DocumentEntity
import com.familydocs.app.data.FileStore
import com.familydocs.app.data.GroupEntity
import com.familydocs.app.data.MemberEntity
import com.familydocs.app.data.PasswordEntity
import com.familydocs.app.data.Peer
import com.familydocs.app.data.VaultDao
import com.familydocs.app.domain.GroupCodes
import com.familydocs.app.domain.GroupInvite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Sharing a group between accounts, with no server: every member's app keeps an encrypted "share" in its own Drive
 * and reads the others'.
 *
 *   My Drive › Family Docs › <Group> › Shared with <Group> (encrypted)/
 *       index.fdx          ← people, documents, Common logins (JSON), sealed
 *       <file id>.fdx      ← each scan / page / picture, sealed
 *
 * What goes in: you, the Common vault and the people you added to the group, with the documents added on this phone.
 * Your own logins never do; Common logins do. Things other members shared with you are never passed on.
 * Sealing: AES-256-GCM ([BackupCrypto]) with a key derived from the group code (PBKDF2, salt from the group id), so
 * only someone with the code can open it. The files are "anyone with the link" because the app's Drive access
 * (drive.file) can't open files other accounts share by email; the file ids travel only inside invites.
 * What others share shows up read-only here, marked with their key ([MemberEntity.origin] / [DocumentEntity.origin]).
 */
class GroupShare(private val context: Context, private val dao: VaultDao, private val files: FileStore) {
    private val prefs = context.getSharedPreferences("group_share", Context.MODE_PRIVATE)
    private val running = Mutex()

    /** Publishes your share of every shared group and reads everyone else's. Returns what changed, for the UI. */
    suspend fun sync(accessToken: String, only: String? = null, onProgress: (String) -> Unit = {}): String = running.withLock { withContext(Dispatchers.IO) {
        val drive = DriveClient(accessToken)
        val me = dao.selfNow() ?: error("No account on this phone")
        val groups = dao.groupsNow().filter { (only == null || it.id == only) && (it.shareIndex != null || it.peerList.isNotEmpty() || it.id == only) }
        var received = 0; val problems = mutableListOf<String>()
        groups.forEach { g0 ->
            onProgress("Sharing ${g0.name}…")
            val g = publish(drive, g0, me)
            g.peerList.forEach { p ->
                onProgress("Reading ${p.name}'s share…")
                runCatching { received += read(g, p, me.id) }.onFailure { problems += "${p.name}: ${it.message}" }
            }
        }
        if (problems.isEmpty()) "Group up to date" + if (received > 0) " · $received new or changed" else ""
        else "Some shares couldn't be read (${problems.joinToString("; ")})"
    } }

    /** The invite for group [groupId] (publishes your share first if it isn't in Drive yet). */
    suspend fun invite(accessToken: String, groupId: String): GroupInvite = withContext(Dispatchers.IO) {
        val me = dao.selfNow() ?: error("No account on this phone")
        val g0 = dao.groupNow(groupId) ?: error("Group not found")
        val g = if (g0.shareIndex != null) g0 else running.withLock { publish(DriveClient(accessToken), g0, me) }
        GroupInvite(g.id, g.name, g.code, g.shareIndex!!, me.id, dao.accountNow()?.name ?: me.name)
    }

    /**
     * Pasted invite: joins the group if it's new on this phone, or adds the sender's share to it (someone sending their
     * link back). Returns the group, and a problem to show if their share couldn't be read yet.
     */
    suspend fun accept(accessToken: String?, inv: GroupInvite): Pair<GroupEntity, String?> = withContext(Dispatchers.IO) {
        val me = dao.selfNow() ?: error("No account on this phone")
        require(inv.from != me.id) { "That's your own invite. Send it to the person you want to add." }
        val existing = dao.groupNow(inv.groupId)
        if (existing != null) require(GroupCodes.normalize(existing.code) == GroupCodes.normalize(inv.code)) { "This invite has a different code from your copy of ${existing.name}" }
        val base: GroupEntity = existing ?: GroupEntity(inv.groupId, inv.name, inv.code)
        if (dao.sharedIn(base.id) == null)
            dao.upsertMember(MemberEntity(java.util.UUID.randomUUID().toString(), "Common", "Shared", null, createdAt = 0L, shared = true, groupId = base.id))
        val peer = Peer(inv.from, inv.fromName, inv.index)
        val g: GroupEntity = base.copy(peers = Peer.encode(base.peerList.filter { it.key != peer.key } + peer))
        dao.upsertGroup(g)
        // Read it now (this also checks the invite works); if the connection fails, the next sync tries again.
        val problem = running.withLock {
            val err = runCatching { read(g, peer, me.id) }.exceptionOrNull()?.let { it.message ?: "Couldn't read the share yet" }
            // Publish yours too, so the invite you send back works right away.
            if (accessToken != null) runCatching { publish(DriveClient(accessToken), dao.groupNow(g.id)!!, me) }
            err
        }
        dao.groupNow(g.id)!! to problem
    }

    /** Stops reading [peerKey]'s share and removes what they shared from this phone. */
    suspend fun forget(groupId: String, peerKey: String) = withContext(Dispatchers.IO) {
        val g = dao.groupNow(groupId) ?: return@withContext
        removeFrom(g, peerKey, keepDocs = emptySet(), keepMembers = emptySet(), keepPws = emptySet())
        dao.upsertGroup(g.copy(peers = Peer.encode(g.peerList.filter { it.key != peerKey })))
    }

    // ---------------- publishing ----------------

    private fun key(g: GroupEntity) = BackupCrypto.deriveKey(GroupCodes.normalize(g.code).toCharArray(), GroupInvite.salt(g.id))

    private suspend fun publish(drive: DriveClient, g: GroupEntity, me: MemberEntity): GroupEntity {
        val key = key(g); val salt = GroupInvite.salt(g.id)
        fun seal(b: ByteArray) = BackupCrypto.seal(b, key, salt)
        val root = drive.folder("Family Docs", null, mapOf(DriveBackup.P_ROOT to "1"))
        val groupFolder = drive.folder(g.name, root, mapOf(DriveBackup.P_GROUP to g.id), "Family Docs group · its people, Common vault and events")
        val folder = drive.folder("Shared with ${g.name} (encrypted)", groupFolder, mapOf(P_SHARE to g.id),
            "Encrypted copy for the other members of ${g.name}. Only readable in Family Docs with the group's code.")

        val all = dao.membersNow()
        val common = all.firstOrNull { it.shared && it.groupId == g.id }
        val people = listOf(me) + listOfNotNull(common) + all.filter { it.groupId == g.id && !it.shared && !it.isSelf && it.origin == null }
        val ids = people.map { it.id }.toSet()
        val docs = dao.allDocumentsNow().filter { it.memberId in ids && it.origin == null && it.eventId == null }
        val pws = common?.let { c -> dao.allPasswordsNow().filter { it.memberId == c.id && it.origin == null } }.orEmpty()

        // Files are uploaded once (their ids never change: an edited scan is a new file), then just referenced.
        val live = mutableSetOf<String>()
        fun upload(fileId: String?): String? {
            fileId ?: return null
            val tag = "${g.id}:$fileId"
            prefs.getString(tag, null)?.let { live += tag; return it }
            val bytes = files.get(fileId) ?: return null
            val id = drive.upsertFile(folder, "$fileId.fdx", seal(bytes), OCTET, mapOf(P_SHARE_FILE to tag), null, P_SHARE_FILE)
            drive.shareWithLink(id)
            prefs.edit().putString(tag, id).apply(); live += tag
            return id
        }

        val index = JSONObject().put("v", 1).put("group", g.id).put("name", g.name)
            .put("from", JSONObject().put("key", me.id).put("name", dao.accountNow()?.name ?: me.name))
            .put("members", JSONArray(people.map { m ->
                JSONObject().put("id", m.id).put("name", if (m.isSelf) (dao.accountNow()?.name ?: m.name) else m.name).put("relation", m.relation)
                    .put("kind", when { m.isSelf -> "self"; m.shared -> "common"; else -> "person" })
                    .put("avatar", m.avatar ?: JSONObject.NULL).put("photo", upload(m.photoFile) ?: JSONObject.NULL)
            }))
            .put("documents", JSONArray(docs.map { d ->
                JSONObject().put("id", d.id).put("member", d.memberId).put("name", d.name).put("fields", d.fieldsJson).put("text", d.rawText)
                    .put("ocr", d.readByOcr).put("country", d.country ?: JSONObject.NULL).put("display", d.display.name)
                    .put("flag", d.showFlag ?: JSONObject.NULL).put("created", d.createdAt).put("updated", d.updatedAt).put("order", d.sortOrder ?: JSONObject.NULL)
                    .put("files", JSONArray(d.attachments.mapNotNull { a -> upload(a.id)?.let { JSONObject().put("f", it).put("mime", a.mime) } }))
                    .put("picture", upload(d.pictureFile) ?: JSONObject.NULL)
            }))
            .put("passwords", JSONArray(pws.map { JSONObject().put("id", it.id).put("site", it.site).put("user", it.username).put("pass", it.password).put("created", it.createdAt) }))
            // Who else you read: someone who joins through you finds the rest of the group from here.
            .put("peers", JSONArray(g.peerList.map { JSONObject().put("key", it.key).put("name", it.name).put("index", it.index) }))
        val text = index.toString()
        val hash = sha(text)
        var out = g
        if (g.shareIndex == null || prefs.getString("${g.id}:index-hash", null) != hash || g.shareFolder != folder) {
            val id = drive.upsertFile(folder, "index.fdx", seal(text.toByteArray()), OCTET, mapOf(P_SHARE_FILE to "${g.id}:index"), null, P_SHARE_FILE)
            if (g.shareIndex != id) drive.shareWithLink(id)
            out = g.copy(shareFolder = folder, shareIndex = id)
            dao.upsertGroup(out)
            prefs.edit().putString("${g.id}:index-hash", hash).apply()
            // Files nothing points to any more (deleted documents, replaced scans) leave Drive.
            drive.children(folder).filter { val t = it.props[P_SHARE_FILE]; t != null && t != "${g.id}:index" && t !in live }.forEach { drive.trash(it.id) }
            prefs.edit().apply { prefs.all.keys.filter { it.startsWith("${g.id}:") && !it.endsWith(":index-hash") && it !in live }.forEach { remove(it) } }.apply()
        }
        return out
    }

    // ---------------- reading ----------------

    /** Reads one member's share into this phone. Returns how many documents were new or changed. */
    private suspend fun read(g: GroupEntity, p: Peer, myKey: String): Int {
        val k = key(g)
        fun fetch(driveId: String) = fetchFile(driveId, k)
        val j = JSONObject(String(BackupCrypto.open(DriveClient.publicDownload(p.index), k)))
        require(j.getString("group") == g.id) { "This share belongs to another group" }
        val from = j.getJSONObject("from")
        val prefix = "s:${g.id}:${p.key}:"
        val common = dao.sharedIn(g.id)
        var changed = 0

        val memberIds = mutableMapOf<String, String>()
        val keepMembers = mutableSetOf<String>()
        j.getJSONArray("members").forEachObj { m ->
            val kind = m.optString("kind")
            if (kind == "common") { common?.let { memberIds[m.getString("id")] = it.id }; return@forEachObj }
            val id = prefix + m.getString("id")
            memberIds[m.getString("id")] = id; keepMembers += id
            val photo = m.optStringOrNull("photo")?.let { fetch(it) }
            dao.upsertMember(MemberEntity(id, m.getString("name"), if (kind == "self") "Group member" else m.optString("relation"), photo,
                createdAt = 1L, avatar = m.optStringOrNull("avatar"), groupId = g.id, origin = p.key))
        }

        val keepDocs = mutableSetOf<String>()
        j.getJSONArray("documents").forEachObj { d ->
            val member = memberIds[d.getString("member")] ?: return@forEachObj
            val id = prefix + d.getString("id"); keepDocs += id
            val existing = dao.documentNow(id)
            val atts = mutableListOf<Attachment>()
            d.getJSONArray("files").forEachObj { f -> fetch(f.getString("f"))?.let { atts += Attachment(it, f.optString("mime", "image/jpeg")) } }
            if (existing != null && existing.updatedAt == d.getLong("updated") && existing.attachments.map { it.id } == atts.map { it.id }) return@forEachObj
            dao.upsertDocument(DocumentEntity(
                id = id, memberId = member, name = d.getString("name"), fileId = atts.firstOrNull()?.id, fileMime = atts.firstOrNull()?.mime,
                display = runCatching { DisplayMode.valueOf(d.getString("display")) }.getOrDefault(DisplayMode.ICON),
                pictureFile = d.optStringOrNull("picture")?.let { fetch(it) }, fieldsJson = d.optString("fields", "[]"), rawText = d.optString("text"),
                readByOcr = d.optBoolean("ocr"), country = d.optStringOrNull("country"), createdAt = d.optLong("created"), updatedAt = d.getLong("updated"),
                showFlag = if (d.isNull("flag")) null else d.getBoolean("flag"), moreFiles = Attachment.encode(atts.drop(1)),
                sortOrder = if (d.isNull("order")) null else d.getInt("order"), origin = p.key,
            ))
            changed++
        }

        val keepPws = mutableSetOf<String>()
        if (common != null) j.getJSONArray("passwords").forEachObj { w ->
            val id = prefix + w.getString("id"); keepPws += id
            dao.upsertPassword(PasswordEntity(id, common.id, w.getString("site"), w.getString("user"), w.getString("pass"), w.optLong("created"), origin = p.key))
        }
        removeFrom(g, p.key, keepDocs, keepMembers, keepPws)

        // Their name may have changed, and they may know members you don't yet.
        val now = dao.groupNow(g.id) ?: g
        val known = now.peerList.associateBy { it.key }.toMutableMap()
        known[p.key] = p.copy(name = from.optString("name", p.name), at = System.currentTimeMillis())
        j.optJSONArray("peers")?.forEachObj { o -> val k = o.getString("key"); if (k != myKey && k !in known) known[k] = Peer(k, o.optString("name"), o.getString("index")) }
        dao.upsertGroup(now.copy(peers = Peer.encode(known.values.toList())))
        return changed
    }

    private suspend fun removeFrom(g: GroupEntity, key: String, keepDocs: Set<String>, keepMembers: Set<String>, keepPws: Set<String>) {
        val prefix = "s:${g.id}:$key:%"
        dao.receivedDocuments(key, prefix).filter { it.id !in keepDocs }.forEach { d ->
            d.attachments.forEach { files.delete(it.id) }; files.delete(d.pictureFile); dao.deleteDocument(d.id)
        }
        dao.receivedPasswords(key, prefix).filter { it.id !in keepPws }.forEach { dao.deletePassword(it.id) }
        dao.receivedMembers(key, g.id).filter { it.id !in keepMembers }.forEach { files.delete(it.photoFile); dao.deleteMember(it.id) }
    }

    /** A shared file, kept on the phone (encrypted like everything else) under an id made from its Drive id: downloaded once. */
    private fun fetchFile(driveId: String, key: ByteArray): String? {
        val id = "s-$driveId"
        if (files.has(id)) return id
        return runCatching { files.put(BackupCrypto.open(DriveClient.publicDownload(driveId), key), id) }.getOrNull()
    }

    private inline fun JSONArray.forEachObj(f: (JSONObject) -> Unit) { for (i in 0 until length()) f(getJSONObject(i)) }
    private fun JSONObject.optStringOrNull(k: String): String? = if (!has(k) || isNull(k)) null else optString(k).ifEmpty { null }
    private fun sha(s: String) = java.security.MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    companion object {
        const val P_SHARE = "fd_share"; const val P_SHARE_FILE = "fd_share_file"
        private const val OCTET = "application/octet-stream"
    }
}
