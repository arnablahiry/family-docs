package com.familydocs.app.backup

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** Minimal Google Drive v3 REST client (scope drive.file: the app only ever sees files it created). */
class DriveClient(private val token: String) {
    data class Item(val id: String, val name: String, val mime: String, val props: Map<String, String>)

    private val http = OkHttpClient.Builder().readTimeout(2, TimeUnit.MINUTES).writeTimeout(2, TimeUnit.MINUTES).build()

    private fun call(req: Request.Builder): String =
        http.newCall(req.header("Authorization", "Bearer $token").build()).execute().use { r ->
            val body = r.body?.string().orEmpty()
            check(r.isSuccessful) { "Drive ${r.code}: ${body.take(200)}" }
            body
        }

    private fun parse(o: JSONObject): Item {
        val p = o.optJSONObject("appProperties")
        return Item(o.getString("id"), o.optString("name"), o.optString("mimeType"), p?.keys()?.asSequence()?.associateWith { p.getString(it) } ?: emptyMap())
    }

    fun list(q: String): List<Item> {
        val out = mutableListOf<Item>()
        var page: String? = null
        do {
            val url = "$FILES?pageSize=1000&fields=nextPageToken,files(id,name,mimeType,appProperties)&q=" +
                URLEncoder.encode("$q and trashed = false", "UTF-8") + (page?.let { "&pageToken=$it" } ?: "")
            val j = JSONObject(call(Request.Builder().url(url)))
            val arr = j.getJSONArray("files")
            for (i in 0 until arr.length()) out += parse(arr.getJSONObject(i))
            page = j.optString("nextPageToken").ifEmpty { null }
        } while (page != null)
        return out
    }

    fun children(parent: String) = list("'$parent' in parents")

    fun findByProp(key: String, value: String, parent: String? = null): Item? =
        list("appProperties has { key='$key' and value='${value.replace("'", "\\'")}' }" + (parent?.let { " and '$it' in parents" } ?: "")).firstOrNull()

    private fun meta(name: String, parent: String?, props: Map<String, String>, description: String?, mime: String?, color: String?) = JSONObject().apply {
        put("name", name)
        parent?.let { put("parents", JSONArray().put(it)) }
        put("appProperties", JSONObject(props))
        description?.let { put("description", it) }
        mime?.let { put("mimeType", it) }
        color?.let { put("folderColorRgb", it) }
    }

    /** Finds the folder tagged with [props] under [parent], creating it if needed; keeps name/description/colour current. */
    fun folder(name: String, parent: String?, props: Map<String, String>, description: String? = null, color: String? = null): String {
        val (k, v) = props.entries.first()
        val existing = findByProp(k, v, parent)
        if (existing != null) {
            if (existing.name != name || description != null || color != null) {
                call(Request.Builder().url("$FILES/${existing.id}").patch(
                    JSONObject().put("name", name).apply { description?.let { put("description", it) }; color?.let { put("folderColorRgb", it) } }
                        .toString().toRequestBody(JSON)))
            }
            return existing.id
        }
        return JSONObject(call(Request.Builder().url("$FILES?fields=id").post(
            meta(name, parent, props, description, FOLDER, color).toString().toRequestBody(JSON)))).getString("id")
    }

    /** Creates or overwrites a file (matched by appProperties [matchKey]). */
    fun upsertFile(parent: String, name: String, bytes: ByteArray, mime: String, props: Map<String, String>, description: String?, matchKey: String): String {
        val existing = findByProp(matchKey, props.getValue(matchKey), parent)
        if (existing != null) {
            val body = MultipartBody.Builder().setType("multipart/related".toMediaType())
                .addPart(JSONObject().put("name", name).put("appProperties", JSONObject(props)).apply { description?.let { put("description", it) } }.toString().toRequestBody(JSON))
                .addPart(bytes.toRequestBody(mime.toMediaType())).build()
            call(Request.Builder().url("$UPLOAD/${existing.id}?uploadType=multipart").patch(body))
            return existing.id
        }
        val body = MultipartBody.Builder().setType("multipart/related".toMediaType())
            .addPart(meta(name, parent, props, description, null, null).toString().toRequestBody(JSON))
            .addPart(bytes.toRequestBody(mime.toMediaType())).build()
        return JSONObject(call(Request.Builder().url("$UPLOAD?uploadType=multipart&fields=id").post(body))).getString("id")
    }

    fun download(id: String): ByteArray =
        http.newCall(Request.Builder().url("$FILES/$id?alt=media").header("Authorization", "Bearer $token").build()).execute().use { r ->
            check(r.isSuccessful) { "Drive download ${r.code}" }
            r.body!!.bytes()
        }

    fun trash(id: String) { call(Request.Builder().url("$FILES/$id").patch(JSONObject().put("trashed", true).toString().toRequestBody(JSON))) }

    companion object {
        const val FOLDER = "application/vnd.google-apps.folder"
        private const val FILES = "https://www.googleapis.com/drive/v3/files"
        private const val UPLOAD = "https://www.googleapis.com/upload/drive/v3/files"
        private val JSON = "application/json; charset=UTF-8".toMediaType()
    }
}
