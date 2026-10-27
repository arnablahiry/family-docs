package com.familydocs.app.ui.document

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familydocs.app.AppContainer
import com.familydocs.app.data.DisplayMode
import com.familydocs.app.data.DocumentEntity
import com.familydocs.app.domain.Countries
import com.familydocs.app.domain.DocField
import com.familydocs.app.domain.DocTypes
import com.familydocs.app.ocr.Images
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/** Editable copy of a field; [id] keeps Compose keys stable while labels change. */
data class EditField(val id: String = UUID.randomUUID().toString(), val label: String, val value: String, val auto: Boolean = false, val verified: Boolean? = null)

sealed interface OcrState {
    data object Idle : OcrState
    data object Reading : OcrState
    data class Done(val label: String?, val count: Int) : OcrState
    data class Failed(val message: String) : OcrState
}

class AddDocumentViewModel(private val c: AppContainer, private val appContext: Context, private val memberId: String, private val docId: String?) : ViewModel() {
    var name by mutableStateOf("")
    var display by mutableStateOf(DisplayMode.ICON)
    var preview by mutableStateOf<Bitmap?>(null); private set
    var customPicture by mutableStateOf<Bitmap?>(null); private set
    var fileName by mutableStateOf<String?>(null); private set
    var ocr by mutableStateOf<OcrState>(OcrState.Idle); private set
    var rawText by mutableStateOf(""); private set
    val fields = mutableStateListOf<EditField>()
    var saving by mutableStateOf(false); private set
    /** Issuing country picked by the user; null = follow what the scan / name suggests. */
    var countryChoice by mutableStateOf<String?>(null)

    val country: String
        get() = countryChoice ?: Countries.infer(name, fields.map { DocField(it.label, it.value) }, rawText)

    private var fileBytes: ByteArray? = null
    private var fileMime: String? = null
    private var existing: DocumentEntity? = null
    private var fileChanged = false
    private var pictureChanged = false
    private var fieldsTouched = false
    private var ocrJob: Job? = null

    init {
        if (docId != null) viewModelScope.launch {
            val d = c.repo.documentNow(docId) ?: return@launch
            existing = d
            name = d.name; display = d.display; rawText = d.rawText; fileMime = d.fileMime; countryChoice = d.country
            fields.addAll(d.fields.map { EditField(label = it.label, value = it.value, auto = it.auto, verified = it.verified) })
            fieldsTouched = true
            if (d.readByOcr) ocr = OcrState.Done(null, d.fields.count { it.auto })
            withContext(Dispatchers.IO) {
                d.fileId?.let { c.files.get(it) }?.let { bytes -> preview = Images.pages(appContext, bytes, d.fileMime).firstOrNull() }
                if (d.display == DisplayMode.CUSTOM) d.pictureFile?.let { c.files.get(it) }?.let { customPicture = Images.decode(it, 600) }
            }
        }
    }

    /** Image or PDF from the picker / camera → preview → on-device OCR → fields. */
    fun onFile(context: Context, uri: Uri) {
        ocrJob?.cancel()
        ocrJob = viewModelScope.launch {
            ocr = OcrState.Reading
            runCatching {
                val mime = context.contentResolver.getType(uri)
                val bytes = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }.also {
                        // Camera shots land in a temp file first; remove the unencrypted copy straight away.
                        if (uri.authority == context.packageName + ".files") runCatching { context.contentResolver.delete(uri, null, null) }
                    }
                }
                val pages = withContext(Dispatchers.Default) { Images.pages(context, bytes, mime) }
                require(pages.isNotEmpty()) { "Couldn't open that file" }
                fileBytes = bytes; fileMime = mime ?: "image/jpeg"; fileChanged = true
                fileName = uri.lastPathSegment?.substringAfterLast('/')
                preview = pages.first()
                val (text, result) = c.textReader.readAndParse(pages)
                rawText = text
                if (name.isBlank() && result.suggestedName != null) name = result.suggestedName
                // Replace previous auto-filled values, keep anything typed by hand.
                fields.removeAll { it.auto || (!fieldsTouched && it.value.isBlank()) }
                fields.addAll(0, result.fields.map { EditField(label = it.label, value = it.value, auto = true, verified = it.verified) })
                if (result.fields.isEmpty() && fields.isEmpty()) applyTemplate()
                ocr = OcrState.Done(result.suggestedName, result.fields.size)
            }.onFailure { ocr = OcrState.Failed(it.message ?: "Couldn't read this file"); if (fields.isEmpty()) applyTemplate() }
        }
    }

    fun removeFile() {
        ocrJob?.cancel()
        fileBytes = null; fileMime = null; preview = null; fileName = null; fileChanged = true; rawText = ""
        fields.removeAll { it.auto }
        if (display == DisplayMode.FROM_FILE) display = DisplayMode.ICON
        ocr = OcrState.Idle
    }

    fun onCustomPicture(context: Context, uri: Uri) = viewModelScope.launch {
        val bmp = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { it.readBytes() }?.let { Images.decode(it, 800) } }
        if (bmp != null) { customPicture = bmp; pictureChanged = true; display = DisplayMode.CUSTOM }
    }

    fun onNameChange(v: String) {
        name = v
        // Untouched blank template follows the document type as the user types.
        if (!fieldsTouched && fields.none { it.value.isNotBlank() }) { fields.clear(); applyTemplate() }
    }

    private fun applyTemplate() { fields.addAll(DocTypes.template(name).map { EditField(label = it, value = "") }) }

    fun updateField(i: Int, f: EditField) { fields[i] = f; fieldsTouched = true }
    fun removeField(i: Int) { fields.removeAt(i); fieldsTouched = true }
    fun addField() { fields.add(EditField(label = "New field", value = "")); fieldsTouched = true }

    fun save(onSaved: () -> Unit) {
        if (name.isBlank() || saving) return
        saving = true
        viewModelScope.launch {
            val old = existing
            val fileId = withContext(Dispatchers.IO) {
                if (fileChanged) { c.files.delete(old?.fileId); fileBytes?.let { c.files.put(it) } } else old?.fileId
            }
            val picture = withContext(Dispatchers.Default) {
                when (display) {
                    DisplayMode.ICON -> null
                    DisplayMode.FROM_FILE -> preview?.let { Images.centerSquareJpeg(it, 480) }
                    DisplayMode.CUSTOM -> customPicture?.let { Images.centerSquareJpeg(it, 480) }
                }
            }
            val pictureId = withContext(Dispatchers.IO) {
                when {
                    display == DisplayMode.ICON -> { c.files.delete(old?.pictureFile); null }
                    picture != null && (fileChanged || pictureChanged || old?.pictureFile == null || old?.display != display) -> { c.files.delete(old?.pictureFile); c.files.put(picture) }
                    else -> old?.pictureFile
                }
            }
            val doc = (old ?: DocumentEntity(id = UUID.randomUUID().toString(), memberId = memberId, name = name, fileId = null, fileMime = null)).copy(
                name = name.trim(), fileId = fileId, fileMime = if (fileId == null) null else fileMime, display = display, pictureFile = pictureId,
                rawText = rawText, readByOcr = ocr is OcrState.Done, country = country,
            )
            c.repo.saveDocument(doc, fields.filter { it.label.isNotBlank() || it.value.isNotBlank() }.map {
                DocField(it.label.trim(), it.value.trim(), DocTypes.isSensitive(it.label), it.auto, it.verified)
            })
            saving = false
            onSaved()
        }
    }
}
