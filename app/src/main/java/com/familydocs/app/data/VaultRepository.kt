package com.familydocs.app.data

import com.familydocs.app.domain.Countries
import com.familydocs.app.domain.DocField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.withContext
import java.util.UUID

const val DEFAULT_SELF_AVATAR = "char_blue_short"
/** The family this app was made for: whoever signs up as one of them gets the other two, with the right relation and picture. */
data class FamilyPreset(val name: String, val avatar: String)
val FAMILY_PRESET = listOf(FamilyPreset("Arnab", "char_blue_short"), FamilyPreset("Mohua", "char_peach_bun"), FamilyPreset("Teesta", "char_lilac_long"))
private val PRESET_RELATIONS = mapOf(
    "Arnab" to mapOf("Mohua" to "Mom", "Teesta" to "Sister"),
    "Mohua" to mapOf("Arnab" to "Son", "Teesta" to "Daughter"),
    "Teesta" to mapOf("Arnab" to "Brother", "Mohua" to "Mom"),
)
fun presetFor(name: String): FamilyPreset? = FAMILY_PRESET.firstOrNull { name.trim().substringBefore(' ').equals(it.name, ignoreCase = true) }

val CHARACTER_KEYS = listOf("char_blue_short", "char_peach_bun", "char_lilac_long", "char_mint_glasses", "char_sand_bun", "char_rose_long", "char_sky_short", "char_slate_glasses")

class VaultRepository(private val dao: VaultDao, val files: FileStore) {
    /** Called after anything the backup covers is added, changed or deleted (AppContainer queues a Drive backup). */
    var onChanged: () -> Unit = {}

    val account: Flow<AccountEntity?> = dao.account()

    // ---- groups: the screens show the current group's people, documents and events ----
    /** The group being shown (AppContainer.selectGroup changes it and remembers it). */
    val groupId = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val groups: Flow<List<GroupEntity>> = dao.groups()
    suspend fun groupsNow() = dao.groupsNow()
    fun membersOf(g: String?): Flow<List<MemberEntity>> = dao.membersIn(g)
    fun eventsOf(g: String?): Flow<List<EventEntity>> = dao.eventsIn(g)
    /** Documents of group [g]'s people (yours included). */
    fun documentsOf(g: String?): Flow<List<DocumentEntity>> = kotlinx.coroutines.flow.combine(dao.allDocuments(), dao.membersIn(g)) { d, m ->
        val ids = m.map { it.id }.toSet(); d.filter { it.memberId in ids }
    }
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val members: Flow<List<MemberEntity>> = groupId.flatMapLatest { membersOf(it) }
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val allDocuments: Flow<List<DocumentEntity>> = groupId.flatMapLatest { documentsOf(it) }

    /** A new group with its own Common vault and a fresh code. */
    suspend fun createGroup(name: String): GroupEntity = withContext(Dispatchers.IO) {
        val g = GroupEntity(UUID.randomUUID().toString(), name.trim().ifBlank { "Family" }, com.familydocs.app.domain.GroupCodes.new())
        dao.upsertGroup(g)
        dao.upsertMember(MemberEntity(UUID.randomUUID().toString(), "Common", "Shared", photoFile = null, createdAt = 0L, shared = true, groupId = g.id))
        onChanged()
        g
    }
    suspend fun groupByCode(code: String): GroupEntity? = dao.groupsNow().firstOrNull {
        com.familydocs.app.domain.GroupCodes.normalize(it.code) == com.familydocs.app.domain.GroupCodes.normalize(code)
    }
    val documentCounts: Flow<List<MemberCount>> = dao.documentCounts()

    fun member(id: String) = dao.member(id)
    fun documents(memberId: String) = dao.documents(memberId)
    fun document(id: String) = dao.document(id)
    fun passwords(memberId: String) = dao.passwords(memberId)

    suspend fun accountNow() = dao.accountNow()
    suspend fun selfNow() = dao.selfNow()
    suspend fun documentsCount(memberId: String) = dao.documentsNow(memberId).size
    fun self() = dao.self()
    suspend fun documentNow(id: String) = dao.documentNow(id)
    suspend fun memberNow(id: String) = dao.memberNow(id)

    /**
     * Creates or updates the account and the matching "You" member. Your profile photo is ONE file shared by the
     * account and your circle on the home screen, so changing it in either place changes both.
     * [photo] = new picture, [removePhoto] = go back to the character, otherwise the current photo is kept.
     */
    suspend fun saveAccount(
        name: String, cc: String, phone: String, email: String, photo: ByteArray?, google: Boolean, countries: List<String>,
        removePhoto: Boolean = false, avatar: String? = null,
    ) = withContext(Dispatchers.IO) {
        val oldAccount = dao.accountNow()
        val self = dao.selfNow()
        val oldPhotos = setOfNotNull(oldAccount?.photoFile, self?.photoFile)
        val photoId = when {
            photo != null -> files.put(photo)
            removePhoto -> null
            else -> self?.photoFile ?: oldAccount?.photoFile
        }
        oldPhotos.filter { it != photoId }.forEach { files.delete(it) }
        dao.upsertAccount(AccountEntity(name = name, countryCode = cc, phone = phone, backupEmail = email, photoFile = photoId, googleLinked = google,
            countries = Countries.encode(countries)))
        dao.upsertMember(
            (self ?: MemberEntity(id = UUID.randomUUID().toString(), name = name, relation = "You", photoFile = null, isSelf = true, avatar = DEFAULT_SELF_AVATAR))
                .let { it.copy(name = name, photoFile = photoId, avatar = avatar ?: it.avatar ?: DEFAULT_SELF_AVATAR) },
        )
        // Every group gets one Common vault (rent, bills, Wi-Fi...) that all its account holders share.
        if (dao.sharedIn(groupId.value) == null) {
            dao.upsertMember(MemberEntity(UUID.randomUUID().toString(), "Common", "Shared", photoFile = null, createdAt = 0L, shared = true, groupId = groupId.value))
        }
        if (self == null) ensureFamily()
    }

    /**
     * Adds the rest of the preset family (see [FAMILY_PRESET]) if you are one of them and they aren't here yet,
     * and turns a plain "Family" relation into the right one (Mom, Sister…). Never touches anyone else.
     */
    suspend fun ensureFamily() = withContext(Dispatchers.IO) {
        val me = dao.selfNow() ?: return@withContext
        val preset = presetFor(me.name) ?: return@withContext
        val rel = PRESET_RELATIONS.getValue(preset.name)
        val g = groupId.value
        val people = dao.membersNow().filter { it.groupId == g }
        var t = System.currentTimeMillis()
        FAMILY_PRESET.filter { it != preset }.forEach { p ->
            val existing = people.firstOrNull { !it.isSelf && !it.shared && it.name.trim().equals(p.name, ignoreCase = true) }
            when {
                existing == null -> dao.upsertMember(MemberEntity(UUID.randomUUID().toString(), p.name, rel.getValue(p.name), photoFile = null, createdAt = t++, avatar = p.avatar, groupId = g))
                existing.relation == "Family" -> dao.upsertMember(existing.copy(relation = rel.getValue(p.name)))
            }
        }
    }

    /** Add or edit a person. For yourself this also updates the account photo (same file). */
    suspend fun saveMember(id: String?, name: String, relation: String, photo: ByteArray?, keepPhoto: Boolean, avatar: String?) = withContext(Dispatchers.IO) {
        val old = id?.let { dao.memberNow(it) }
        val photoId = when {
            photo != null -> files.put(photo)
            keepPhoto -> old?.photoFile
            else -> null
        }
        old?.photoFile?.takeIf { it != photoId }?.let { files.delete(it) }
        val chosenAvatar = avatar ?: old?.avatar ?: nextCharacter()
        dao.upsertMember(
            old?.copy(name = name, relation = relation, photoFile = photoId, avatar = chosenAvatar)
                ?: MemberEntity(UUID.randomUUID().toString(), name, relation, photoId, avatar = chosenAvatar, groupId = groupId.value),
        )
        if (old?.isSelf == true) dao.accountNow()?.let { a ->
            if (a.photoFile != photoId) { a.photoFile?.takeIf { it != old.photoFile && it != photoId }?.let { files.delete(it) } }
            dao.upsertAccount(a.copy(name = name, photoFile = photoId))
        }
        onChanged()
    }

    /** First character nobody uses yet, so a new person looks different from the others. */
    suspend fun nextCharacter(): String {
        val used = dao.membersNow().mapNotNull { it.avatar }.toSet()
        return CHARACTER_KEYS.firstOrNull { it !in used } ?: CHARACTER_KEYS[used.size % CHARACTER_KEYS.size]
    }

    suspend fun deleteMember(id: String) = withContext(Dispatchers.IO) {
        if (dao.memberNow(id)?.shared == true) return@withContext // the Common vault can't be removed
        dao.documentsNow(id).forEach { it.attachments.forEach { a -> files.delete(a.id) }; files.delete(it.pictureFile) }
        dao.memberNow(id)?.let { files.delete(it.photoFile) }
        dao.deleteMember(id)
        onChanged()
    }

    suspend fun saveDocument(doc: DocumentEntity, fields: List<DocField>) = withContext(Dispatchers.IO) {
        dao.upsertDocument(doc.copy(fieldsJson = encodeFields(fields), updatedAt = System.currentTimeMillis()))
        onChanged()
    }

    /** New order of a person's cards after hold-and-drag: [ids] top to bottom. */
    suspend fun reorderDocuments(ids: List<String>) = withContext(Dispatchers.IO) {
        ids.forEachIndexed { i, id -> dao.setDocumentOrder(id, i) }
        onChanged()
    }

    suspend fun deleteDocument(id: String) = withContext(Dispatchers.IO) {
        dao.documentNow(id)?.let { it.attachments.forEach { a -> files.delete(a.id) }; files.delete(it.pictureFile) }
        dao.deleteDocument(id)
        onChanged()
    }

    suspend fun savePassword(memberId: String, site: String, user: String, pass: String, id: String? = null) {
        dao.upsertPassword(PasswordEntity(id ?: UUID.randomUUID().toString(), memberId, site, user, pass))
        onChanged()
    }

    suspend fun deletePassword(id: String) { dao.deletePassword(id); onChanged() }

    // ---- events ----
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val events: Flow<List<EventEntity>> = groupId.flatMapLatest { eventsOf(it) }
    fun event(id: String) = dao.event(id)
    fun eventDocuments(eventId: String) = dao.eventDocuments(eventId)
    suspend fun eventNow(id: String) = dao.eventNow(id)
    suspend fun saveEvent(e: EventEntity) = withContext(Dispatchers.IO) { dao.upsertEvent(if (e.groupId == null) e.copy(groupId = groupId.value) else e); onChanged() }
    suspend fun setEventPins(id: String, pins: List<String>) = withContext(Dispatchers.IO) {
        dao.eventNow(id)?.let { dao.upsertEvent(it.copy(pins = pins.distinct().joinToString(","))) }
        onChanged()
    }
    /** Deletes the event and the tickets/bookings added only for it. Pinned vault documents are untouched. */
    suspend fun deleteEvent(id: String) = withContext(Dispatchers.IO) {
        dao.eventDocumentsNow(id).forEach { it.attachments.forEach { a -> files.delete(a.id) }; files.delete(it.pictureFile) }
        dao.eventNow(id)?.let { files.delete(it.photoFile) }
        dao.deleteEventDocuments(id)
        dao.deleteEventRow(id)
        onChanged()
    }

    // ---- used by backup ----
    suspend fun snapshot() = withContext(Dispatchers.IO) {
        Snapshot(dao.accountNow(), dao.membersNow(), dao.allDocumentsNow(), dao.allPasswordsNow(), dao.eventsNow(), dao.groupsNow())
    }

    /** Marks the account as linked to this Google account (its Drive holds the backups). */
    suspend fun linkGoogle(email: String) = withContext(Dispatchers.IO) {
        dao.accountNow()?.let { dao.upsertAccount(it.copy(backupEmail = email, googleLinked = true)) }
    }

    /** Removes every account, person, document and password from this phone. */
    suspend fun wipe() = withContext(Dispatchers.IO) { dao.replaceAll(null, emptyList(), emptyList(), emptyList()); files.clear() }

    suspend fun restore(s: Snapshot) = withContext(Dispatchers.IO) {
        var snap = s
        // A backup from before groups: it all becomes the group "Family", like the database upgrade does.
        if (s.groups.isEmpty() && s.members.any { !it.isSelf }) {
            val g = GroupEntity(UUID.randomUUID().toString(), "Family", com.familydocs.app.domain.GroupCodes.new())
            snap = s.copy(groups = listOf(g), members = s.members.map { if (it.isSelf) it else it.copy(groupId = it.groupId ?: g.id) },
                events = s.events.map { it.copy(groupId = it.groupId ?: g.id) })
        }
        dao.replaceAll(snap.account, snap.members, snap.documents, snap.passwords, snap.events, snap.groups)
        if (snap.groups.none { it.id == groupId.value }) groupId.value = snap.groups.firstOrNull()?.id
    }

    data class Snapshot(
        val account: AccountEntity?, val members: List<MemberEntity>, val documents: List<DocumentEntity>, val passwords: List<PasswordEntity>,
        val events: List<EventEntity> = emptyList(),
        val groups: List<GroupEntity> = emptyList(),
    )
}
