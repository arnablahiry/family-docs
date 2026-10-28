package com.familydocs.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.familydocs.app.domain.DocField
import org.json.JSONArray
import org.json.JSONObject

/** One stored file of a document (encrypted in [FileStore]). */
data class Attachment(val id: String, val mime: String) {
    companion object {
        fun encode(list: List<Attachment>): String? = if (list.isEmpty()) null
            else JSONArray(list.map { JSONObject().put("id", it.id).put("mime", it.mime) }).toString()
        fun decode(json: String?): List<Attachment> = runCatching {
            val a = JSONArray(json ?: return emptyList())
            (0 until a.length()).map { a.getJSONObject(it).let { o -> Attachment(o.getString("id"), o.optString("mime", "image/jpeg")) } }
        }.getOrDefault(emptyList())
    }
}

/** A group (e.g. your family): its own people, Common vault and events, and a code to join it. Database version 7. */
@Entity(tableName = "vault_groups")
data class GroupEntity(
    @PrimaryKey val id: String,
    val name: String,
    val code: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "account")
data class AccountEntity(
    @PrimaryKey val id: Int = 1,
    val name: String,
    val countryCode: String,
    val phone: String,
    val backupEmail: String,
    val photoFile: String?,
    val googleLinked: Boolean,
    /** Countries the family keeps documents for, e.g. "IN,GR". Drives the filter pills and "Issued by" choices. */
    val countries: String = "IN,GR",
)

@Entity(tableName = "members")
data class MemberEntity(
    @PrimaryKey val id: String,
    val name: String,
    val relation: String,
    val photoFile: String?,
    val isSelf: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    /** The "Common" vault: one per family, every account holder can see and edit it. */
    val shared: Boolean = false,
    /** Illustrated character used when there's no photo (e.g. "char_blue_short"). Added in database version 2. */
    val avatar: String? = null,
    /** The group this person belongs to; null for you (you're in every group). Added in database version 7. */
    val groupId: String? = null,
)

enum class DisplayMode { ICON, FROM_FILE, CUSTOM }

@Entity(
    tableName = "documents",
    foreignKeys = [ForeignKey(entity = MemberEntity::class, parentColumns = ["id"], childColumns = ["memberId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("memberId")],
)
data class DocumentEntity(
    @PrimaryKey val id: String,
    val memberId: String,
    val name: String,
    /** Encrypted original (image or PDF) in [FileStore]. */
    val fileId: String?,
    val fileMime: String?,
    val display: DisplayMode = DisplayMode.ICON,
    /** Square thumbnail used when [display] is FROM_FILE or CUSTOM. */
    val pictureFile: String? = null,
    val fieldsJson: String = "[]",
    val rawText: String = "",
    val readByOcr: Boolean = false,
    /** ISO alpha-2 issuing country ("IN", "GR"…), "" for none, null = work it out from the fields. */
    val country: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    /** Set for a ticket or booking added only for an event: it then shows in that event, not in the vault. Added in database version 3. */
    val eventId: String? = null,
    /** Faint country flag behind the card art: null = the type's default (on for national ID cards). Added in database version 4. */
    val showFlag: Boolean? = null,
    /** Files after the first one ([fileId]), in order: JSON [{"id":…,"mime":…}]. Added in database version 5. */
    val moreFiles: String? = null,
    /** Position in the person's wallet, set by hold-and-drag (null = in the order added). Added in database version 6. */
    val sortOrder: Int? = null,
) {
    /** Every file of the document (front, back, more pages…), in order. */
    val attachments: List<Attachment> get() = listOfNotNull(fileId?.let { Attachment(it, fileMime ?: "image/jpeg") }) + Attachment.decode(moreFiles)
    val fields: List<DocField> get() = decodeFields(fieldsJson)
    /** Whether the card shows its issuing country's flag. */
    val flagShown: Boolean get() = showFlag ?: com.familydocs.app.domain.DocTypes.flagByDefault(name)
    val issuer: String get() = country ?: com.familydocs.app.domain.Countries.infer(name, fields, rawText)
}

@Entity(
    tableName = "passwords",
    foreignKeys = [ForeignKey(entity = MemberEntity::class, parentColumns = ["id"], childColumns = ["memberId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("memberId")],
)
data class PasswordEntity(
    @PrimaryKey val id: String,
    val memberId: String,
    val site: String,
    val username: String,
    val password: String,
    val createdAt: Long = System.currentTimeMillis(),
)

data class MemberCount(val memberId: String, val n: Int)

fun encodeFields(fields: List<DocField>): String = JSONArray().apply {
    fields.forEach { f ->
        put(JSONObject().put("label", f.label).put("value", f.value).put("sensitive", f.sensitive).put("auto", f.auto)
            .apply { f.verified?.let { put("verified", it) } })
    }
}.toString()

fun decodeFields(json: String): List<DocField> = runCatching {
    val a = JSONArray(json)
    (0 until a.length()).map { i ->
        val o = a.getJSONObject(i)
        DocField(
            label = o.optString("label"), value = o.optString("value"), sensitive = o.optBoolean("sensitive"),
            auto = o.optBoolean("auto"), verified = if (o.has("verified")) o.getBoolean("verified") else null,
        )
    }
}.getOrDefault(emptyList())

/**
 * A trip or occasion (database version 3): picture, dates, who's going, who can see it, the vault documents it
 * needs (pinned, they stay in their owner's vault) and a reminder. Its own tickets/bookings are documents with [DocumentEntity.eventId].
 */
@Entity(tableName = "events")
data class EventEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** One of the event pictures ("mountain", "beach", "heritage"…), used when there's no photo. */
    val art: String,
    val photoFile: String? = null,
    /** ISO dates, "2026-11-20". */
    val startDay: String,
    val endDay: String? = null,
    /** Member ids, comma separated. */
    val people: String = "",
    /** Member ids who can see the event; the creator is always included. */
    val visibleTo: String = "",
    /** Pinned vault document ids, comma separated. */
    val pins: String = "",
    /** Days before [startDay] to notify, or -1 for no reminder. */
    val remindDays: Int = -1,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    /** The group the event belongs to. Added in database version 7. */
    val groupId: String? = null,
) {
    val peopleIds: List<String> get() = people.split(',').filter { it.isNotBlank() }
    val visibleIds: List<String> get() = visibleTo.split(',').filter { it.isNotBlank() }
    val pinIds: List<String> get() = pins.split(',').filter { it.isNotBlank() }
}
