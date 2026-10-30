package com.familydocs.app.data

import android.content.Context
import android.graphics.Bitmap
import com.familydocs.app.ocr.Images
import org.json.JSONObject
import java.security.MessageDigest

/**
 * The originals behind cropped pictures (profile photos, group and event pictures), so "Edit photo" can reopen the
 * whole photo with the framing it had instead of only the saved circle. Kept encrypted in [FileStore] on this phone
 * only (never backed up or shared); looked up by the saved picture's contents, so it works wherever that picture is
 * used. Pictures saved before this (or received from other phones) are edited from the saved square itself.
 */
class PhotoSources(context: Context, private val files: FileStore) {
    private val prefs = context.getSharedPreferences("photo_sources", Context.MODE_PRIVATE)

    /** A photo to edit: the [bitmap] to crop and the framing to start from (pan as a fraction of the crop circle). */
    class Source(val bitmap: Bitmap, val zoom: Float, val panX: Float, val panY: Float, val originalId: String?)

    private fun key(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).take(16).joinToString("") { "%02x".format(it) }

    /** The picture in [photoFile], ready to re-crop. Slow (decrypts): call off the main thread. */
    fun load(photoFile: String): Source? {
        val saved = files.get(photoFile) ?: return null
        prefs.getString(key(saved), null)?.let { s ->
            runCatching {
                val o = JSONObject(s)
                val orig = files.get(o.getString("orig"))?.let { Images.decode(it, 1600) }
                if (orig != null) return Source(orig, o.optDouble("z", 1.0).toFloat(), o.optDouble("x").toFloat(), o.optDouble("y").toFloat(), o.getString("orig"))
            }
        }
        return Images.decode(saved, 1600)?.let { Source(it, 1f, 0f, 0f, null) }
    }

    /**
     * After a cropped picture ([saved], the exact bytes stored) is saved: keeps [original] (or reuses [originalId] when
     * it was an edit of an earlier one) with the framing, for the next edit. Slow: call off the main thread.
     */
    fun keep(saved: ByteArray, original: Bitmap, zoom: Float, panX: Float, panY: Float, originalId: String?) {
        val orig = originalId?.takeIf { files.get(it) != null } ?: files.put(Images.jpeg(original, 90))
        val order = (prefs.getString(ORDER, "") ?: "").split(',').filter { it.isNotEmpty() }.toMutableList()
        val k = key(saved)
        prefs.edit().putString(k, JSONObject().put("orig", orig).put("z", zoom.toDouble()).put("x", panX.toDouble()).put("y", panY.toDouble()).toString()).apply()
        order.remove(k); order += k
        // only the most recent ones: older originals (pictures long since replaced) are deleted
        while (order.size > MAX) {
            val old = order.removeAt(0)
            val o = prefs.getString(old, null)?.let { runCatching { JSONObject(it).getString("orig") }.getOrNull() }
            prefs.edit().remove(old).apply()
            if (o != null && o != orig && order.none { prefs.getString(it, null)?.contains("\"$o\"") == true }) files.delete(o)
        }
        prefs.edit().putString(ORDER, order.joinToString(",")).apply()
    }

    /** Sign out / delete account. */
    fun clear() { prefs.edit().clear().apply() }

    private companion object { const val ORDER = "order"; const val MAX = 60 }
}
