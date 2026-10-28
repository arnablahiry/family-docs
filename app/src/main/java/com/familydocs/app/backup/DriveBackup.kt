package com.familydocs.app.backup

import android.content.Context
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.familydocs.app.data.AccountEntity
import com.familydocs.app.data.DisplayMode
import com.familydocs.app.data.DocumentEntity
import com.familydocs.app.data.FileStore
import com.familydocs.app.data.MemberEntity
import com.familydocs.app.data.PasswordEntity
import com.familydocs.app.data.VaultRepository
import com.familydocs.app.domain.Countries
import com.familydocs.app.domain.DocTypes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Backup to the user's own Google Drive, laid out so it makes sense when browsed:
 *
 *   My Drive
 *   └─ Family Docs/                              ← root (label fd_root)
 *      ├─ Family Docs vault.fdenc                ← index: account, people, document list
 *      ├─ Common (shared)/                       ← one folder per person / the Common vault (label fd_member)
 *      │  ├─ Passwords.fdenc
 *      │  └─ Rental Agreement · Greece/          ← one folder per document (label fd_doc, coloured by type)
 *      │     ├─ original.jpg.fdenc               ← the scan / PDF, encrypted
 *      │     ├─ details.fdenc                    ← digitised fields, encrypted
 *      │     └─ original.jpg                     ← optional readable copy (Settings → "Viewable copies in Drive")
 *      ├─ Arnab/
 *      │  ├─ Aadhaar · India/ …
 *      │  └─ Passport · India/ …
 *      └─ Mohua/ …
 *
 * Every file and folder carries Drive appProperties ("labels": member, document id, type, country, expiry,
 * last-changed time) and a human description, so Drive search works and only changed documents are re-uploaded.
 * Encryption: AES-256-GCM (see [BackupCrypto]) with a random key that lives on the phone and in the Google
 * account's hidden app folder (scope drive.appdata), so signing in with Google on a new phone is enough to
 * restore. Names are readable, contents are not. Scope drive.file: the app only sees files it created.
 */
class DriveBackup(
    private val context: Context,
    private val repo: VaultRepository,
    private val files: FileStore,
    val keys: BackupKeyStore,
) {
    /** Same grant as "Continue with Google"; if the result has a resolution, the UI must launch it once. */
    suspend fun authorize(): AuthorizationResult =
        Identity.getAuthorizationClient(context).authorize(com.familydocs.app.auth.GoogleAccountHelper(context).request()).await()

    /** The backup key for this Google account: the one already in Drive's hidden app folder wins, so every phone shares it. */
    private fun keyFor(drive: DriveClient): BackupKeyStore.Key {
        val remote = drive.appDataRead(KEY_FILE)?.let { runCatching { parseKey(it) }.getOrNull() }
        val local = keys.current()
        return when {
            remote != null -> { if (local == null || !local.id.contentEquals(remote.id)) keys.install(remote); remote }
            local != null -> local.also { drive.appDataWrite(KEY_FILE, keyJson(it)) }
            else -> keys.generate().also { drive.appDataWrite(KEY_FILE, keyJson(it)) }
        }
    }

    private fun keyJson(k: BackupKeyStore.Key) = JSONObject().put("v", 1)
        .put("id", android.util.Base64.encodeToString(k.id, android.util.Base64.NO_WRAP))
        .put("key", android.util.Base64.encodeToString(k.key, android.util.Base64.NO_WRAP)).toString().toByteArray()

    private fun parseKey(b: ByteArray): BackupKeyStore.Key = JSONObject(String(b)).let {
        BackupKeyStore.Key(android.util.Base64.decode(it.getString("key"), android.util.Base64.NO_WRAP), android.util.Base64.decode(it.getString("id"), android.util.Base64.NO_WRAP))
    }

    /** True when this Google account already has a Family Docs backup. */
    suspend fun hasBackup(accessToken: String): Boolean = withContext(Dispatchers.IO) {
        val drive = DriveClient(accessToken)
        drive.findByProp(P_ROOT, "1") != null && drive.appDataFind(KEY_FILE) != null
    }

    /** Permanently removes the Family Docs folder and the backup key from this Google account's Drive. */
    suspend fun deleteFromDrive(accessToken: String) = withContext(Dispatchers.IO) {
        val drive = DriveClient(accessToken)
        drive.findByProp(P_ROOT, "1")?.let { drive.delete(it.id) }
        drive.appDataDelete(KEY_FILE)
    }

    data class Report(val uploaded: Int, val unchanged: Int, val removed: Int)

    suspend fun backup(accessToken: String, viewableCopies: Boolean = false, onProgress: (String) -> Unit = {}): Report = withContext(Dispatchers.IO) {
        val drive = DriveClient(accessToken)
        val s = repo.snapshot()
        val k = keyFor(drive); val key = k.key; val salt = k.id
        fun seal(b: ByteArray) = BackupCrypto.seal(b, key, salt)
        val selfName = s.account?.name ?: "Me"

        onProgress("Preparing Family Docs folder…")
        val root = drive.folder("Family Docs", null, mapOf(P_ROOT to "1"), "Encrypted backup made by the Family Docs app for $selfName. Open it with the app.")
        var uploaded = 0; var unchanged = 0; var removed = 0

        val memberFolders = mutableMapOf<String, String>()
        s.members.forEach { m ->
            val title = if (m.shared) "${m.name} (shared)" else m.name
            memberFolders[m.id] = drive.folder(title, root, mapOf(P_MEMBER to m.id), if (m.shared) "Shared with the whole family" else m.relation)
        }

        s.documents.forEachIndexed { i, d ->
            onProgress("Backing up ${i + 1} of ${s.documents.size}: ${d.name}")
            val parent = memberFolders[d.memberId] ?: return@forEachIndexed
            val type = DocTypes.forName(d.name)
            val country = d.issuer
            val folderName = listOfNotNull(d.name, country.takeIf { it.isNotEmpty() }?.let { Countries.name(it) }).joinToString(" · ")
            val expiry = d.fields.firstOrNull { Regex("expiry|valid until|valid till", RegexOption.IGNORE_CASE).containsMatchIn(it.label) && it.value.isNotBlank() }?.value
            val labels = buildMap {
                put(P_DOC, d.id); put(P_MEMBER_OF, d.memberId); put(P_TYPE, type.key)
                if (country.isNotEmpty()) put(P_COUNTRY, country)
                expiry?.let { put(P_EXPIRY, it) }
            }
            val description = listOfNotNull(type.label.takeIf { type.matched }, s.members.firstOrNull { it.id == d.memberId }?.name,
                country.takeIf { it.isNotEmpty() }?.let { Countries.name(it) }, expiry?.let { "valid until $it" }).joinToString(" · ")
            val docFolder = drive.folder(folderName, parent, labels, description, color = hex(type.hue))

            val remote = drive.children(docFolder)
            val stamp = d.updatedAt.toString()
            val upToDate = remote.any { it.props[P_KIND] == "details" && it.props[P_UPDATED] == stamp } &&
                (d.fileId == null || remote.any { it.props[P_KIND] == "original" && it.props[P_UPDATED] == stamp }) &&
                (!viewableCopies || d.fileId == null || remote.any { it.props[P_KIND] == "plain" && it.props[P_UPDATED] == stamp })
            if (upToDate) { unchanged++; return@forEachIndexed }

            val ext = when (d.fileMime) { "application/pdf" -> "pdf"; "image/png" -> "png"; else -> "jpg" }
            d.fileId?.let { files.get(it) }?.let { raw ->
                drive.upsertFile(docFolder, "original.$ext.fdenc", seal(raw), OCTET, mapOf(P_KIND to "original", P_DOC_FILE to "${d.id}:original", P_UPDATED to stamp, "ext" to ext), "Encrypted $description", P_DOC_FILE)
                if (viewableCopies) drive.upsertFile(docFolder, "original.$ext", raw, d.fileMime ?: "image/jpeg",
                    mapOf(P_KIND to "plain", P_DOC_FILE to "${d.id}:plain", P_UPDATED to stamp), "Readable copy: $description", P_DOC_FILE)
            }
            d.pictureFile?.let { files.get(it) }?.let { pic ->
                drive.upsertFile(docFolder, "picture.jpg.fdenc", seal(pic), OCTET, mapOf(P_KIND to "picture", P_DOC_FILE to "${d.id}:picture", P_UPDATED to stamp), null, P_DOC_FILE)
            }
            drive.upsertFile(docFolder, "details.fdenc", seal(docJson(d).toString().toByteArray()), OCTET,
                mapOf(P_KIND to "details", P_DOC_FILE to "${d.id}:details", P_UPDATED to stamp), "Digitised details, encrypted", P_DOC_FILE)
            if (!viewableCopies) remote.filter { it.props[P_KIND] == "plain" }.forEach { drive.trash(it.id) }
            uploaded++
        }

        onProgress("Saving passwords and photos…")
        s.members.forEach { m ->
            val folder = memberFolders.getValue(m.id)
            val pws = s.passwords.filter { it.memberId == m.id }
            // Passwords are ALWAYS sealed. "Viewable copies" only ever applies to document scans, never to logins or fields.
            drive.upsertFile(folder, "Passwords.fdenc", seal(JSONArray(pws.map { pwJson(it) }).toString().toByteArray()), OCTET,
                mapOf(P_KIND to "passwords", P_DOC_FILE to "${m.id}:passwords"), "${pws.size} saved logins, encrypted", P_DOC_FILE)
            m.photoFile?.let { files.get(it) }?.let { photo ->
                drive.upsertFile(folder, "photo.jpg.fdenc", seal(photo), OCTET, mapOf(P_KIND to "photo", P_DOC_FILE to "${m.id}:photo"), null, P_DOC_FILE)
            }
        }
        s.account?.photoFile?.let { files.get(it) }?.let { photo ->
            drive.upsertFile(root, "account-photo.jpg.fdenc", seal(photo), OCTET, mapOf(P_KIND to "account-photo", P_DOC_FILE to "account:photo"), null, P_DOC_FILE)
        }

        // Anything in Drive that no longer exists on the phone goes to Drive's bin (recoverable for 30 days).
        onProgress("Tidying up…")
        val liveDocs = s.documents.map { it.id }.toSet(); val liveMembers = s.members.map { it.id }.toSet()
        memberFolders.values.forEach { mf ->
            drive.children(mf).filter { it.mime == DriveClient.FOLDER && it.props[P_DOC] != null && it.props[P_DOC] !in liveDocs }.forEach { drive.trash(it.id); removed++ }
        }
        drive.children(root).filter { it.mime == DriveClient.FOLDER && it.props[P_MEMBER] != null && it.props[P_MEMBER] !in liveMembers }.forEach { drive.trash(it.id); removed++ }

        onProgress("Writing index…")
        val index = JSONObject()
            .put("format", 2)
            .put("account", s.account?.let { accountJson(it) } ?: JSONObject.NULL)
            .put("members", JSONArray(s.members.map { memberJson(it) }))
            .put("documents", JSONArray(s.documents.map { JSONObject().put("id", it.id).put("member", it.memberId) }))
        drive.upsertFile(root, "Family Docs vault.fdenc", seal(index.toString().toByteArray()), OCTET, mapOf(P_KIND to "index", P_DOC_FILE to "index"),
            "Index of people and documents, encrypted", P_DOC_FILE)
        Report(uploaded, unchanged, removed)
    }

    /** Rebuilds the vault from Drive using the key in the hidden app folder. Any failure → nothing on the phone changes. */
    suspend fun restore(accessToken: String, onProgress: (String) -> Unit = {}) = withContext(Dispatchers.IO) {
        val drive = DriveClient(accessToken)
        val root = drive.findByProp(P_ROOT, "1") ?: error("No Family Docs backup found in this Google account")
        val indexFile = drive.children(root.id).firstOrNull { it.props[P_KIND] == "index" } ?: error("Backup index is missing")
        val k = drive.appDataRead(KEY_FILE)?.let { parseKey(it) } ?: error("This backup's key is missing from Google Drive, so it can't be opened")
        val key = k.key
        val index = JSONObject(String(BackupCrypto.open(drive.download(indexFile.id), key)))
        fun open(b: ByteArray): ByteArray = BackupCrypto.open(b, key)

        val blobs = mutableMapOf<String, ByteArray>()
        val members = index.getJSONArray("members").let { a -> (0 until a.length()).map { a.getJSONObject(it) } }
        val docs = mutableListOf<DocumentEntity>(); val pws = mutableListOf<PasswordEntity>(); val memberEntities = mutableListOf<MemberEntity>()
        val rootChildren = drive.children(root.id)
        members.forEachIndexed { mi, mj ->
            onProgress("Restoring ${mj.getString("name")} (${mi + 1} of ${members.size})…")
            val folder = rootChildren.firstOrNull { it.props[P_MEMBER] == mj.getString("id") } ?: return@forEachIndexed
            val inside = drive.children(folder.id)
            var photoId: String? = null
            inside.firstOrNull { it.props[P_KIND] == "photo" }?.let { f -> photoId = "m-" + mj.getString("id"); blobs[photoId!!] = open(drive.download(f.id)) }
            memberEntities += memberFromJson(mj, photoId)
            inside.firstOrNull { it.props[P_KIND] == "passwords" }?.let { f ->
                val arr = JSONArray(String(open(drive.download(f.id))))
                for (i in 0 until arr.length()) pws += pwFromJson(arr.getJSONObject(i))
            }
            inside.filter { it.mime == DriveClient.FOLDER && it.props[P_DOC] != null }.forEach { df ->
                val parts = drive.children(df.id)
                val details = parts.firstOrNull { it.props[P_KIND] == "details" } ?: return@forEach
                val dj = JSONObject(String(open(drive.download(details.id))))
                val fileId = parts.firstOrNull { it.props[P_KIND] == "original" }?.let { f -> ("d-" + dj.getString("id") + "-file").also { blobs[it] = open(drive.download(f.id)) } }
                val picId = parts.firstOrNull { it.props[P_KIND] == "picture" }?.let { f -> ("d-" + dj.getString("id") + "-pic").also { blobs[it] = open(drive.download(f.id)) } }
                docs += docFromJson(dj, fileId, picId)
            }
        }
        var accountPhoto: String? = null
        rootChildren.firstOrNull { it.props[P_KIND] == "account-photo" }?.let { f -> accountPhoto = "account-photo"; blobs["account-photo"] = open(drive.download(f.id)) }
        val account = index.optJSONObject("account")?.let { accountFromJson(it, accountPhoto) }

        // Everything downloaded and decrypted: only now replace what's on the phone.
        onProgress("Saving to this phone…")
        files.clear()
        blobs.forEach { (id, data) -> files.put(data, id) }
        repo.restore(VaultRepository.Snapshot(account, memberEntities, docs, pws))
        keys.install(k)
    }

    // ---------- JSON ----------
    private fun docJson(d: DocumentEntity) = JSONObject().put("id", d.id).put("member", d.memberId).put("name", d.name).put("mime", d.fileMime ?: JSONObject.NULL)
        .put("display", d.display.name).put("fields", d.fieldsJson).put("text", d.rawText).put("ocr", d.readByOcr)
        .put("country", d.country ?: JSONObject.NULL).put("created", d.createdAt).put("updated", d.updatedAt)

    private fun docFromJson(j: JSONObject, fileId: String?, picId: String?) = DocumentEntity(
        id = j.getString("id"), memberId = j.getString("member"), name = j.getString("name"), fileId = fileId,
        fileMime = if (j.isNull("mime")) null else j.getString("mime"),
        display = runCatching { DisplayMode.valueOf(j.getString("display")) }.getOrDefault(DisplayMode.ICON), pictureFile = picId,
        fieldsJson = j.optString("fields", "[]"), rawText = j.optString("text"), readByOcr = j.optBoolean("ocr"),
        country = if (j.isNull("country")) null else j.optString("country"), createdAt = j.optLong("created"), updatedAt = j.optLong("updated"),
    )

    private fun memberJson(m: MemberEntity) = JSONObject().put("id", m.id).put("name", m.name).put("relation", m.relation)
        .put("self", m.isSelf).put("shared", m.shared).put("created", m.createdAt).put("avatar", m.avatar ?: JSONObject.NULL)
    private fun memberFromJson(j: JSONObject, photo: String?) = MemberEntity(j.getString("id"), j.getString("name"), j.getString("relation"), photo,
        j.optBoolean("self"), j.optLong("created"), j.optBoolean("shared"), if (j.isNull("avatar")) null else j.optString("avatar"))

    private fun pwJson(p: PasswordEntity) = JSONObject().put("id", p.id).put("member", p.memberId).put("site", p.site).put("user", p.username).put("pass", p.password).put("created", p.createdAt)
    private fun pwFromJson(j: JSONObject) = PasswordEntity(j.getString("id"), j.getString("member"), j.getString("site"), j.getString("user"), j.getString("pass"), j.optLong("created"))

    private fun accountJson(a: AccountEntity) = JSONObject().put("name", a.name).put("cc", a.countryCode).put("phone", a.phone).put("email", a.backupEmail)
        .put("google", a.googleLinked).put("countries", a.countries)
    private fun accountFromJson(j: JSONObject, photo: String?) = AccountEntity(name = j.getString("name"), countryCode = j.getString("cc"), phone = j.getString("phone"),
        backupEmail = j.getString("email"), photoFile = photo, googleLinked = j.optBoolean("google"), countries = j.optString("countries", "IN,GR"))

    private fun hex(argb: Long) = "#%06X".format(argb and 0xFFFFFF)

    companion object {
        /** drive.file: non-sensitive scope, no Google verification screen, and the app can't see the rest of the user's Drive. */
        const val KEY_FILE = "family-docs-backup-key.json"
        private const val OCTET = "application/octet-stream"
        // label keys (Drive appProperties)
        const val P_ROOT = "fd_root"; const val P_MEMBER = "fd_member"; const val P_MEMBER_OF = "fd_member_of"; const val P_DOC = "fd_doc"
        const val P_DOC_FILE = "fd_file"; const val P_KIND = "fd_kind"; const val P_TYPE = "fd_type"; const val P_COUNTRY = "fd_country"
        const val P_EXPIRY = "fd_expiry"; const val P_UPDATED = "fd_updated"
    }
}
