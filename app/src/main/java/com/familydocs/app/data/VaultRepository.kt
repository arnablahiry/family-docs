package com.familydocs.app.data

import com.familydocs.app.domain.Countries
import com.familydocs.app.domain.DocField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.UUID

const val DEFAULT_SELF_AVATAR = "char_blue_short"
val CHARACTER_KEYS = listOf("char_blue_short", "char_peach_bun", "char_lilac_long", "char_mint_glasses", "char_sand_bun", "char_rose_long", "char_sky_short", "char_slate_glasses")

class VaultRepository(private val dao: VaultDao, val files: FileStore) {
    val account: Flow<AccountEntity?> = dao.account()
    val members: Flow<List<MemberEntity>> = dao.members()
    val allDocuments: Flow<List<DocumentEntity>> = dao.allDocuments()
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
        // Every family gets one Common vault (rent, bills, Wi-Fi...) that all account holders share.
        if (dao.sharedNow() == null) {
            dao.upsertMember(MemberEntity(UUID.randomUUID().toString(), "Common", "Shared", photoFile = null, createdAt = 0L, shared = true))
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
                ?: MemberEntity(UUID.randomUUID().toString(), name, relation, photoId, avatar = chosenAvatar),
        )
        if (old?.isSelf == true) dao.accountNow()?.let { a ->
            if (a.photoFile != photoId) { a.photoFile?.takeIf { it != old.photoFile && it != photoId }?.let { files.delete(it) } }
            dao.upsertAccount(a.copy(name = name, photoFile = photoId))
        }
    }

    /** First character nobody uses yet, so a new person looks different from the others. */
    suspend fun nextCharacter(): String {
        val used = dao.membersNow().mapNotNull { it.avatar }.toSet()
        return CHARACTER_KEYS.firstOrNull { it !in used } ?: CHARACTER_KEYS[used.size % CHARACTER_KEYS.size]
    }

    suspend fun deleteMember(id: String) = withContext(Dispatchers.IO) {
        if (dao.memberNow(id)?.shared == true) return@withContext // the Common vault can't be removed
        dao.documentsNow(id).forEach { files.delete(it.fileId); files.delete(it.pictureFile) }
        dao.memberNow(id)?.let { files.delete(it.photoFile) }
        dao.deleteMember(id)
    }

    suspend fun saveDocument(doc: DocumentEntity, fields: List<DocField>) = withContext(Dispatchers.IO) {
        dao.upsertDocument(doc.copy(fieldsJson = encodeFields(fields), updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteDocument(id: String) = withContext(Dispatchers.IO) {
        dao.documentNow(id)?.let { files.delete(it.fileId); files.delete(it.pictureFile) }
        dao.deleteDocument(id)
    }

    suspend fun savePassword(memberId: String, site: String, user: String, pass: String, id: String? = null) =
        dao.upsertPassword(PasswordEntity(id ?: UUID.randomUUID().toString(), memberId, site, user, pass))

    suspend fun deletePassword(id: String) = dao.deletePassword(id)

    // ---- used by backup ----
    suspend fun snapshot() = withContext(Dispatchers.IO) {
        Snapshot(dao.accountNow(), dao.membersNow(), dao.allDocumentsNow(), dao.allPasswordsNow())
    }

    /** Marks the account as linked to this Google account (its Drive holds the backups). */
    suspend fun linkGoogle(email: String) = withContext(Dispatchers.IO) {
        dao.accountNow()?.let { dao.upsertAccount(it.copy(backupEmail = email, googleLinked = true)) }
    }

    /** Removes every account, person, document and password from this phone. */
    suspend fun wipe() = withContext(Dispatchers.IO) { dao.replaceAll(null, emptyList(), emptyList(), emptyList()); files.clear() }

    suspend fun restore(s: Snapshot) = withContext(Dispatchers.IO) { dao.replaceAll(s.account, s.members, s.documents, s.passwords) }

    data class Snapshot(val account: AccountEntity?, val members: List<MemberEntity>, val documents: List<DocumentEntity>, val passwords: List<PasswordEntity>)
}
