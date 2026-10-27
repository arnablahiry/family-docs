package com.familydocs.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.familydocs.app.backup.BackupWorker
import com.familydocs.app.data.VaultRepository
import com.familydocs.app.domain.Countries
import com.familydocs.app.domain.DocTypes
import com.familydocs.app.security.BiometricHelper
import com.familydocs.app.security.SettingsStore
import com.familydocs.app.ui.components.LocalContainer
import com.familydocs.app.ui.ds.BtnKind
import com.familydocs.app.ui.ds.Fd
import com.familydocs.app.ui.ds.FdAvatar
import com.familydocs.app.ui.ds.FdButton
import com.familydocs.app.ui.ds.FdField
import com.familydocs.app.ui.ds.FdFlag
import com.familydocs.app.ui.ds.FdIcon
import com.familydocs.app.ui.ds.FdIconButton
import com.familydocs.app.ui.ds.FdInput
import com.familydocs.app.ui.ds.FdList
import com.familydocs.app.ui.ds.FdListItem
import com.familydocs.app.ui.ds.FdLic
import com.familydocs.app.ui.ds.FdSectionTitle
import com.familydocs.app.ui.ds.FdSheet
import com.familydocs.app.ui.ds.FdSwitch
import com.familydocs.app.ui.ds.FdTopBar
import com.familydocs.app.ui.ds.LocalToast
import com.familydocs.app.ui.ds.SheetTitle
import com.familydocs.app.ui.ds.fdText
import com.familydocs.app.ui.ds.fmtDate
import com.familydocs.app.ui.ds.tap
import com.google.android.gms.auth.api.identity.Identity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private sealed interface DriveAction { data object Backup : DriveAction; class Restore(val password: CharArray) : DriveAction }

/** Demo `settings`: profile card, Security, Countries, Backup, About, all as `.list` groups with `.lic` tiles and `.sw` switches. */
@Composable
fun SettingsScreen(onBack: () -> Unit, onEditAccount: () -> Unit, onChangePasscode: () -> Unit) {
    val app = LocalContainer.current
    val c = Fd.c
    val context = LocalContext.current
    val toast = LocalToast.current
    val scope = rememberCoroutineScope()
    val account by app.repo.account.collectAsStateWithLifecycle(null)
    val self by app.repo.self().collectAsStateWithLifecycle(null)
    val members by app.repo.members.collectAsStateWithLifecycle(emptyList())
    val docs by app.repo.allDocuments.collectAsStateWithLifecycle(emptyList())
    val s by app.settings.state.collectAsStateWithLifecycle()
    var busy by remember { mutableStateOf<String?>(null) }
    var askPassword by remember { mutableStateOf<String?>(null) } // "set" | "restore"
    var pending by remember { mutableStateOf<DriveAction?>(null) }
    var showTree by remember { mutableStateOf(false) }
    val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "" }

    fun run(action: DriveAction, token: String) = scope.launch {
        busy = if (action is DriveAction.Backup) "Encrypting ${docs.size} documents…" else "Downloading and decrypting…"
        runCatching {
            when (action) {
                DriveAction.Backup -> {
                    val r = app.backup.backup(token, s.viewableCopies) { msg -> busy = msg }
                    app.settings.update { it.copy(lastBackup = System.currentTimeMillis()) }
                    if (r.uploaded == 0 && r.removed == 0) "Backup complete · already up to date" else "Backup complete"
                }
                is DriveAction.Restore -> { app.backup.restore(token, action.password) { msg -> busy = msg }; "Vault restored" }
            }
        }.onSuccess { toast.show(it) }
            .onFailure { toast.show(if (it is javax.crypto.AEADBadTagException) "Wrong backup password" else "Didn't finish: ${it.message}") }
        busy = null
    }

    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val action = pending ?: return@rememberLauncherForActivityResult
        pending = null
        val token = runCatching { Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(result.data).accessToken }.getOrNull()
        if (token != null) run(action, token) else toast.show("Google Drive access wasn't granted")
    }

    fun drive(action: DriveAction) = scope.launch {
        val auth = runCatching { app.backup.authorize() }.getOrElse { toast.show("Couldn't reach Google: ${it.message}"); return@launch }
        val intent = auth.pendingIntent
        if (auth.hasResolution() && intent != null) {
            pending = action
            app.appLock.expectExternalActivity()
            consent.launch(IntentSenderRequest.Builder(intent.intentSender).build())
        } else auth.accessToken?.let { run(action, it) }
    }

    val countries = Countries.decode(account?.countries)

    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding()) {
        FdTopBar("Settings", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            // `.prof`
            account?.let { a ->
                Row(
                    Modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.surface)
                        .border(1.dp, c.line, RoundedCornerShape(20.dp)).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    FdAvatar(a.name, 64.dp, photoFile = self?.photoFile ?: a.photoFile, character = self?.avatar)
                    Column(Modifier.weight(1f)) {
                        Text(a.name, style = fdText(16f, FontWeight.Bold))
                        Text("${a.countryCode} ${a.phone}", style = fdText(12f, color = c.muted))
                        if (a.backupEmail.isNotBlank()) Text(a.backupEmail + if (a.googleLinked) " · Google" else "", style = fdText(12f, color = c.muted))
                    }
                    FdIconButton("edit", onEditAccount, iconSize = 19.dp, description = "Edit account")
                }
            }

            FdSectionTitle("Security")
            FdList {
                FdListItem("Change passcode", "${app.passcode.length} digits", first = true, icon = "lock", onClick = onChangePasscode)
                if (BiometricHelper.available(context)) FdListItem("Fingerprint unlock", "Passcode still works as fallback", icon = "finger", trailing = {
                    FdSwitch(s.biometric, { v -> app.settings.update { it.copy(biometric = v) } }, "Fingerprint unlock")
                })
                FdListItem("Auto-lock", "After ${SettingsStore.autoLockLabel(s.autoLockMs)} in background", icon = "refresh", onClick = {
                    val o = SettingsStore.AUTO_LOCK_OPTIONS
                    val next = o[(o.indexOfFirst { it.first == s.autoLockMs } + 1) % o.size].first
                    app.settings.update { it.copy(autoLockMs = next) }
                })
                FdListItem("Block screenshots", "Hides the app in Recents too", icon = "eyeoff", trailing = {
                    FdSwitch(s.blockScreenshots, { v -> app.settings.update { it.copy(blockScreenshots = v) } }, "Block screenshots")
                })
            }

            FdSectionTitle("Countries")
            FdList {
                FdListItem("Document countries", countries.joinToString(" · ") { Countries.name(it) }, first = true, icon = "plane", onClick = onEditAccount,
                    chevron = true, trailing = { Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { countries.take(4).forEach { FdFlag(it, 20.dp) } } })
            }

            FdSectionTitle("Backup")
            FdList {
                FdListItem("Google Drive backup", "My Drive › Family Docs · encrypted · weekly on Wi-Fi", first = true, icon = "cloud", trailing = {
                    FdSwitch(s.backupEnabled, { v ->
                        if (v && !app.backup.keys.hasPassword) askPassword = "set"
                        else { app.settings.update { it.copy(backupEnabled = v) }; BackupWorker.schedule(context, v) }
                    }, "Google Drive backup")
                })
                BackupNowRow(s.backupEnabled, s.lastBackup, busy) {
                    if (busy == null) { if (app.backup.keys.hasPassword) drive(DriveAction.Backup) else askPassword = "set" }
                }
                FdListItem("How it's organised in Drive", "One folder per person and per document", icon = "grid", onClick = { showTree = true })
                FdListItem("Viewable copies in Drive",
                    if (s.viewableCopies) "Scans also saved unencrypted, so you can open them in Drive" else "Off: everything in Drive is encrypted", icon = "eye", trailing = {
                        FdSwitch(s.viewableCopies, { v -> app.settings.update { it.copy(viewableCopies = v) } }, "Viewable copies in Drive")
                    })
                FdListItem("Restore from Drive", "Needs your backup password", icon = "refresh", onClick = { if (busy == null) askPassword = "restore" })
            }

            FdSectionTitle("About")
            FdList {
                FdListItem("Family Docs ${version.substringBeforeLast('.').ifBlank { version }}",
                    "${members.size} people · ${docs.size} documents on this phone", first = true, icon = "info")
            }
            Spacer(Modifier.height(30.dp))
        }
    }

    if (showTree) FdSheet({ showTree = false }) {
        SheetTitle("Your Google Drive",
            "Each document gets its own folder, colour-coded by type and labelled with the person, country and expiry, so Drive search finds it. " +
                "Files marked with a lock are encrypted with your backup password. Only changed documents are uploaded again, and deleted ones go to Drive's bin.")
        DriveTree(app.repo, s.viewableCopies)
    }

    askPassword?.let { mode ->
        var pw by remember { mutableStateOf("") }
        var pw2 by remember { mutableStateOf("") }
        val setting = mode == "set"
        val ok = if (setting) pw.length >= 8 && pw == pw2 else pw.isNotEmpty()
        FdSheet({ askPassword = null }) {
            SheetTitle(if (setting) "Choose a backup password" else "Backup password",
                if (setting) "Backups are encrypted with this password. You'll need it to restore on a new phone. It can't be recovered, so keep it somewhere safe."
                else "Enter the password you chose when you set up backups. The vault on this phone will be replaced.")
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                FdField("Password", hint = if (setting) "At least 8 characters" else null) {
                    FdInput(pw, { pw = it }, icon = "key", keyboardType = KeyboardType.Password, visualTransformation = PasswordVisualTransformation())
                }
                if (setting) FdField("Repeat password", error = if (pw2.isNotEmpty() && pw2 != pw) "Passwords don't match" else null) {
                    FdInput(pw2, { pw2 = it }, icon = "key", keyboardType = KeyboardType.Password, visualTransformation = PasswordVisualTransformation(),
                        error = pw2.isNotEmpty() && pw2 != pw)
                }
                FdButton(if (setting) "Save & back up" else "Restore", {
                    askPassword = null
                    if (setting) scope.launch {
                        withContext(Dispatchers.Default) { app.backup.keys.setPassword(pw.toCharArray()) }
                        app.settings.update { it.copy(backupEnabled = true) }; BackupWorker.schedule(context, true)
                        drive(DriveAction.Backup)
                    } else drive(DriveAction.Restore(pw.toCharArray()))
                }, enabled = ok)
                FdButton("Cancel", { askPassword = null }, kind = BtnKind.Ghost)
            }
        }
    }
}

/** "Back up now" with the demo's `.progress` bar under the status text while a backup runs; dimmed when backups are off. */
@Composable
private fun BackupNowRow(enabled: Boolean, last: Long, busy: String?, onClick: () -> Unit) {
    val c = Fd.c
    val sub = busy ?: if (last > 0) "Last backup ${fmtDate(last)}, ${SimpleDateFormat("HH:mm", Locale.UK).format(Date(last))}" else "Never backed up"
    Column(Modifier.alpha(if (enabled) 1f else .5f)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
        Row(
            Modifier.fillMaxWidth().tap(enabled = enabled, onClick = onClick).padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FdLic("upload")
            Column(Modifier.weight(1f)) {
                Text("Back up now", style = fdText(14f, FontWeight.SemiBold))
                Text(sub, style = fdText(12f, color = c.muted), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (busy != null) Progress()
            }
        }
    }
}

/** `.progress`: 6px green bar; the real upload has no fixed length, so it sweeps. */
@Composable
private fun Progress() {
    val c = Fd.c
    val t = rememberInfiniteTransition(label = "bk")
    val x by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart), label = "bkx")
    Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(6.dp).clip(RoundedCornerShape(6.dp)).background(c.surface2)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(x.coerceAtLeast(.04f)).clip(RoundedCornerShape(6.dp)).background(c.ok))
    }
}

private val folderVector: ImageVector by lazy {
    ImageVector.Builder("folder", 24.dp, 24.dp, 24f, 24f).apply {
        addPath(PathParser().parsePathString("M3 6.5A1.5 1.5 0 0 1 4.5 5h4.6l2 2h8.4A1.5 1.5 0 0 1 21 8.5v9A1.5 1.5 0 0 1 19.5 19h-15A1.5 1.5 0 0 1 3 17.5z").toNodes(),
            fill = SolidColor(Color.Black))
    }.build()
}

private class TreeRow(val depth: Int, val folder: Color?, val name: String, val label: String, val plain: Boolean = false)

/** `.dtree`: the same folder layout DriveBackup creates, built from what's on this phone. */
@Composable
private fun DriveTree(repo: VaultRepository, viewable: Boolean) {
    val c = Fd.c
    val rows by produceState(emptyList<TreeRow>(), viewable) {
        value = withContext(Dispatchers.IO) {
            val s = repo.snapshot()
            buildList {
                add(TreeRow(0, Color(0xFF8F8F94), "My Drive", ""))
                add(TreeRow(1, Color(0xFFE3962E), "Family Docs", ""))
                add(TreeRow(2, null, "Family Docs vault.fdenc", "index"))
                s.members.forEach { m ->
                    add(TreeRow(2, if (m.shared) Color(0xFFE3962E) else Color(0xFF5E7BB0), if (m.shared) "${m.name} (shared)" else m.name, if (m.shared) "everyone" else m.relation))
                    add(TreeRow(3, null, "Passwords.fdenc", "${s.passwords.count { it.memberId == m.id }} logins"))
                    s.documents.filter { it.memberId == m.id }.forEach { d ->
                        val t = DocTypes.forName(d.name)
                        val ctry = d.issuer
                        val exp = d.fields.firstOrNull { Regex("expiry|valid until", RegexOption.IGNORE_CASE).containsMatchIn(it.label) && it.value.isNotBlank() }?.value
                        add(TreeRow(3, Color(t.hue), d.name + if (ctry.isNotEmpty()) " · ${Countries.name(ctry)}" else "",
                            listOfNotNull(t.label.takeIf { t.matched }, exp?.let { "until $it" }).joinToString(" · ")))
                        if (d.fileId != null) {
                            val ext = if (d.fileMime == "application/pdf") "pdf" else "jpg"
                            add(TreeRow(4, null, "original.$ext.fdenc", ""))
                            if (viewable) add(TreeRow(4, null, "original.$ext", "viewable", plain = true))
                        }
                        add(TreeRow(4, null, "details.fdenc", "${d.fields.size} fields"))
                    }
                }
            }
        }
    }
    Column(
        Modifier.fillMaxWidth().heightIn(max = 420.dp).clip(RoundedCornerShape(14.dp)).background(c.bg).border(1.dp, c.line, RoundedCornerShape(14.dp))
            .verticalScroll(rememberScrollState()).padding(vertical = 8.dp, horizontal = 4.dp),
    ) {
        rows.forEach { r ->
            Row(Modifier.fillMaxWidth().padding(start = (8 + r.depth * 16).dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                if (r.folder != null) Icon(folderVector, null, Modifier.size(18.dp), tint = r.folder)
                else Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
                    FdIcon(if (r.plain) "image" else "lock", 13.dp, 2f, if (r.plain) c.danger else c.muted)
                }
                Text(r.name, style = fdText(12.5f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (r.label.isNotEmpty()) {
                    Spacer(Modifier.weight(1f))
                    Text(r.label, style = fdText(10.5f, color = c.muted), maxLines = 1, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}
