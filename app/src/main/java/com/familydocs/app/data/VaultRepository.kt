package com.familydocs.app.data

import com.familydocs.app.domain.Countries
import com.familydocs.app.domain.DocField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.UUID

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
    suspend fun documentNow(id: String) = dao.documentNow(id)
    suspend fun memberNow(id: String) = dao.memberNow(id)

    /** Creates or updates the account and the matching "You" member. */
    suspend fun saveAccount(name: String, cc: String, phone: String, email: String, photo: ByteArray?, google: Boolean, countries: List<String>) = withContext(Dispatchers.IO) {
        val old = dao.accountNow()
        val photoId = photo?.let { files.put(it) } ?: old?.photoFile
        if (photo != null) files.delete(old?.photoFile?.takeIf { it != photoId })
        dao.upsertAccount(AccountEntity(name = name, countryCode = cc, phone = phone, backupEmail = email, photoFile = photoId, googleLinked = google, countries = Countries.encode(countries)))
        val self = dao.selfNow()
        // The "You" member keeps its own copy of the photo so it can be changed independently later.
        val memberPhoto = if (photo != null) files.put(photo).also { files.delete(self?.photoFile) } else self?.photoFile
        dao.upsertMember(
            (self ?: MemberEntity(id = UUID.randomUUID().toString(), name = name, relation = "You", photoFile = null, isSelf = true))
                .copy(name = name, photoFile = memberPhoto),
        )
        // Every family gets one Common vault (rent, bills, Wi-Fi...) that all account holders share.
        if (dao.sharedNow() == null) {
            dao.upsertMember(MemberEntity(UUID.randomUUID().toString(), "Common", "Shared", photoFile = null, createdAt = 0L, shared = true))
        }
    }

    suspend fun saveMember(id: String?, name: String, relation: String, photo: ByteArray?, keepPhoto: Boolean) = withContext(Dispatchers.IO) {
        val old = id?.let { dao.memberNow(it) }
        val photoId = when {
            photo != null -> files.put(photo).also { files.delete(old?.photoFile) }
            keepPhoto -> old?.photoFile
            else -> null.also { files.delete(old?.photoFile) }
        }
        dao.upsertMember(
            old?.copy(name = name, relation = relation, photoFile = photoId)
                ?: MemberEntity(UUID.randomUUID().toString(), name, relation, photoId),
        )
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

    suspend fun restore(s: Snapshot) = withContext(Dispatchers.IO) { dao.replaceAll(s.account, s.members, s.documents, s.passwords) }

    data class Snapshot(val account: AccountEntity?, val members: List<MemberEntity>, val documents: List<DocumentEntity>, val passwords: List<PasswordEntity>)
}
