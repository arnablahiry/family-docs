package com.familydocs.app.ui.member

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.familydocs.app.ocr.Images
import com.familydocs.app.ui.components.Avatar
import com.familydocs.app.ui.components.BackBar
import com.familydocs.app.ui.components.LocalContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val RELATIONS = listOf("Spouse", "Mother", "Father", "Son", "Daughter", "Sister", "Brother", "Grandmother", "Grandfather", "Family", "Other")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditMemberScreen(memberId: String?, onBack: () -> Unit, onDeleted: () -> Unit) {
    val c = LocalContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var relation by remember { mutableStateOf("Family") }
    var existingPhoto by remember { mutableStateOf<String?>(null) }
    var isSelf by remember { mutableStateOf(false) }
    var isShared by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf<Bitmap?>(null) }
    var removePhoto by remember { mutableStateOf(false) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var confirmDelete by remember { mutableStateOf(false) }
    val boxDp = 220.dp
    val boxPx = with(LocalDensity.current) { boxDp.toPx() }

    LaunchedEffect(memberId) {
        memberId?.let { c.repo.memberNow(it) }?.let { m -> name = m.name; relation = m.relation; existingPhoto = m.photoFile; isSelf = m.isSelf; isShared = m.shared }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            picked = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { it.readBytes() }?.let { Images.decode(it, 1600) } }
            zoom = 1f; pan = Offset.Zero; removePhoto = false
        }
    }

    Scaffold(topBar = { BackBar(if (memberId == null) "Add family member" else if (isShared) "Edit shared vault" else "Edit member", onBack) }, containerColor = MaterialTheme.colorScheme.background) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).imePadding()) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val bmp = picked
                    if (bmp != null) {
                        // Drag + pinch inside the circle; what you see is what gets saved.
                        Box(Modifier.size(boxDp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)
                            .pointerInput(Unit) { detectTransformGestures { _, p, z, _ -> zoom = (zoom * z).coerceIn(1f, 4f); pan += p } }) {
                            Image(bmp.asImageBitmap(), "Photo preview", contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = zoom; scaleY = zoom; translationX = pan.x; translationY = pan.y })
                        }
                    } else if (existingPhoto != null && !removePhoto) {
                        Avatar(name, existingPhoto, boxDp)
                    } else if (isShared) {
                        Avatar(name, null, boxDp, shared = true) // house icon until a picture is chosen
                    } else {
                        Box(Modifier.size(boxDp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.PhotoCamera, null, Modifier.size(34.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)) {
                    AssistChip({
                        c.appLock.expectExternalActivity()
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }, label = { Text(if (picked != null || existingPhoto != null) "Change photo" else "Choose photo") }, leadingIcon = { Icon(Icons.Outlined.Image, null) })
                    if (picked != null || (existingPhoto != null && !removePhoto)) {
                        AssistChip({ picked = null; removePhoto = true }, label = { Text("Remove") }, leadingIcon = { Icon(Icons.Outlined.Delete, null) })
                    }
                }
                if (picked != null) Text("Pinch to zoom, drag to position.", Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Name") }, singleLine = true, shape = RoundedCornerShape(14.dp))
                if (!isSelf && !isShared) {
                    Text("Relation", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RELATIONS.forEach { r -> FilterChip(relation == r, { relation = r }, label = { Text(r) }) }
                    }
                }
                if (memberId != null && !isSelf && !isShared) {
                    OutlinedButton({ confirmDelete = true }, Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Delete, null); Text("  Remove ${name.ifBlank { "member" }}", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            Button(
                onClick = {
                    if (name.isBlank()) return@Button
                    scope.launch {
                        val bytes = picked?.let { bmp -> withContext(Dispatchers.Default) { Images.jpeg(cropCircle(bmp, boxPx, zoom, pan)) } }
                        c.repo.saveMember(memberId, name.trim(), when { isSelf -> "You"; isShared -> "Shared"; else -> relation }, bytes, keepPhoto = !removePhoto)
                        onBack()
                    }
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth().padding(20.dp).height(54.dp), shape = RoundedCornerShape(16.dp),
            ) { Text("Save") }
        }
    }

    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Remove $name?") },
        text = { Text("Their documents and passwords will be deleted from this phone.") },
        confirmButton = { TextButton({ scope.launch { memberId?.let { c.repo.deleteMember(it) }; confirmDelete = false; onDeleted() } }) { Text("Remove", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton({ confirmDelete = false }) { Text("Cancel") } },
    )
}

/** Reproduces the on-screen ContentScale.Crop + zoom + pan into a 512 px square (shown as a circle everywhere). */
private fun cropCircle(src: Bitmap, boxPx: Float, zoom: Float, pan: Offset, out: Int = 512): Bitmap {
    val base = maxOf(boxPx / src.width, boxPx / src.height)
    val w = src.width * base * zoom
    val h = src.height * base * zoom
    val k = out / boxPx
    val left = (boxPx / 2 + pan.x - w / 2) * k
    val top = (boxPx / 2 + pan.y - h / 2) * k
    return Bitmap.createBitmap(out, out, Bitmap.Config.ARGB_8888).also {
        Canvas(it).drawBitmap(src, null, RectF(left, top, left + w * k, top + h * k), android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
    }
}
