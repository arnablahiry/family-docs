package com.familydocs.app.ui.document

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.familydocs.app.data.DisplayMode
import com.familydocs.app.domain.Countries
import com.familydocs.app.domain.DocTypes
import com.familydocs.app.ui.components.CountryPicker
import com.familydocs.app.ui.components.BackBar
import com.familydocs.app.ui.components.DocIconTile
import com.familydocs.app.ui.components.LocalContainer
import com.familydocs.app.ui.components.SectionTitle
import com.familydocs.app.ui.components.appViewModel
import com.familydocs.app.ui.components.outlinedCard
import com.familydocs.app.ui.theme.MonoFont
import java.io.File

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddDocumentScreen(memberId: String, docId: String?, onBack: () -> Unit, onSaved: () -> Unit) {
    val c = LocalContainer.current
    val context = LocalContext.current
    val vm = appViewModel(key = "doc-$memberId-$docId") { AddDocumentViewModel(it, context.applicationContext, memberId, docId) }
    var cameraUri by rememberSaveable { mutableStateOf<String?>(null) }
    val account by c.repo.account.collectAsStateWithLifecycle(null)
    val mine = Countries.decode(account?.countries)

    val openDoc = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { vm.onFile(context, it) } }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val u = cameraUri?.let(Uri::parse)
        if (ok && u != null) vm.onFile(context, u)
    }
    val pickPicture = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { vm.onCustomPicture(context, it) } }

    fun launchCamera() {
        val f = File(context.cacheDir, "camera").apply { mkdirs() }.let { File(it, "scan.jpg") }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", f)
        cameraUri = uri.toString()
        c.appLock.expectExternalActivity()
        takePicture.launch(uri)
    }

    Scaffold(
        topBar = { BackBar(if (docId == null) "Add document" else "Edit document", onBack) { TextButton({ vm.save(onSaved) }, enabled = vm.name.isNotBlank() && !vm.saving) { Text("Save") } } },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).imePadding()) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                // ---------- File ----------
                SectionTitle("File")
                val preview = vm.preview
                if (preview != null) {
                    Box(Modifier.padding(horizontal = 20.dp).clip(RoundedCornerShape(18.dp))) {
                        Image(preview.asImageBitmap(), "Selected document", Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
                        Row(Modifier.align(Alignment.BottomEnd).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            SmallOverlayButton("Replace", Icons.Outlined.Refresh) { c.appLock.expectExternalActivity(); openDoc.launch(arrayOf("image/*", "application/pdf")) }
                            SmallOverlayButton("Remove", Icons.Outlined.Delete) { vm.removeFile() }
                        }
                    }
                } else {
                    Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PickOption("Scan", "Use camera", Icons.Outlined.PhotoCamera, Modifier.weight(1f)) { launchCamera() }
                        PickOption("Upload", "Image or PDF", Icons.Outlined.UploadFile, Modifier.weight(1f)) { c.appLock.expectExternalActivity(); openDoc.launch(arrayOf("image/*", "application/pdf")) }
                    }
                }
                if (vm.ocr is OcrState.Reading) {
                    LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp))
                }

                // ---------- Name ----------
                SectionTitle("Document name")
                Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    DocIconTile(vm.name)
                    OutlinedTextField(vm.name, vm::onNameChange, Modifier.weight(1f).padding(start = 12.dp), placeholder = { Text("e.g. Passport, OCI card") }, singleLine = true, shape = RoundedCornerShape(14.dp))
                }
                val type = DocTypes.forName(vm.name)
                Text(
                    when { vm.name.isBlank() -> "The tile icon is picked from the name you type."; type.matched -> "Icon matched to ${type.label}"; else -> "No match. Using a file icon with initials." },
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 20.dp, top = 6.dp),
                )
                FlowRow(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DocTypes.SUGGESTIONS.forEach { s -> FilterChip(vm.name.equals(s, true), { vm.onNameChange(s) }, label = { Text(s) }) }
                }

                // ---------- Issued by ----------
                SectionTitle("Issued by")
                CountryPicker(
                    selected = listOf(vm.country), onToggle = { vm.countryChoice = it }, multi = false,
                    pinned = mine, allowNone = true, modifier = Modifier.padding(horizontal = 20.dp),
                )

                // ---------- Display picture ----------
                SectionTitle("Display picture")
                Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DisplayOption("Auto icon", vm.display == DisplayMode.ICON, true, Modifier.weight(1f), { vm.display = DisplayMode.ICON }) { DocIconTile(vm.name) }
                    DisplayOption("From file", vm.display == DisplayMode.FROM_FILE, preview != null, Modifier.weight(1f), { vm.display = DisplayMode.FROM_FILE }) {
                        DocIconTile(vm.name, picture = preview?.asImageBitmap(), iconOverride = if (preview == null) com.familydocs.app.domain.DocIcon.FILE else null)
                    }
                    DisplayOption("Own picture", vm.display == DisplayMode.CUSTOM, true, Modifier.weight(1f), {
                        if (vm.customPicture == null) { c.appLock.expectExternalActivity(); pickPicture.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                        else vm.display = DisplayMode.CUSTOM
                    }) { DocIconTile(vm.name, picture = vm.customPicture?.asImageBitmap()) }
                }

                // ---------- Digitised details ----------
                SectionTitle("Digitised details")
                when (val o = vm.ocr) {
                    is OcrState.Done -> Banner(Icons.Outlined.AutoAwesome, (o.label?.let { "Looks like a $it. " } ?: "") + "Filled ${o.count} field${if (o.count == 1) "" else "s"}. Check and edit below.", true)
                    is OcrState.Failed -> Banner(Icons.Outlined.Info, "${o.message}. Type the details below.", false)
                    OcrState.Reading -> Banner(Icons.Outlined.DocumentScanner, "Reading the document…", true)
                    OcrState.Idle -> if (vm.fields.isEmpty()) Banner(Icons.Outlined.DocumentScanner, "Add a file and the card number, names and dates are read from it automatically.", false)
                }
                if (vm.fields.isNotEmpty()) Column(Modifier.padding(horizontal = 20.dp).outlinedCard(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.outlineVariant, 16.dp)) {
                    vm.fields.forEachIndexed { i, f ->
                        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Row(Modifier.padding(start = 14.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    BasicTextField(f.label, { vm.updateField(i, f.copy(label = it)) }, singleLine = true,
                                        textStyle = TextStyle(fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant), cursorBrush = SolidColor(MaterialTheme.colorScheme.primary))
                                    if (f.verified == true) Icon(Icons.Outlined.Verified, "Checksum verified", Modifier.padding(start = 4.dp).size(13.dp), tint = com.familydocs.app.ui.theme.LocalExtra.current.ok)
                                    if (f.auto) Text(" · auto", fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
                                }
                                BasicTextField(f.value, { vm.updateField(i, f.copy(value = it)) }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                                    textStyle = TextStyle(fontFamily = MonoFont, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface), cursorBrush = SolidColor(MaterialTheme.colorScheme.primary))
                            }
                            IconButton({ vm.removeField(i) }) { Icon(Icons.Outlined.Close, "Remove ${f.label}", Modifier.size(18.dp)) }
                        }
                    }
                }
                TextButton(vm::addField, Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) { Icon(Icons.Outlined.Add, null); Text(" Add field") }
                if (vm.rawText.isNotBlank()) {
                    var showRaw by remember { mutableStateOf(false) }
                    TextButton({ showRaw = !showRaw }, Modifier.padding(horizontal = 12.dp)) { Text(if (showRaw) "Hide text read from the file" else "Show text read from the file") }
                    if (showRaw) Text(vm.rawText, fontFamily = MonoFont, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 20.dp).outlinedCard(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.outlineVariant, 12.dp).padding(10.dp).heightIn(max = 200.dp))
                }
            }
            Button({ vm.save(onSaved) }, Modifier.fillMaxWidth().padding(20.dp), enabled = vm.name.isNotBlank() && !vm.saving, shape = RoundedCornerShape(16.dp)) {
                Text(if (vm.saving) "Saving…" else "Save document", modifier = Modifier.padding(vertical = 8.dp), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun PickOption(title: String, sub: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.outlinedCard(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.outline, 16.dp).clickable(onClick = onClick).padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, Modifier.size(26.dp))
        Text(title, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
        Text(sub, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DisplayOption(label: String, selected: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit, preview: @Composable () -> Unit) {
    Surface(
        modifier = modifier, shape = RoundedCornerShape(16.dp), enabled = enabled, onClick = onClick,
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = .07f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(vertical = 12.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(54.dp)) { preview() }
            Text(label, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun Banner(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, accent: Boolean) {
    Row(
        Modifier.padding(horizontal = 20.dp, vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .then(Modifier.outlinedCard(if (accent) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant, androidx.compose.ui.graphics.Color.Transparent, 14.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(18.dp))
        Text(text, fontSize = 13.sp, modifier = Modifier.padding(start = 10.dp))
    }
}

@Composable
private fun SmallOverlayButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(10.dp), color = androidx.compose.ui.graphics.Color(0xB80A0E18), contentColor = androidx.compose.ui.graphics.Color.White) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(14.dp)); Text(" $label", fontSize = 12.sp)
        }
    }
}

