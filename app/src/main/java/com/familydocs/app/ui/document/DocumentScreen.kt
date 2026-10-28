package com.familydocs.app.ui.document

import androidx.compose.foundation.Image
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.familydocs.app.domain.maskValue
import com.familydocs.app.ocr.Images
import com.familydocs.app.ui.components.LocalContainer
import com.familydocs.app.ui.components.copySensitive
import com.familydocs.app.ui.ds.BtnKind
import com.familydocs.app.ui.ds.Fd
import com.familydocs.app.ui.ds.FdButton
import com.familydocs.app.ui.ds.FdConfirmSheet
import com.familydocs.app.ui.ds.FdDocIcon
import com.familydocs.app.ui.ds.FdEmpty
import com.familydocs.app.ui.ds.FdIcon
import com.familydocs.app.ui.ds.FdIconButton
import com.familydocs.app.ui.ds.FdSectionTitle
import com.familydocs.app.ui.ds.FdTopBar
import com.familydocs.app.ui.ds.LocalToast
import com.familydocs.app.ui.ds.fdText
import com.familydocs.app.ui.ds.fmtDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DocumentScreen(docId: String, onBack: () -> Unit, onEdit: (memberId: String) -> Unit) {
    val app = LocalContainer.current
    val c = Fd.c
    val context = LocalContext.current
    val toast = LocalToast.current
    val scope = rememberCoroutineScope()
    val doc by app.repo.document(docId).collectAsStateWithLifecycle(null)
    val d = doc ?: return
    val member by app.repo.member(d.memberId).collectAsStateWithLifecycle(null)
    var revealed by remember { mutableStateOf(setOf<Int>()) }
    var zoomed by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    // Every page of every file (front, back, PDF pages…), in order.
    val pages by produceState<List<ImageBitmap>>(emptyList(), d.fileId, d.moreFiles) {
        value = withContext(Dispatchers.IO) {
            d.attachments.flatMap { a ->
                app.files.get(a.id)?.let { bytes ->
                    if (a.mime == "application/pdf") Images.renderPdf(context, bytes, pages = 6).map { it.asImageBitmap() }
                    else listOfNotNull(Images.decode(bytes, 2000)?.asImageBitmap())
                }.orEmpty()
            }
        }
    }
    // Only the details that have a value.
    val filled = d.fields.filter { it.value.isNotBlank() }

    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding()) {
        FdTopBar("", onBack) { FdIconButton("edit", { onEdit(d.memberId) }, iconSize = 20.dp, description = "Edit document") }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            // `.dhead`
            Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                FdDocIcon(d)
                Column {
                    Text(d.name, style = fdText(20f, FontWeight.SemiBold))
                    Text(listOfNotNull(member?.name, member?.let { if (it.shared) "Shared" else it.relation }).joinToString(" · "), style = fdText(12f, color = c.muted))
                }
            }
            // `.docimg`
            if (pages.isNotEmpty()) {
                val pager = rememberPagerState { pages.size }
                Box(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).border(1.dp, c.line, RoundedCornerShape(18.dp))
                    .background(c.surface2)) {
                    HorizontalPager(pager, Modifier.fillMaxWidth()) { i ->
                        Image(pages[i], "${d.name}, page ${i + 1}", Modifier.fillMaxWidth().clickable { zoomed = true }, contentScale = ContentScale.FillWidth)
                    }
                    Box(Modifier.align(Alignment.BottomEnd).padding(10.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xB30A0E18)).padding(horizontal = 9.dp, vertical = 5.dp)) {
                        Text(if (pages.size > 1) "${pager.currentPage + 1} / ${pages.size} · swipe · tap to zoom" else "Tap to zoom", style = fdText(11f, color = Color.White))
                    }
                }
                if (pages.size > 1) PageDots(pages.size, pager.currentPage, Modifier.fillMaxWidth().padding(top = 8.dp))
            }
            FdSectionTitle("Details") { Text("${filled.size} field${if (filled.size == 1) "" else "s"}", style = fdText(11f, FontWeight.Medium, c.muted)) }
            if (filled.isEmpty()) FdEmpty("No details saved. Tap edit to add them.")
            else Column(Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.surface).border(1.dp, c.line, RoundedCornerShape(18.dp))) {
                filled.forEachIndexed { i, f ->
                    if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
                    val show = !f.sensitive || i in revealed
                    Row(Modifier.padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(f.label + if (f.verified == true) " ✓" else "", style = fdText(11f, color = c.muted))
                            Text(if (show) f.value.ifBlank { "empty" } else maskValue(f.value), style = fdText(14.5f, FontWeight.SemiBold, if (f.value.isBlank()) c.muted else c.fg))
                        }
                        if (f.sensitive) FdIconButton(if (show) "eyeoff" else "eye", { revealed = if (i in revealed) revealed - i else revealed + i },
                            size = 36.dp, iconSize = 19.dp, tint = c.muted, description = if (show) "Hide ${f.label}" else "Show ${f.label}")
                        FdIconButton("copy", { copySensitive(context, f.label, f.value); toast.show("Copied") }, size = 36.dp, iconSize = 18.dp, tint = c.muted, description = "Copy ${f.label}")
                    }
                }
            }
            // `.meta`
            Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FdIcon(if (d.readByOcr) "sparkle" else "edit", 14.dp, tint = c.muted)
                Text("Added ${fmtDate(d.createdAt)} · ${if (d.readByOcr) "details read from the file" else "entered by hand"}", style = fdText(11.5f, color = c.muted))
            }
            // `.actions`
            Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FdButton("Edit", { onEdit(d.memberId) }, Modifier.weight(1f), BtnKind.Ghost, icon = "edit")
                FdButton("Delete", { confirmDelete = true }, Modifier.weight(1f), BtnKind.Ghost, icon = "trash", contentColor = c.danger)
            }
        }
    }

    // `.lightbox`
    if (zoomed && pages.isNotEmpty()) Dialog({ zoomed = false }, DialogProperties(usePlatformDefaultWidth = false)) {
        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        Box(Modifier.fillMaxSize().background(Color(0xFF05070C)).pointerInput(Unit) {
            detectTransformGestures { _, pan, z, _ -> scale = (scale * z).coerceIn(1f, 6f); offset = if (scale == 1f) Offset.Zero else offset + pan }
        }) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(10.dp), verticalArrangement = Arrangement.Center) {
                pages.forEach { p ->
                    Image(p, null, Modifier.fillMaxWidth().padding(vertical = 4.dp).graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y },
                        contentScale = ContentScale.FillWidth)
                }
            }
            Row(Modifier.statusBarsPadding().padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                FdIconButton("close", { zoomed = false }, tint = Color.White, description = "Close")
                Text(d.name, style = fdText(17f, FontWeight.SemiBold, Color.White))
            }
        }
    }

    if (confirmDelete) FdConfirmSheet(
        "Delete this document?", "The file and its details are removed from this phone. Copies in older Drive backups are kept until they expire.", "Delete",
        onConfirm = { scope.launch { app.repo.deleteDocument(d.id); confirmDelete = false; toast.show("Deleted"); onBack() } },
        onDismiss = { confirmDelete = false },
    )
}
