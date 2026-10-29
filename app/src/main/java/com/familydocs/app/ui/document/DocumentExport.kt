package com.familydocs.app.ui.document

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.familydocs.app.data.DocumentEntity
import com.familydocs.app.data.FileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * A document's files (scans, photos, PDFs) out of the vault: saved to the phone's Downloads, or shared with another app.
 * The vault keeps them encrypted; these are plain copies, so they're only made when you ask.
 */
object DocumentExport {
    private fun ext(mime: String) = when (mime) { "application/pdf" -> "pdf"; "image/png" -> "png"; "image/webp" -> "webp"; else -> "jpg" }

    /** "Passport.pdf", or "Passport - 1.jpg", "Passport - 2.jpg"… for several files. */
    fun names(d: DocumentEntity): List<String> {
        val base = d.name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifEmpty { "Document" }
        val n = d.attachments.size
        return d.attachments.mapIndexed { i, a -> (if (n == 1) base else "$base - ${i + 1}") + "." + ext(a.mime) }
    }

    /** Android 10+: straight into Downloads › Lararium, no permission needed. Returns how many files were saved. */
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    suspend fun toDownloads(context: Context, files: FileStore, d: DocumentEntity): Int = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val names = names(d)
        var saved = 0
        d.attachments.forEachIndexed { i, a ->
            val bytes = files.get(a.id) ?: return@forEachIndexed
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, names[i]); put(MediaStore.MediaColumns.MIME_TYPE, a.mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Lararium"); put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return@forEachIndexed
            resolver.openOutputStream(uri)?.use { it.write(bytes) }
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            saved++
        }
        saved
    }

    /** Older Android: into a folder you picked ([tree] from the system folder picker). */
    suspend fun toFolder(context: Context, tree: Uri, files: FileStore, d: DocumentEntity): Int = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val dir = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val names = names(d)
        var saved = 0
        d.attachments.forEachIndexed { i, a ->
            val bytes = files.get(a.id) ?: return@forEachIndexed
            val uri = DocumentsContract.createDocument(resolver, dir, a.mime, names[i]) ?: return@forEachIndexed
            resolver.openOutputStream(uri)?.use { it.write(bytes) }
            saved++
        }
        saved
    }

    /** The share sheet with the document's files (temporary copies, cleared the next time something is shared). */
    suspend fun shareIntent(context: Context, files: FileStore, d: DocumentEntity): Intent? = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        clearShared(context)
        val names = names(d)
        val uris = d.attachments.mapIndexedNotNull { i, a ->
            val bytes = files.get(a.id) ?: return@mapIndexedNotNull null
            val f = File(dir, names[i]).apply { writeBytes(bytes) }
            FileProvider.getUriForFile(context, "${context.packageName}.files", f) to a.mime
        }
        if (uris.isEmpty()) return@withContext null
        val mime = uris.map { it.second }.distinct().singleOrNull() ?: if (uris.all { it.second.startsWith("image/") }) "image/*" else "*/*"
        val send = if (uris.size == 1) Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0].first)
            else Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris.map { it.first }))
        send.setType(mime).putExtra(Intent.EXTRA_SUBJECT, d.name).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        // the receiving app may only read the files it's given
        send.clipData = android.content.ClipData.newRawUri(d.name, uris[0].first).apply { uris.drop(1).forEach { addItem(android.content.ClipData.Item(it.first)) } }
        Intent.createChooser(send, "Share ${d.name}")
    }

    /** Removes the plain copies made for sharing. */
    fun clearShared(context: Context) { File(context.cacheDir, "share").listFiles()?.forEach { it.delete() } }
}
