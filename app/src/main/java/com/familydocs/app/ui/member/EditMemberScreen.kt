package com.familydocs.app.ui.member

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.familydocs.app.ocr.Images
import com.familydocs.app.ui.components.LocalContainer
import com.familydocs.app.ui.ds.BtnKind
import com.familydocs.app.ui.ds.CropState
import com.familydocs.app.ui.ds.Fd
import com.familydocs.app.ui.ds.FdAvatar
import com.familydocs.app.ui.ds.FdButton
import com.familydocs.app.ui.ds.FdCharacterPicker
import com.familydocs.app.ui.ds.FdChip
import com.familydocs.app.ui.ds.FdChips
import com.familydocs.app.ui.ds.FdConfirmSheet
import com.familydocs.app.ui.ds.FdField
import com.familydocs.app.ui.ds.FdIcon
import com.familydocs.app.ui.ds.FdInput
import com.familydocs.app.ui.ds.FdPhotoCropper
import com.familydocs.app.ui.ds.FdSavebar
import com.familydocs.app.ui.ds.FdTextButton
import com.familydocs.app.ui.ds.FdTopBar
import com.familydocs.app.ui.ds.LocalToast
import com.familydocs.app.ui.ds.cropBoxPx
import com.familydocs.app.ui.ds.cropToSquare
import com.familydocs.app.ui.ds.fdText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val RELATIONS = listOf("Spouse", "Mother", "Father", "Son", "Daughter", "Sister", "Brother", "Grandmother", "Grandfather", "Family", "Other")

/** `addmember`: photo in a drag/zoom circle or an illustrated character, name, relation. Works for you, family and the Common vault. */
@Composable
fun EditMemberScreen(memberId: String?, onBack: () -> Unit, onDeleted: () -> Unit) {
    val app = LocalContainer.current
    val c = Fd.c
    val context = LocalContext.current
    val toast = LocalToast.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var relation by remember { mutableStateOf("Family") }
    var existingPhoto by remember { mutableStateOf<String?>(null) }
    var avatar by remember { mutableStateOf<String?>(null) }
    var isSelf by remember { mutableStateOf(false) }
    var isShared by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf<Bitmap?>(null) }
    var removePhoto by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var docCount by remember { mutableStateOf(0) }
    val crop = remember { CropState() }
    val box = cropBoxPx()

    LaunchedEffect(memberId) {
        val m = memberId?.let { app.repo.memberNow(it) }
        if (m != null) {
            name = m.name; relation = m.relation; existingPhoto = m.photoFile; isSelf = m.isSelf; isShared = m.shared; avatar = m.avatar
            docCount = app.repo.documentsCount(m.id)
        } else avatar = app.repo.nextCharacter()
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            picked = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { it.readBytes() }?.let { Images.decode(it, 1600) } }
            crop.zoom = 1f; crop.pan = Offset.Zero; removePhoto = false
        }
    }
    fun choosePhoto() { app.appLock.expectExternalActivity(); picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    val hasPhoto = picked != null || (existingPhoto != null && !removePhoto)

    fun save() {
        if (name.isBlank()) { toast.show("Add a name first"); return }
        scope.launch {
            val bytes = picked?.let { bmp -> withContext(Dispatchers.Default) { Images.jpeg(cropToSquare(bmp, crop, box)) } }
            app.repo.saveMember(memberId, name.trim(), when { isSelf -> "You"; isShared -> "Shared"; else -> relation }, bytes, keepPhoto = !removePhoto, avatar = avatar)
            toast.show(if (memberId == null) "Added ${name.trim()}" else "Saved")
            onBack()
        }
    }

    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding().imePadding()) {
        FdTopBar(when { memberId == null -> "Add family member"; isShared -> "Edit shared vault"; isSelf -> "Edit your profile"; else -> "Edit member" }, onBack) {
            FdTextButton("Save", ::save)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            FdPhotoCropper(picked, crop) {
                when {
                    existingPhoto != null && !removePhoto -> FdAvatar(name, 174.dp, photoFile = existingPhoto)
                    isShared -> FdAvatar(name, 174.dp, shared = true)
                    avatar != null -> FdAvatar(name, 174.dp, character = avatar)
                    else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        FdIcon("camera", 30.dp, tint = c.muted); Text("Add a photo", style = fdText(12f, color = c.muted))
                    }
                }
            }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                FdChips {
                    FdChip(if (hasPhoto) "Change photo" else "Choose photo", false, ::choosePhoto, leading = { FdIcon("image", 16.dp, tint = c.fg) })
                    if (hasPhoto) FdChip("Remove photo", false, { picked = null; removePhoto = true }, leading = { FdIcon("trash", 16.dp, tint = c.fg) })
                }
            }
            if (!isShared && !hasPhoto) FdField("Or pick a character") { FdCharacterPicker(avatar) { avatar = it } }
            if (isShared && !hasPhoto) Text("The shared vault uses the house icon until you choose a picture.", style = fdText(11.5f, color = c.muted))

            FdField("Name") { FdInput(name, { name = it }, placeholder = if (isShared) "Common" else "e.g. Mohua, Teesta", icon = "user") }
            if (!isSelf && !isShared) FdField("Relation") {
                FdChips { RELATIONS.forEach { r -> FdChip(r, relation == r, { relation = r }) } }
            }
            if (memberId != null && !isSelf && !isShared) {
                Spacer(Modifier.height(8.dp))
                FdButton("Remove ${name.ifBlank { "member" }}", { confirmDelete = true }, kind = BtnKind.Ghost, icon = "trash", contentColor = c.danger)
            }
            Spacer(Modifier.height(8.dp))
        }
        FdSavebar { FdButton("Save", ::save, icon = "check") }
    }

    if (confirmDelete) FdConfirmSheet(
        "Remove $name?", "Their $docCount documents and passwords will be deleted from this phone.", "Remove",
        onConfirm = { scope.launch { memberId?.let { app.repo.deleteMember(it) }; confirmDelete = false; toast.show("Removed $name"); onDeleted() } },
        onDismiss = { confirmDelete = false },
    )
}
