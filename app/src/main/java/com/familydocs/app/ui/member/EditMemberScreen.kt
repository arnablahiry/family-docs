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

private val RELATIONS = listOf("Mom", "Dad", "Sister", "Brother", "Son", "Daughter", "Spouse", "Grandmother", "Grandfather", "Family", "Other")

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
    /** Another member of the group (you're an admin): their phone takes over what you change here. */
    var peer by remember { mutableStateOf(false) }
    var groupName by remember { mutableStateOf("the group") }
    var canEdit by remember { mutableStateOf(true) }
    val crop = remember { CropState() }
    val box = cropBoxPx()
    /** Editing the saved photo: its original (when this phone has it), so a re-save reuses it. */
    var editingFrom by remember { mutableStateOf<com.familydocs.app.data.PhotoSources.Source?>(null) }

    LaunchedEffect(memberId) {
        val m = memberId?.let { app.repo.memberNow(it) }
        if (m != null) {
            name = m.name; relation = m.relation; existingPhoto = m.photoFile; isSelf = m.isSelf; isShared = m.shared; avatar = m.avatar
            docCount = app.repo.documentsCount(m.id)
            peer = m.origin != null
            m.groupId?.let { gid -> app.repo.groupsNow().firstOrNull { it.id == gid }?.let { g -> groupName = g.name; if (peer) canEdit = g.isAdmin(app.repo.selfNow()?.id) } }
        } else avatar = app.repo.nextCharacter()
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            picked = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { it.readBytes() }?.let { Images.decode(it, 1600) } }
            crop.zoom = 1f; crop.pan = Offset.Zero; removePhoto = false; editingFrom = null
        }
    }
    /** Reopens the current photo in the cropper (the whole original, framed as it was, when this phone has it). */
    fun editPhoto() = scope.launch {
        val s = com.familydocs.app.ui.home.loadPhotoSource(app, existingPhoto) ?: run { toast.show("Couldn't open the photo"); return@launch }
        picked = s.bitmap; crop.zoom = s.zoom; crop.pan = Offset(s.panX * box, s.panY * box); editingFrom = s
    }
    fun choosePhoto() { app.appLock.expectExternalActivity(); picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    val hasPhoto = picked != null || (existingPhoto != null && !removePhoto)

    fun save() {
        if (name.isBlank()) { toast.show("Add a name first"); return }
        scope.launch {
            val bytes = picked?.let { bmp -> withContext(Dispatchers.Default) { Images.jpeg(cropToSquare(bmp, crop, box)) } }
            val original = picked; val z = crop.zoom; val px = crop.pan.x / box; val py = crop.pan.y / box; val from = editingFrom?.originalId
            runCatching { app.repo.saveMember(memberId, name.trim(), when { isSelf -> "You"; isShared -> "Shared"; else -> relation }, bytes, keepPhoto = !removePhoto, avatar = avatar) }
                .onSuccess {
                    // keep the whole photo too, for the next "Edit photo"
                    if (bytes != null && original != null) app.appScope.launch(Dispatchers.IO) { runCatching { app.photoSources.keep(bytes, original, z, px, py, from) } }
                    toast.show(when { memberId == null -> "Added ${name.trim()}"; peer -> "Saved. ${name.trim()}'s phone takes it over when it next syncs"; else -> "Saved" })
                    onBack()
                }.onFailure { toast.show(it.message ?: "Couldn't save") }
        }
    }

    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding().imePadding()) {
        FdTopBar(when { memberId == null -> "Add family member"; isShared -> "Edit shared vault"; isSelf -> "Edit your profile"; peer -> "Edit profile (admin)"; else -> "Edit member" }, onBack) {
            if (canEdit) FdTextButton("Save", ::save)
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
                    if (existingPhoto != null && picked == null && !removePhoto && canEdit) FdChip("Edit photo", false, { editPhoto() }, leading = { FdIcon("edit", 16.dp, tint = c.fg) })
                    FdChip(if (hasPhoto) "Change photo" else "Choose photo", false, ::choosePhoto, leading = { FdIcon("image", 16.dp, tint = c.fg) })
                    if (hasPhoto) FdChip("Remove photo", false, { picked = null; removePhoto = true }, leading = { FdIcon("trash", 16.dp, tint = c.fg) })
                }
            }
            if (!isShared && !hasPhoto) FdField("Or pick a character") { FdCharacterPicker(avatar, onPick = { avatar = it },
                keys = if (isSelf || com.familydocs.app.data.presetFor(name) != null) com.familydocs.app.data.FAMILY_PRESET.map { it.avatar } else null) }
            if (isShared && !hasPhoto) Text("The shared vault uses the house icon until you choose a picture.", style = fdText(11.5f, color = c.muted))

            FdField("Name") { FdInput(name, { name = it }, placeholder = if (isShared) "Common" else "e.g. Mohua, Teesta", icon = "user") }
            if (peer && !canEdit) Text("${name.ifBlank { "They" }} keeps their own profile up to date. Only an admin of $groupName can change it or remove them.",
                style = fdText(12f, color = c.muted))
            if (peer && canEdit) Text("You're an admin of $groupName, so you can change ${name.ifBlank { "their" }}'s profile. Their phone takes it over and shows them a note that you did.",
                style = fdText(12f, color = c.muted))
            if (!isSelf && !isShared && !peer) FdField("Relation") {
                FdChips { RELATIONS.forEach { r -> FdChip(r, relation == r, { relation = r }) } }
            }
            if (memberId != null && !isSelf && !isShared && canEdit) {
                Spacer(Modifier.height(8.dp))
                FdButton(if (peer) "Remove ${name.ifBlank { "them" }} from the group" else "Remove ${name.ifBlank { "member" }}", { confirmDelete = true }, kind = BtnKind.Ghost, icon = "trash", contentColor = c.danger)
            }
            Spacer(Modifier.height(8.dp))
        }
        if (canEdit) FdSavebar { FdButton("Save", ::save, icon = "check") }
    }

    if (confirmDelete) FdConfirmSheet(
        if (peer) "Remove $name from $groupName?" else "Remove $name?",
        if (peer) "$name leaves $groupName: their documents disappear from everyone's phone and they no longer see the group's things. " +
            "Nothing is deleted from their own phone or Drive, and they can ask to join again with the code."
        else "Their $docCount documents and passwords will be deleted from this phone.", "Remove",
        onConfirm = { scope.launch {
            confirmDelete = false
            runCatching { memberId?.let { app.repo.deleteMember(it) } }.onSuccess { toast.show(if (peer) "$name was removed from $groupName" else "Removed $name"); onDeleted() }
                .onFailure { toast.show(it.message ?: "Couldn't remove") }
        } },
        onDismiss = { confirmDelete = false },
    )
}
