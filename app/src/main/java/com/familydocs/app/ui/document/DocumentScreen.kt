package com.familydocs.app.ui.document

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.familydocs.app.domain.maskValue
import com.familydocs.app.ocr.Images
import com.familydocs.app.ui.components.BackBar
import com.familydocs.app.ui.components.DocumentIcon
import com.familydocs.app.ui.components.LocalContainer
import com.familydocs.app.ui.components.SectionTitle
import com.familydocs.app.ui.components.copySensitive
import com.familydocs.app.ui.components.outlinedCard
import com.familydocs.app.ui.theme.LocalExtra
import com.familydocs.app.ui.theme.MonoFont
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

@Composable
fun DocumentScreen(docId: String, onBack: () -> Unit, onEdit: (memberId: String) -> Unit) {
    val c = LocalContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val doc by c.repo.document(docId).collectAsStateWithLifecycle(null)
    val d = doc ?: return
    val member by c.repo.member(d.memberId).collectAsStateWithLifecycle(null)
    var revealed by remember { mutableStateOf(setOf<Int>()) }
    var zoomed by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val snack = remember { SnackbarHostState() }
    // Images decode directly; PDFs show page 1.
    val pages by produceState<List<ImageBitmap>>(emptyList(), d.fileId) {
        value = withContext(Dispatchers.IO) {
            d.fileId?.let { c.files.get(it) }?.let { bytes ->
                if (d.fileMime == "application/pdf") Images.renderPdf(context, bytes, pages = 6).map { it.asImageBitmap() }
                else listOfNotNull(Images.decode(bytes, 2000)?.asImageBitmap())
            } ?: emptyList()
        }
    }

    Scaffold(
        topBar = { BackBar("", onBack) { IconButton({ onEdit(d.memberId) }) { Icon(Icons.Outlined.Edit, "Edit document") } } },
        snackbarHost = { SnackbarHost(snack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState())) {
            Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                DocumentIcon(d)
                Column(Modifier.padding(start = 14.dp)) {
                    Text(d.name, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Text(listOfNotNull(member?.name, member?.relation).joinToString(" · "), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            pages.firstOrNull()?.let { first ->
                Image(first, "${d.name} scan", contentScale = ContentScale.FillWidth,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable { zoomed = true })
            }
            SectionTitle("Details") { Text("${d.fields.size} field${if (d.fields.size == 1) "" else "s"}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (d.fields.isEmpty()) Text("No details saved. Tap edit to add them.", Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            else Column(Modifier.padding(horizontal = 20.dp).outlinedCard(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.outlineVariant)) {
                d.fields.forEachIndexed { i, f ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    val show = !f.sensitive || i in revealed
                    Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(f.label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (f.verified == true) Icon(Icons.Outlined.Verified, "Checksum verified", Modifier.padding(start = 4.dp).size(13.dp), tint = LocalExtra.current.ok)
                            }
                            Text(if (show) f.value else maskValue(f.value), fontFamily = MonoFont, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        }
                        if (f.sensitive) IconButton({ revealed = if (i in revealed) revealed - i else revealed + i }) {
                            Icon(if (show) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, if (show) "Hide ${f.label}" else "Show ${f.label}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton({ copySensitive(context, f.label, f.value); scope.launch { snack.showSnackbar("${f.label} copied · clears in 45 s") } }) {
                            Icon(Icons.Outlined.ContentCopy, "Copy ${f.label}", Modifier.size(19.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (d.readByOcr) Icons.Outlined.AutoAwesome else Icons.Outlined.Edit, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(" Added ${DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(d.createdAt))} · ${if (d.readByOcr) "details read from the file" else "entered by hand"}",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton({ onEdit(d.memberId) }, Modifier.weight(1f)) { Icon(Icons.Outlined.Edit, null, Modifier.size(18.dp)); Text("  Edit") }
                OutlinedButton({ confirmDelete = true }, Modifier.weight(1f)) { Icon(Icons.Outlined.Delete, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error); Text("  Delete", color = MaterialTheme.colorScheme.error) }
            }
        }
    }

    if (zoomed && pages.isNotEmpty()) Dialog({ zoomed = false }, DialogProperties(usePlatformDefaultWidth = false)) {
        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        Box(Modifier.fillMaxSize().background(Color(0xFF05070C)).pointerInput(Unit) {
            detectTransformGestures { _, pan, z, _ -> scale = (scale * z).coerceIn(1f, 6f); offset = if (scale == 1f) Offset.Zero else offset + pan }
        }) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.Center) {
                pages.forEach { p ->
                    Image(p, null, Modifier.fillMaxWidth().padding(4.dp).graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y }, contentScale = ContentScale.FillWidth)
                }
            }
            IconButton({ zoomed = false }, Modifier.padding(16.dp)) { Icon(Icons.Outlined.Close, "Close", tint = Color.White) }
        }
    }

    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete this document?") },
        text = { Text("The file and its details are removed from this phone. Older Drive backups keep a copy until the next backup replaces them.") },
        confirmButton = { TextButton({ scope.launch { c.repo.deleteDocument(d.id); confirmDelete = false; onBack() } }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton({ confirmDelete = false }) { Text("Cancel") } },
    )
}
