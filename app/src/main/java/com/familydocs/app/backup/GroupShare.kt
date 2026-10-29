package com.familydocs.app.backup

import android.content.Context
import com.familydocs.app.data.AdminEdits
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
 *   My Drive › Lararium › <Group> › Shared with <Group> (encrypted)/
 *       index.fdx          ← people, documents, Common logins (JSON), sealed
 *       <file id>.fdx      ← each scan / page / picture, sealed
 *
 * What goes in: you, the Common vault and the people you added to the group, with the documents added on this phone.
 * Your own logins never do; Common logins do. Things other members shared with you are never passed on.
 * Sealing: AES-256-GCM ([BackupCrypto]) with a key derived from the group code (PBKDF2, salt from the group id), so
 * only someone with the code can open it. The files are "anyone with the link" because the app's Drive access
 * (drive.file) can't open files other accounts share by email; the file ids travel only inside invites.
 * What others share shows up read-only here, marked with their key ([MemberEntity.origin] / [DocumentEntity.origin]).
 *
 * Admins: whoever made the group is its admin and can make others admins. An admin can change another member's documents
 * and profile, or remove them from the group. Each member's things live in their own vault, so an admin's change is an
 * "op" in the admin's share addressed to that member; their phone applies it to its own vault (telling them who changed
 * what), lists it as applied, and the admin's phone then drops it. Until then the admin sees the change on their phone.
 */
class GroupShare(private val context: Context, private val dao: VaultDao, private val files: FileStore,
                 private val crypto: com.familydocs.app.security.CryptoManager) : AdminEdits {
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
                // removed from the group meanwhile (by an admin, read from an earlier share)
                if (dao.groupNow(g.id)?.peerList?.any { it.key == p.key } != true) return@forEach
                onProgress("Reading ${p.name}'s share…")
                runCatching { received += read(g, p, me.id) }.onFailure { e ->
                    problems += "${p.name}: ${e.message}"
                    // remember why, so Group settings can show it next to their name
                    dao.groupNow(g.id)?.let { now -> dao.upsertGroup(now.copy(peers = Peer.encode(now.peerList.map { if (it.key == p.key) it.copy(err = e.message ?: "Couldn't read") else it }))) }
                }
            }
            settleCreator(g.id, me)
        }
        if (problems.isEmpty()) "Group up to date" + if (received > 0) " · $received new or changed" else ""
        else "Some shares couldn't be read (${problems.joinToString("; ")})"
    } }

    /**
     * The invite for group [groupId]. Quick: the first time it only puts a small index in Drive (your name, no files yet)
     * so the invite can be sent straight away; your documents follow in the background.
     */
    suspend fun invite(accessToken: String, groupId: String): GroupInvite = withContext(Dispatchers.IO) {
        val me = dao.selfNow() ?: error("No account on this phone")
        val g0 = dao.groupNow(groupId) ?: error("Group not found")
        val g = if (g0.shareIndex != null) g0 else {
            val quick = running.withLock { dao.groupNow(groupId)?.takeIf { it.shareIndex != null } ?: firstIndex(DriveClient(accessToken), g0, me) }
            BackupWorker.syncShares(context) // the full share (documents, files) uploads in the background
            quick
        }
        GroupInvite(g.id, g.name, g.code, g.shareIndex!!, me.id, dao.accountNow()?.name ?: me.name)
    }

    /** A first, tiny share index (just you), link-shared, so an invite exists before the files are uploaded. */
    private suspend fun firstIndex(drive: DriveClient, g: GroupEntity, me: MemberEntity): GroupEntity {
        val key = key(g)
        val folder = g.shareFolder ?: run {
            val root = drive.folder("Lararium", null, mapOf(DriveBackup.P_ROOT to "1"))
            val groupFolder = drive.folder(g.name, root, mapOf(DriveBackup.P_GROUP to g.id), "Lararium group · its people, Common vault and events")
            drive.folder("Shared with ${g.name} (encrypted)", groupFolder, mapOf(P_SHARE to g.id),
                "Encrypted copy for the other members of ${g.name}. Only readable in Lararium with the group's code.")
        }
        val name = dao.accountNow()?.name ?: me.name
        val text = JSONObject().put("v", 1).put("group", g.id).put("name", g.name).put("from", JSONObject().put("key", me.id).put("name", name))
            .put("members", JSONArray().put(JSONObject().put("id", me.id).put("name", name).put("relation", me.relation).put("kind", "self").put("avatar", me.avatar ?: JSONObject.NULL)))
            .put("documents", JSONArray()).put("passwords", JSONArray())
            .put("peers", JSONArray(g.peerList.map { JSONObject().put("key", it.key).put("name", it.name).put("index", it.index) }))
            .let { meta(it, g, me, null) }.toString()
        val id = drive.upsertFile(folder, "index.fdx", BackupCrypto.seal(text.toByteArray(), key, GroupInvite.salt(g.id)), OCTET, mapOf(P_SHARE_FILE to "${g.id}:index"), null, P_SHARE_FILE)
        drive.shareWithLink(id)
        val out = g.copy(shareFolder = folder, shareIndex = id)
        dao.upsertGroup(out)
        return out
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
        readmit(base.id, peer.key)
        val g: GroupEntity = base.copy(peers = Peer.encode(base.peerList.filter { it.key != peer.key } + peer))
        dao.upsertGroup(g)
        // Read it now (this also checks the invite works); if the connection fails, the next sync tries again.
        val problem = running.withLock {
            val err = runCatching { read(g, peer, me.id) }.exceptionOrNull()?.let { it.message ?: "Couldn't read the share yet" }
            // Publish yours too, so the invite you send back works right away.
            if (accessToken != null) runCatching { publish(DriveClient(accessToken), dao.groupNow(g.id)!!, me) }
            err
        }
        if (accessToken == null) BackupWorker.syncShares(context)
        dao.groupNow(g.id)!! to problem
    }

    /** Stops reading [peerKey]'s share and removes what they shared from this phone. */
    suspend fun forget(groupId: String, peerKey: String) = withContext(Dispatchers.IO) {
        val g = dao.groupNow(groupId) ?: return@withContext
        removeFrom(g, peerKey, keepDocs = emptySet(), keepMembers = emptySet(), keepPws = emptySet())
        dao.upsertGroup(g.copy(peers = Peer.encode(g.peerList.filter { it.key != peerKey })))
    }

    // ---------------- publishing ----------------

    /** The group's key (from its code and id): seals the shares, and join-request replies only members can read. */
    fun groupKey(g: GroupEntity) = key(g)

    // The key is slow to derive on purpose (it protects the code), so it's worked out once per group and code.
    private val keyCache = java.util.concurrent.ConcurrentHashMap<String, ByteArray>()
    private fun key(g: GroupEntity) = keyCache.getOrPut("${g.id}|${GroupCodes.normalize(g.code)}") {
        BackupCrypto.deriveKey(GroupCodes.normalize(g.code).toCharArray(), GroupInvite.salt(g.id))
    }

    private suspend fun publish(drive: DriveClient, g: GroupEntity, me: MemberEntity): GroupEntity {
        val key = key(g); val salt = GroupInvite.salt(g.id)
        fun seal(b: ByteArray) = BackupCrypto.seal(b, key, salt)
        val root = drive.folder("Lararium", null, mapOf(DriveBackup.P_ROOT to "1"))
        val groupFolder = drive.folder(g.name, root, mapOf(DriveBackup.P_GROUP to g.id), "Lararium group · its people, Common vault and events")
        val folder = drive.folder("Shared with ${g.name} (encrypted)", groupFolder, mapOf(P_SHARE to g.id),
            "Encrypted copy for the other members of ${g.name}. Only readable in Lararium with the group's code.")

        val all = dao.membersNow()
        val common = all.firstOrNull { it.shared && it.groupId == g.id }
        // Only you and the Common vault: everyone else in a group has their own account and shares their own.
        val people = listOf(me) + listOfNotNull(common)
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
            // Common logins don't travel under the group code: they're sealed to each member's phone key ("logins" below).
            .put("passwords", JSONArray())
            .put("logins", sealedLogins(g, me, pws))
            // Who else you read: someone who joins through you finds the rest of the group from here.
            .put("peers", JSONArray(g.peerList.map { JSONObject().put("key", it.key).put("name", it.name).put("index", it.index) }))
        // admin changes nobody picked up for two months are dropped
        synchronized(prefs) { ops(g.id).let { all -> all.filter { System.currentTimeMillis() - it.optLong("at") < OP_LIFE }.let { if (it.size != all.size) saveOps(g.id, it) } } }
        meta(index, g, me, ::upload)
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
        // Your admin changes their phone has taken over: drop them (their version is read below).
        val retired = retire(g.id, p.key, j.optJSONArray("applied"))
        // Your admin changes still waiting for their phone: shown here instead of what their share says.
        val pending = ops(g.id).filter { it.optString("to") == p.key }
        val pendingDocs = pending.filter { it.optString("kind") == "doc" }.map { prefix + it.getJSONObject("doc").getString("id") }.toSet()
        val pendingDel = pending.filter { it.optString("kind") == "deldoc" }.map { prefix + it.optString("doc") }.toSet()
        val pendingProfile = pending.lastOrNull { it.optString("kind") == "profile" }

        val memberIds = mutableMapOf<String, String>()
        val keepMembers = mutableSetOf<String>()
        j.getJSONArray("members").forEachObj { m ->
            val kind = m.optString("kind")
            if (kind == "common") { common?.let { memberIds[m.getString("id")] = it.id }; return@forEachObj }
            // Only the account holder themselves: people their phone made up (older versions) are not group members.
            if (kind != "self") return@forEachObj
            val id = prefix + m.getString("id")
            memberIds[m.getString("id")] = id; keepMembers += id
            val photo = m.optStringOrNull("photo")?.let { fetch(it) }
            var row = MemberEntity(id, m.getString("name"), "Group member", photo, createdAt = 1L, avatar = m.optStringOrNull("avatar"), groupId = g.id, origin = p.key)
            pendingProfile?.let { op -> row = row.copy(name = op.optStringOrNull("name") ?: row.name, avatar = op.optStringOrNull("avatar") ?: row.avatar,
                photoFile = if (op.has("photo")) op.optString("photo").ifEmpty { null } else row.photoFile) }
            dao.upsertMember(row)
        }

        val keepDocs = mutableSetOf<String>()
        j.getJSONArray("documents").forEachObj { d ->
            val member = memberIds[d.getString("member")] ?: return@forEachObj
            val id = prefix + d.getString("id")
            if (id in pendingDel) return@forEachObj
            keepDocs += id
            if (id in pendingDocs) return@forEachObj
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
        if (common != null) {
            suspend fun take(w: JSONObject) {
                val id = prefix + w.getString("id"); keepPws += id
                dao.upsertPassword(PasswordEntity(id, common.id, w.getString("site"), w.getString("user"), w.getString("pass"), w.optLong("created"), origin = p.key))
            }
            j.optJSONArray("passwords")?.forEachObj { take(it) } // (older apps)
            j.optJSONObject("logins")?.let { l ->
                val opened = runCatching { openLogins(l, myKey) }.getOrNull()
                // not sealed to this phone yet (they haven't read your new key): keep what you had from them
                if (opened == null) dao.receivedPasswords(p.key, "s:${g.id}:${p.key}:%").forEach { keepPws += it.id }
                else opened.forEachObj { take(it) }
            }
        }
        removeFrom(g, p.key, keepDocs + pendingDocs, keepMembers, keepPws)
        cleanup(retired)

        // ---- admins: who made the group, who else is an admin, removals, and changes an admin made to your things ----
        j.optLong("since").takeIf { it > 0 }?.let { prefs.edit().putLong("since:${g.id}:${p.key}", it).apply() }
        var grp = dao.groupNow(g.id) ?: g
        if (grp.creator == null) j.optStringOrNull("creator")?.let { grp = grp.copy(creator = it); dao.upsertGroup(grp) }
        val by = from.optString("name", p.name)
        if (p.key in grp.adminKeys) {
            j.optJSONObject("admins")?.let { a ->
                val at = a.optLong("at")
                if (at > grp.adminsAt) {
                    val keys = a.optJSONArray("keys")?.let { ks -> (0 until ks.length()).map { ks.getString(it) } }.orEmpty().filter { it != grp.creator }
                    val was = myKey in grp.adminKeys
                    grp = grp.copy(admins = keys.joinToString(",").ifEmpty { null }, adminsAt = at); dao.upsertGroup(grp)
                    val isNow = myKey in grp.adminKeys
                    if (isNow && !was) addNotice(grp, by, "$by made you an admin of ${grp.name}: you can change everyone's documents and profiles, and add or remove admins and members.")
                    if (was && !isNow) addNotice(grp, by, "$by took away your admin role in ${grp.name}.")
                }
            }
            var removedMe = false
            j.optJSONObject("removed")?.let { r ->
                val handled = synchronized(prefs) { longMap("handled:${g.id}") }
                r.keys().forEach { rk ->
                    val at = r.optLong(rk)
                    if (at > (handled[rk] ?: 0L)) {
                        handled[rk] = at
                        if (rk == myKey) removedMe = true else if (rk != p.key && rk != grp.creator) forget(g.id, rk)
                    }
                }
                synchronized(prefs) { putLongMap("handled:${g.id}", longMap("handled:${g.id}").apply { putAll(handled) }) }
            }
            if (removedMe) { leave(grp, by); return changed }
            changed += applyOps(grp, j, by, myKey, k)
        }

        // Their name may have changed, and they may know members you don't yet (not people an admin just removed).
        val now = dao.groupNow(g.id) ?: grp
        val known = now.peerList.associateBy { it.key }.toMutableMap()
        known[p.key] = p.copy(name = by, at = System.currentTimeMillis(), err = null, pub = j.optStringOrNull("pub") ?: p.pub)
        val gone = synchronized(prefs) { longMap("handled:${g.id}") }
        j.optJSONArray("peers")?.forEachObj { o -> val pk = o.getString("key")
            if (pk != myKey && pk !in known && System.currentTimeMillis() - (gone[pk] ?: 0L) > REMOVED_QUIET) known[pk] = Peer(pk, o.optString("name"), o.getString("index")) }
        dao.upsertGroup(now.copy(peers = Peer.encode(known.values.toList())))
        return changed
    }

    // ---------------- Common logins, sealed to each member's phone ----------------

    /**
     * This phone's key pair for the group's Common logins: the private half never leaves the phone (kept encrypted with
     * the Android Keystore key), the public half goes in your share. A new phone gets a new pair; the others seal to it
     * once they've read your share.
     */
    private fun deviceKeys(): Pair<String, java.security.PrivateKey> {
      synchronized(prefs) {
        val pub = prefs.getString("device_pub", null)
        val priv = crypto.readSecret("device_ec_priv")
        if (pub != null && priv != null)
            return pub to java.security.KeyFactory.getInstance("EC").generatePrivate(java.security.spec.PKCS8EncodedKeySpec(priv))
        val kp = RequestCrypto.newKeys()
        crypto.storeSecret("device_ec_priv", kp.private.encoded)
        val p = RequestCrypto.b64(kp.public.encoded)
        prefs.edit().putString("device_pub", p).commit()
        return p to kp.private
      }
    }
    private fun devicePub() = deviceKeys().first

    /**
     * Your Common logins for the share: sealed with a fresh random key, and that key sealed to each member's phone key
     * (and yours). Only members whose phones you've read can open them; the group code alone can't. Reused while
     * nothing changed, so the share isn't rewritten on every sync.
     */
    private fun sealedLogins(g: GroupEntity, me: MemberEntity, pws: List<PasswordEntity>): Any {
        if (pws.isEmpty()) return JSONObject.NULL
        val plain = JSONArray(pws.map { JSONObject().put("id", it.id).put("site", it.site).put("user", it.username).put("pass", it.password).put("created", it.createdAt) }).toString()
        val to = (g.peerList.mapNotNull { p -> p.pub?.let { p.key to it } } + (me.id to devicePub())).sortedBy { it.first }
        val sig = sha(plain + "|" + to.joinToString(",") { "${it.first}:${it.second}" })
        if (prefs.getString("loginsig:${g.id}", null) == sig) prefs.getString("logins:${g.id}", null)?.let { return JSONObject(it) }
        val k = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
        val env = JSONObject()
        to.forEach { (key, pub) -> runCatching { RequestCrypto.sealTo(RequestCrypto.b64(k), RequestCrypto.publicOf(pub)) }.getOrNull()
            ?.let { (epk, box) -> env.put(key, JSONObject().put("epk", epk).put("box", box)) } }
        val out = JSONObject().put("box", RequestCrypto.b64(BackupCrypto.seal(plain.toByteArray(), k, GroupInvite.salt(g.id)))).put("to", env)
        prefs.edit().putString("logins:${g.id}", out.toString()).putString("loginsig:${g.id}", sig).apply()
        return out
    }

    /** Opens someone's Common logins with this phone's key; null when they aren't sealed to it (yet). */
    private fun openLogins(l: JSONObject, myKey: String): JSONArray? {
        val e = l.optJSONObject("to")?.optJSONObject(myKey) ?: return null
        val k = RequestCrypto.unb64(RequestCrypto.openFrom(e.getString("epk"), e.getString("box"), deviceKeys().second))
        return JSONArray(String(BackupCrypto.open(RequestCrypto.unb64(l.getString("box")), k)))
    }

    // ---------------- admins ----------------

    /** Something an admin changed in your things (or about you), shown on Home until you close it. */
    data class Notice(val id: String, val groupId: String, val by: String, val text: String, val at: Long)
    val notices = kotlinx.coroutines.flow.MutableStateFlow(loadNotices())
    fun dismissNotice(id: String) = synchronized(prefs) { saveNotices(notices.value.filter { it.id != id }) }
    private fun loadNotices(): List<Notice> = runCatching {
        val a = JSONArray(prefs.getString("notices", "[]"))
        (0 until a.length()).map { a.getJSONObject(it).let { o -> Notice(o.getString("id"), o.optString("g"), o.optString("by"), o.getString("t"), o.optLong("at")) } }
    }.getOrDefault(emptyList())
    private fun saveNotices(list: List<Notice>) {
        prefs.edit().putString("notices", JSONArray(list.map { JSONObject().put("id", it.id).put("g", it.groupId).put("by", it.by).put("t", it.text).put("at", it.at) }).toString()).apply()
        notices.value = list
    }
    private fun addNotice(g: GroupEntity, by: String, text: String) = synchronized(prefs) {
        saveNotices((notices.value + Notice(java.util.UUID.randomUUID().toString(), g.id, by, text, System.currentTimeMillis())).takeLast(20))
    }

    private fun jsonList(key: String): MutableList<JSONObject> = runCatching {
        val a = JSONArray(prefs.getString(key, null) ?: return mutableListOf()); (0 until a.length()).map { a.getJSONObject(it) }.toMutableList()
    }.getOrDefault(mutableListOf())
    private fun longMap(key: String): MutableMap<String, Long> = runCatching {
        val o = JSONObject(prefs.getString(key, null) ?: return mutableMapOf()); o.keys().asSequence().associateWith { o.optLong(it) }.toMutableMap()
    }.getOrDefault(mutableMapOf())
    private fun putLongMap(key: String, m: Map<String, Long>) { prefs.edit().putString(key, JSONObject().apply { m.forEach { (k, v) -> put(k, v) } }.toString()).apply() }
    /** Your admin changes for other members of group [gid], not yet taken over by their phones. */
    private fun ops(gid: String) = jsonList("ops:$gid")
    private fun saveOps(gid: String, list: List<JSONObject>) { prefs.edit().putString("ops:$gid", JSONArray(list).toString()).apply() }
    /** Ids of admin changes this phone has applied to your own things (the admin's phone stops sending them). */
    private fun applied(gid: String): MutableList<String> = runCatching {
        val a = JSONArray(prefs.getString("applied:$gid", null) ?: return mutableListOf()); (0 until a.length()).map { a.getString(it) }.toMutableList()
    }.getOrDefault(mutableListOf())
    private fun docIdOf(op: JSONObject) = op.optJSONObject("doc")?.optString("id") ?: op.optString("doc")
    private fun newOp(to: String, kind: String) = JSONObject().put("id", java.util.UUID.randomUUID().toString()).put("to", to).put("at", System.currentTimeMillis()).put("kind", kind)
    private fun addOp(gid: String, op: JSONObject, replaces: (JSONObject) -> Boolean = { false }) = synchronized(prefs) { saveOps(gid, ops(gid).filterNot(replaces) + op) }

    private suspend fun requireAdmin(gid: String) {
        val g = dao.groupNow(gid) ?: error("Group not found")
        require(g.isAdmin(dao.selfNow()?.id)) { "Only a group admin can change other members' things" }
    }

    /** Whether [key] was removed from group [groupId] after [at] (an old request of theirs mustn't bring them back). */
    fun removedSince(groupId: String, key: String, at: Long): Boolean = synchronized(prefs) { (longMap("handled:$groupId")[key] ?: 0L) > at }

    /** Someone is added again (an invite, an approved request): earlier removals of them no longer count. */
    private fun readmit(gid: String, key: String) = synchronized(prefs) {
        val removed = longMap("removed:$gid"); if (removed.remove(key) != null) putLongMap("removed:$gid", removed)
        putLongMap("handled:$gid", longMap("handled:$gid").apply { put(key, System.currentTimeMillis()) })
    }

    override suspend fun putDoc(row: DocumentEntity, member: MemberEntity) {
        val gid = member.groupId ?: return; val to = row.origin ?: return
        requireAdmin(gid)
        val docId = row.id.removePrefix("s:$gid:$to:")
        val d = JSONObject().put("id", docId).put("common", member.shared).put("name", row.name).put("fields", row.fieldsJson).put("text", row.rawText)
            .put("ocr", row.readByOcr).put("country", row.country ?: JSONObject.NULL).put("display", row.display.name).put("flag", row.showFlag ?: JSONObject.NULL)
            .put("created", row.createdAt).put("updated", row.updatedAt)
            .put("files", JSONArray(row.attachments.map { JSONObject().put("id", it.id).put("mime", it.mime) })).put("picture", row.pictureFile ?: JSONObject.NULL)
        addOp(gid, newOp(to, "doc").put("doc", d)) { it.optString("to") == to && it.optString("kind") in DOC_KINDS && docIdOf(it) == docId }
        BackupWorker.soon(context)
    }

    override suspend fun deleteDoc(row: DocumentEntity, member: MemberEntity?) {
        val to = row.origin ?: return
        val gid = member?.groupId ?: row.id.split(':').getOrNull(1) ?: return
        requireAdmin(gid)
        val docId = row.id.removePrefix("s:$gid:$to:")
        addOp(gid, newOp(to, "deldoc").put("doc", docId).put("name", row.name)) { it.optString("to") == to && it.optString("kind") in DOC_KINDS && docIdOf(it) == docId }
        BackupWorker.soon(context)
    }

    override suspend fun profile(row: MemberEntity, photoChanged: Boolean) {
        val gid = row.groupId ?: return; val to = row.origin ?: return
        requireAdmin(gid)
        val op = newOp(to, "profile").put("name", row.name).put("avatar", row.avatar ?: JSONObject.NULL)
        if (photoChanged) op.put("photo", row.photoFile ?: "")
        addOp(gid, op)
        BackupWorker.soon(context)
    }

    /** Removes [key] from the group: their phone leaves it, everyone else's stops reading their share. */
    override suspend fun removeMember(groupId: String, key: String) = withContext(Dispatchers.IO) {
        requireAdmin(groupId)
        val g = dao.groupNow(groupId) ?: return@withContext
        require(key != g.creator) { "The person who made the group can't be removed" }
        val now = System.currentTimeMillis()
        synchronized(prefs) {
            putLongMap("removed:$groupId", longMap("removed:$groupId").apply { put(key, now) })
            putLongMap("handled:$groupId", longMap("handled:$groupId").apply { put(key, now) })
            saveOps(groupId, ops(groupId).filter { it.optString("to") != key })
        }
        forget(groupId, key)
        dao.groupNow(groupId)?.let { if (key in it.adminKeys) dao.upsertGroup(it.copy(admins = (it.adminKeys - key - setOfNotNull(it.creator)).joinToString(",").ifEmpty { null }, adminsAt = now)) }
        BackupWorker.soon(context)
    }

    /** Makes [key] an admin of the group, or takes it back (not from the person who made the group). */
    suspend fun setAdmin(groupId: String, key: String, on: Boolean) = withContext(Dispatchers.IO) {
        requireAdmin(groupId)
        val g = dao.groupNow(groupId) ?: return@withContext
        if (key == g.creator) return@withContext
        val others = g.adminKeys - setOfNotNull(g.creator)
        val next = if (on) others + key else others - key
        dao.upsertGroup(g.copy(admins = next.joinToString(",").ifEmpty { null }, adminsAt = System.currentTimeMillis()))
        BackupWorker.soon(context)
    }

    /** Admin details in a share index: when you got the group, who made it, the admins, and the admin changes. */
    private fun meta(o: JSONObject, g: GroupEntity, me: MemberEntity, upload: ((String?) -> String?)?): JSONObject {
        o.put("since", g.createdAt).put("creator", g.creator ?: JSONObject.NULL).put("pub", devicePub())
            .put("admins", JSONObject().put("keys", JSONArray((g.adminKeys - setOfNotNull(g.creator)).toList())).put("at", g.adminsAt))
            .put("applied", JSONArray(synchronized(prefs) { applied(g.id) }))
        if (g.isAdmin(me.id)) {
            o.put("removed", JSONObject().apply { synchronized(prefs) { longMap("removed:${g.id}") }.forEach { (k, v) -> put(k, v) } })
            if (upload != null) o.put("ops", JSONArray(synchronized(prefs) { ops(g.id) }.mapNotNull { wire(it, upload) }))
        }
        return o
    }

    /** An op as it goes into the share: this phone's file ids become the uploaded Drive files. */
    private fun wire(op: JSONObject, upload: (String?) -> String?): JSONObject? {
        val w = JSONObject(op.toString())
        when (op.optString("kind")) {
            "doc" -> {
                val d = w.getJSONObject("doc")
                val fs = d.getJSONArray("files"); val out = JSONArray()
                for (i in 0 until fs.length()) { val f = fs.getJSONObject(i); out.put(JSONObject().put("f", upload(f.getString("id")) ?: return null).put("mime", f.optString("mime"))) }
                d.put("files", out)
                if (!d.isNull("picture")) d.put("picture", upload(d.getString("picture")) ?: return null)
            }
            "profile" -> if (w.optString("photo").isNotEmpty()) w.put("photo", upload(w.getString("photo")) ?: return null)
        }
        return w
    }

    /** An admin's changes addressed to you, from their share: applied to your own vault, then a note on Home. */
    private suspend fun applyOps(g: GroupEntity, j: JSONObject, by: String, myKey: String, key: ByteArray): Int {
        val list = j.optJSONArray("ops") ?: return 0
        val done = synchronized(prefs) { applied(g.id) }
        val before = done.size
        val what = mutableListOf<String>()
        for (i in 0 until list.length()) {
            val op = list.getJSONObject(i)
            if (op.optString("to") != myKey || op.optString("id") in done) continue
            val r = runCatching { applyOp(g, op, key) }
            if (r.isFailure) continue // (a file didn't download: tried again next time)
            done += op.getString("id"); r.getOrNull()?.let { what += it }
        }
        if (done.size != before) synchronized(prefs) { prefs.edit().putString("applied:${g.id}", JSONArray(done.takeLast(300)).toString()).apply() }
        if (what.isNotEmpty()) {
            addNotice(g, by, "$by (admin of ${g.name}) made changes: ${what.joinToString("; ")}.")
            BackupWorker.soon(context) // your share and backup now carry them
        }
        return what.size
    }

    private suspend fun applyOp(g: GroupEntity, op: JSONObject, key: ByteArray): String? {
        fun need(driveId: String) = fetchFile(driveId, key) ?: error("Couldn't download a file")
        when (op.optString("kind")) {
            "profile" -> {
                val self = dao.selfNow() ?: error("No account on this phone"); val acct = dao.accountNow()
                var s = self; val what = mutableListOf<String>()
                op.optStringOrNull("name")?.trim()?.takeIf { it.isNotEmpty() && it != (acct?.name ?: self.name) }?.let { s = s.copy(name = it); what += "your name is now $it" }
                if (op.has("photo")) {
                    val ph = op.optString("photo"); val id = if (ph.isEmpty()) null else need(ph)
                    if (id != self.photoFile) {
                        setOfNotNull(self.photoFile, acct?.photoFile).filter { it != id }.forEach { files.delete(it) }
                        s = s.copy(photoFile = id); what += if (id == null) "removed your photo" else "changed your photo"
                    }
                }
                op.optStringOrNull("avatar")?.takeIf { it != self.avatar }?.let { s = s.copy(avatar = it); if (s.photoFile == null) what += "changed your picture" }
                if (s != self) { dao.upsertMember(s); acct?.let { dao.upsertAccount(it.copy(name = s.name, photoFile = s.photoFile)) } }
                return what.joinToString(", ").ifEmpty { null }
            }
            "doc" -> {
                val d = op.getJSONObject("doc"); val id = d.getString("id")
                val old = dao.documentNow(id)
                require(old == null || old.origin == null) { "Not one of yours" }
                val member = old?.memberId ?: ((if (d.optBoolean("common")) dao.sharedIn(g.id) else dao.selfNow()) ?: error("No vault for it")).id
                val atts = mutableListOf<Attachment>()
                d.getJSONArray("files").forEachObj { f -> atts += Attachment(need(f.getString("f")), f.optString("mime", "image/jpeg")) }
                val pic = d.optStringOrNull("picture")?.let { need(it) }
                old?.let { o -> (o.attachments.map { it.id } + listOfNotNull(o.pictureFile)).filter { f -> atts.none { it.id == f } && f != pic }.forEach { files.delete(it) } }
                dao.upsertDocument(DocumentEntity(
                    id = id, memberId = member, name = d.getString("name"), fileId = atts.firstOrNull()?.id, fileMime = atts.firstOrNull()?.mime,
                    display = runCatching { DisplayMode.valueOf(d.getString("display")) }.getOrDefault(DisplayMode.ICON),
                    pictureFile = pic, fieldsJson = d.optString("fields", "[]"), rawText = d.optString("text"), readByOcr = d.optBoolean("ocr"),
                    country = d.optStringOrNull("country"), createdAt = old?.createdAt ?: d.optLong("created", System.currentTimeMillis()),
                    updatedAt = d.optLong("updated", System.currentTimeMillis()), showFlag = if (d.isNull("flag")) null else d.optBoolean("flag"),
                    moreFiles = Attachment.encode(atts.drop(1)), sortOrder = old?.sortOrder, origin = null,
                ))
                return "${if (old == null) "added" else "edited"} “${d.getString("name")}”"
            }
            "deldoc" -> {
                val old = dao.documentNow(op.optString("doc"))?.takeIf { it.origin == null } ?: return null
                old.attachments.forEach { files.delete(it.id) }; files.delete(old.pictureFile); dao.deleteDocument(old.id)
                return "removed “${old.name}”"
            }
            else -> return null
        }
    }

    /** Admin changes [peerKey]'s phone lists as applied: no longer sent. Returns them, for [cleanup]. */
    private fun retire(gid: String, peerKey: String, theirs: JSONArray?): List<JSONObject> {
        theirs ?: return emptyList()
        val done = (0 until theirs.length()).map { theirs.getString(it) }.toSet()
        return synchronized(prefs) {
            val (out, keep) = ops(gid).partition { it.optString("to") == peerKey && it.optString("id") in done }
            if (out.isNotEmpty()) saveOps(gid, keep)
            out
        }
    }

    /** This phone's copies of files that only finished admin changes still pointed to. */
    private suspend fun cleanup(gone: List<JSONObject>) {
        if (gone.isEmpty()) return
        val ids = gone.flatMap { op ->
            val d = op.optJSONObject("doc")
            val fs = d?.optJSONArray("files")?.let { a -> (0 until a.length()).map { a.getJSONObject(it).getString("id") } }.orEmpty()
            fs + listOfNotNull(d?.optStringOrNull("picture"), op.optStringOrNull("photo"))
        }.toSet()
        if (ids.isEmpty()) return
        val used = mutableSetOf<String>()
        dao.allDocumentsNow().forEach { d -> d.attachments.forEach { used += it.id }; d.pictureFile?.let { used += it } }
        dao.membersNow().forEach { m -> m.photoFile?.let { used += it } }
        dao.accountNow()?.photoFile?.let { used += it }
        dao.groupsNow().forEach { g -> g.photoFile?.let { used += it }; ops(g.id).forEach { op -> op.optJSONObject("doc")?.optJSONArray("files")?.let { a -> for (i in 0 until a.length()) used += a.getJSONObject(i).getString("id") } } }
        (ids - used).forEach { files.delete(it) }
    }

    /** An admin removed you: the group stays on this phone with your own things, without the others. */
    private suspend fun leave(g: GroupEntity, by: String) {
        g.peerList.forEach { removeFrom(g, it.key, emptySet(), emptySet(), emptySet()) }
        dao.upsertGroup(g.copy(peers = null, creator = null, admins = null, adminsAt = 0L))
        synchronized(prefs) { saveOps(g.id, emptyList()) }
        addNotice(g, by, "$by removed you from ${g.name}. Your own documents and the Common vault stay on this phone. If it was a mistake, ask to join again with the group's code.")
    }

    /**
     * Who made the group, for groups from before admins: the member who has had it longest (their "since"), once
     * everyone's app says. Alone in a group, it's you.
     */
    private suspend fun settleCreator(gid: String, me: MemberEntity) {
        val g = dao.groupNow(gid) ?: return
        if (g.creator != null) return
        val since = g.peerList.associate { it.key to prefs.getLong("since:$gid:${it.key}", 0L) }
        if (since.values.any { it == 0L }) return
        val creator = (since + (me.id to g.createdAt)).minByOrNull { it.value }!!.key
        dao.upsertGroup(g.copy(creator = creator))
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
        private val DOC_KINDS = setOf("doc", "deldoc")
        private const val OP_LIFE = 60L * 24 * 3600 * 1000
        /** For a week after someone is removed, other members' (older) lists of who's in the group don't bring them back. */
        private const val REMOVED_QUIET = 7L * 24 * 3600 * 1000
        private const val OCTET = "application/octet-stream"
    }
}
