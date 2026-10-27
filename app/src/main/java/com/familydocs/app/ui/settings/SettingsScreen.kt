package com.familydocs.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Lock
import com.familydocs.app.security.ThemeMode
import com.familydocs.app.ui.components.LocalThemeReveal
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.material.icons.outlined.Public
import com.familydocs.app.domain.Countries
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.auth.api.identity.Identity
import com.familydocs.app.backup.BackupWorker
import com.familydocs.app.security.BiometricHelper
import com.familydocs.app.security.SettingsStore
import com.familydocs.app.ui.components.Avatar
import com.familydocs.app.ui.components.BackBar
import com.familydocs.app.ui.components.LocalContainer
import com.familydocs.app.ui.components.SectionTitle
import com.familydocs.app.ui.components.outlinedCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

private sealed interface DriveAction { data object Backup : DriveAction; data class Restore(val password: CharArray) : DriveAction }

@Composable
fun SettingsScreen(onBack: () -> Unit, onEditAccount: () -> Unit, onChangePasscode: () -> Unit) {
    val c = LocalContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val account by c.repo.account.collectAsStateWithLifecycle(null)
    val s by c.settings.state.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    var busy by remember { mutableStateOf<String?>(null) }
    var askPassword by remember { mutableStateOf<String?>(null) } // "set" | "restore"
    var pending by remember { mutableStateOf<DriveAction?>(null) }

    fun run(action: DriveAction, token: String) = scope.launch {
        busy = if (action is DriveAction.Backup) "Encrypting and uploading…" else "Downloading and decrypting…"
        runCatching {
            when (action) {
                DriveAction.Backup -> {
                    val r = c.backup.backup(token, s.viewableCopies) { msg -> busy = msg }
                    c.settings.update { it.copy(lastBackup = System.currentTimeMillis()) }
                    "Backed up: ${r.uploaded} updated, ${r.unchanged} already up to date" + if (r.removed > 0) ", ${r.removed} removed" else ""
                }
                is DriveAction.Restore -> { c.backup.restore(token, action.password) { msg -> busy = msg }; "Vault restored" }
            }
        }.onSuccess { snack.showSnackbar(it) }
            .onFailure { snack.showSnackbar(if (it is javax.crypto.AEADBadTagException) "Wrong backup password" else "Didn't finish: ${it.message}") }
        busy = null
    }

    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val action = pending ?: return@rememberLauncherForActivityResult
        pending = null
        val token = runCatching { Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(result.data).accessToken }.getOrNull()
        if (token != null) run(action, token) else scope.launch { snack.showSnackbar("Google Drive access wasn't granted") }
    }

    fun drive(action: DriveAction) = scope.launch {
        val auth = runCatching { c.backup.authorize() }.getOrElse { snack.showSnackbar("Couldn't reach Google: ${it.message}"); return@launch }
        val intent = auth.pendingIntent
        if (auth.hasResolution() && intent != null) {
            pending = action
            c.appLock.expectExternalActivity()
            consent.launch(IntentSenderRequest.Builder(intent.intentSender).build())
        } else auth.accessToken?.let { run(action, it) }
    }

    Scaffold(topBar = { BackBar("Settings", onBack) }, snackbarHost = { SnackbarHost(snack) }, containerColor = MaterialTheme.colorScheme.background) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState())) {
            account?.let { a ->
                Row(Modifier.padding(horizontal = 20.dp).fillMaxWidth().outlinedCard(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.outlineVariant, 20.dp)
                    .clickable(onClick = onEditAccount).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(a.name, a.photoFile, 64.dp)
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        Text(a.name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        Text("${a.countryCode} ${a.phone}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(a.backupEmail + if (a.googleLinked) " · Google" else "", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Outlined.Edit, "Edit account")
                }
            }

            SectionTitle("Security")
            Group {
                Item(Icons.Outlined.Lock, "Change passcode", "${c.passcode.length} digits", onClick = onChangePasscode)
                if (BiometricHelper.available(context)) Item(Icons.Outlined.Fingerprint, "Fingerprint unlock", "Passcode still works as fallback") {
                    Switch(s.biometric, { v -> c.settings.update { it.copy(biometric = v) } })
                }
                var menu by remember { mutableStateOf(false) }
                Box {
                    Item(Icons.Outlined.Timer, "Auto-lock", "After ${SettingsStore.autoLockLabel(s.autoLockMs)} in background", onClick = { menu = true })
                    DropdownMenu(menu, { menu = false }) {
                        SettingsStore.AUTO_LOCK_OPTIONS.forEach { (ms, label) -> DropdownMenuItem({ Text(label) }, { c.settings.update { it.copy(autoLockMs = ms) }; menu = false }) }
                    }
                }
                Item(Icons.Outlined.VisibilityOff, "Block screenshots", "Hides the app in Recents too") {
                    Switch(s.blockScreenshots, { v -> c.settings.update { it.copy(blockScreenshots = v) } })
                }
            }

            SectionTitle("Appearance")
            Group {
                var themeMenu by remember { mutableStateOf(false) }
                val reveal = LocalThemeReveal.current
                var themeRowCenter by remember { mutableStateOf<Offset?>(null) }
                Box(Modifier.onGloballyPositioned { themeRowCenter = it.boundsInRoot().center }) {
                    Item(Icons.Outlined.DarkMode, "Theme", when (s.theme) { ThemeMode.SYSTEM -> "Same as phone"; ThemeMode.LIGHT -> "Light"; ThemeMode.DARK -> "Dark" }, onClick = { themeMenu = true })
                    DropdownMenu(themeMenu, { themeMenu = false }) {
                        listOf(ThemeMode.SYSTEM to "Same as phone", ThemeMode.LIGHT to "Light", ThemeMode.DARK to "Dark").forEach { (m, l) ->
                            DropdownMenuItem({ Text(l) }, {
                                themeMenu = false
                                scope.launch { reveal.play(themeRowCenter) { c.settings.update { it.copy(theme = m) } } }
                            })
                        }
                    }
                }
            }

            SectionTitle("Countries")
            Group {
                Item(Icons.Outlined.Public, "Document countries", Countries.decode(account?.countries).joinToString(" · ") { Countries.name(it) }, onClick = onEditAccount)
            }

            SectionTitle("Backup")
            Group {
                Item(Icons.Outlined.CloudUpload, "Google Drive backup", "My Drive › Family Docs › one folder per person and document · encrypted · weekly on Wi-Fi") {
                    Switch(s.backupEnabled, { v ->
                        if (v && !c.backup.keys.hasPassword) askPassword = "set"
                        else { c.settings.update { it.copy(backupEnabled = v) }; BackupWorker.schedule(context, v) }
                    })
                }
                Item(Icons.Outlined.CloudUpload, "Back up now",
                    if (s.lastBackup > 0) "Last backup " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(s.lastBackup)) else "Never backed up",
                    onClick = { if (busy == null) { if (c.backup.keys.hasPassword) drive(DriveAction.Backup) else askPassword = "set" } })
                busy?.let {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { Text(it, fontSize = 12.sp); LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 6.dp)) }
                }
                Item(Icons.Outlined.VisibilityOff, "Viewable copies in Drive", if (s.viewableCopies) "Scans also saved unencrypted, so you can open them in Drive" else "Off: everything in Drive is encrypted") {
                    Switch(s.viewableCopies, { v -> c.settings.update { it.copy(viewableCopies = v) } })
                }
                Item(Icons.Outlined.Restore, "Restore from Drive", "Replaces this phone's vault · needs your backup password", onClick = { askPassword = "restore" })
            }

            SectionTitle("About")
            Group { Item(Icons.Outlined.Info, "Family Docs 0.1", "Documents never leave this phone except as an encrypted Drive backup") }
            Spacer(Modifier.height(30.dp))
        }
    }

    askPassword?.let { mode ->
        var pw by remember { mutableStateOf("") }
        var pw2 by remember { mutableStateOf("") }
        val setting = mode == "set"
        AlertDialog(
            onDismissRequest = { askPassword = null },
            title = { Text(if (setting) "Choose a backup password" else "Backup password") },
            text = {
                Column {
                    Text(
                        if (setting) "Backups are encrypted with this password. You'll need it to restore on a new phone. It can't be recovered, so keep it somewhere safe."
                        else "Enter the password you chose when you set up backups. Your current vault on this phone will be replaced.",
                        fontSize = 13.sp,
                    )
                    OutlinedTextField(pw, { pw = it }, label = { Text("Password") }, singleLine = true, visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.padding(top = 10.dp))
                    if (setting) OutlinedTextField(pw2, { pw2 = it }, label = { Text("Repeat password") }, singleLine = true, visualTransformation = PasswordVisualTransformation(),
                        isError = pw2.isNotEmpty() && pw2 != pw, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        askPassword = null
                        if (setting) scope.launch {
                            withContext(Dispatchers.Default) { c.backup.keys.setPassword(pw.toCharArray()) }
                            c.settings.update { it.copy(backupEnabled = true) }; BackupWorker.schedule(context, true)
                            drive(DriveAction.Backup)
                        } else drive(DriveAction.Restore(pw.toCharArray()))
                    },
                    enabled = if (setting) pw.length >= 8 && pw == pw2 else pw.isNotEmpty(),
                ) { Text(if (setting) "Save & back up" else "Restore") }
            },
            dismissButton = { TextButton({ askPassword = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Group(content: @Composable () -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp).outlinedCard(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.outlineVariant)) { content() }
}

@Composable
private fun Item(icon: ImageVector, title: String, subtitle: String, onClick: (() -> Unit)? = null, trailing: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        when {
            trailing != null -> trailing()
            onClick != null -> Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}
