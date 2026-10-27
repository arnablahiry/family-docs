package com.familydocs.app.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultDao {
    @Query("SELECT * FROM account WHERE id = 1") fun account(): Flow<AccountEntity?>
    @Query("SELECT * FROM account WHERE id = 1") suspend fun accountNow(): AccountEntity?
    @Upsert suspend fun upsertAccount(a: AccountEntity)

    @Query("SELECT * FROM members ORDER BY shared DESC, isSelf DESC, createdAt") fun members(): Flow<List<MemberEntity>>
    @Query("SELECT * FROM members ORDER BY shared DESC, isSelf DESC, createdAt") suspend fun membersNow(): List<MemberEntity>
    @Query("SELECT * FROM members WHERE id = :id") fun member(id: String): Flow<MemberEntity?>
    @Query("SELECT * FROM members WHERE id = :id") suspend fun memberNow(id: String): MemberEntity?
    @Query("SELECT * FROM members WHERE isSelf = 1 LIMIT 1") suspend fun selfNow(): MemberEntity?
    @Query("SELECT * FROM members WHERE shared = 1 LIMIT 1") suspend fun sharedNow(): MemberEntity?
    @Upsert suspend fun upsertMember(m: MemberEntity)
    @Query("DELETE FROM members WHERE id = :id") suspend fun deleteMemberRow(id: String)
    @Query("DELETE FROM documents WHERE memberId = :id") suspend fun deleteDocumentsOf(id: String)
    @Query("DELETE FROM passwords WHERE memberId = :id") suspend fun deletePasswordsOf(id: String)

    @Transaction
    suspend fun deleteMember(id: String) { deleteDocumentsOf(id); deletePasswordsOf(id); deleteMemberRow(id) }

    @Query("SELECT * FROM documents WHERE memberId = :memberId ORDER BY createdAt") fun documents(memberId: String): Flow<List<DocumentEntity>>
    @Query("SELECT * FROM documents ORDER BY createdAt") fun allDocuments(): Flow<List<DocumentEntity>>
    @Query("SELECT * FROM documents") suspend fun allDocumentsNow(): List<DocumentEntity>
    @Query("SELECT * FROM documents WHERE memberId = :memberId") suspend fun documentsNow(memberId: String): List<DocumentEntity>
    @Query("SELECT * FROM documents WHERE id = :id") fun document(id: String): Flow<DocumentEntity?>
    @Query("SELECT * FROM documents WHERE id = :id") suspend fun documentNow(id: String): DocumentEntity?
    @Upsert suspend fun upsertDocument(d: DocumentEntity)
    @Query("DELETE FROM documents WHERE id = :id") suspend fun deleteDocument(id: String)
    @Query("SELECT memberId, COUNT(*) AS n FROM documents GROUP BY memberId") fun documentCounts(): Flow<List<MemberCount>>

    @Query("SELECT * FROM passwords WHERE memberId = :memberId ORDER BY site COLLATE NOCASE") fun passwords(memberId: String): Flow<List<PasswordEntity>>
    @Query("SELECT * FROM passwords") suspend fun allPasswordsNow(): List<PasswordEntity>
    @Upsert suspend fun upsertPassword(p: PasswordEntity)
    @Query("DELETE FROM passwords WHERE id = :id") suspend fun deletePassword(id: String)

    @Query("DELETE FROM documents") suspend fun clearDocuments()
    @Query("DELETE FROM passwords") suspend fun clearPasswords()
    @Query("DELETE FROM members") suspend fun clearMembers()
    @Query("DELETE FROM account") suspend fun clearAccount()

    @Transaction
    suspend fun replaceAll(account: AccountEntity?, members: List<MemberEntity>, docs: List<DocumentEntity>, pws: List<PasswordEntity>) {
        clearDocuments(); clearPasswords(); clearMembers()
        clearAccount()
        account?.let { upsertAccount(it) }
        members.forEach { upsertMember(it) }
        docs.forEach { upsertDocument(it) }
        pws.forEach { upsertPassword(it) }
    }
}
