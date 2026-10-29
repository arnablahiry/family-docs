package com.familydocs.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.familydocs.app.ui.components.LocalContainer
import com.familydocs.app.ui.ds.BtnKind
import com.familydocs.app.ui.ds.Fd
import com.familydocs.app.ui.ds.FdButton
import com.familydocs.app.ui.ds.FdField
import com.familydocs.app.ui.ds.FdIconButton
import com.familydocs.app.ui.ds.FdInput
import com.familydocs.app.ui.ds.FdSheet
import com.familydocs.app.ui.ds.LocalToast
import com.familydocs.app.ui.ds.SheetTitle
import com.familydocs.app.ui.ds.fdText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MIN_LEN = 8

/** A password box with a show/hide eye. */
@Composable
private fun SecretInput(value: String, onChange: (String) -> Unit, placeholder: String) {
    var show by remember { mutableStateOf(false) }
    FdInput(value, onChange, placeholder = placeholder, keyboardType = KeyboardType.Password,
        visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
        trailing = { FdIconButton(if (show) "eyeoff" else "eye", { show = !show }, size = 36.dp, iconSize = 18.dp, tint = Fd.c.muted,
            description = if (show) "Hide" else "Show") })
}

/** Settings › Backup › Backup password for logins: turn on, change or turn off. */
@Composable
fun LoginLockSheet(onDismiss: () -> Unit) {
    val app = LocalContainer.current
    val c = Fd.c
    val toast = LocalToast.current
    val scope = rememberCoroutineScope()
    val on by app.loginLock.on.collectAsStateWithLifecycle()
    var mode by remember { mutableStateOf(if (on) "menu" else "set") } // menu, set, change, off
    var current by remember { mutableStateOf("") }
    var pw by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun work(block: suspend () -> String?) = scope.launch {
        busy = true; error = null
        val msg = runCatching { block() }.getOrElse { it.message ?: "Something went wrong" }
        busy = false
        if (msg == null) onDismiss() else error = msg
    }
    suspend fun currentOk() = withContext(Dispatchers.Default) { app.loginLock.matches(current.toCharArray()) }
    fun newOk(): String? = when {
        pw.length < MIN_LEN -> "Use at least $MIN_LEN characters (a few words is easiest to remember)"
        pw != again -> "The two don't match"
        else -> null
    }

    FdSheet(onDismiss) {
        when (mode) {
            "menu" -> {
                SheetTitle("Backup password for logins", "On. Your logins and the Common ones are locked with it in your Drive backup; on a new phone they come back only after you enter it.")
                FdButton("Change it", { mode = "change" }, kind = BtnKind.Ghost, icon = "edit")
                Spacer(Modifier.height(10.dp))
                FdButton("Turn it off", { mode = "off" }, kind = BtnKind.Ghost, icon = "close", contentColor = c.danger)
            }
            "set", "change" -> {
                SheetTitle(if (mode == "set") "Set a backup password" else "Change the backup password",
                    "Locks your logins (and the Common ones) in your Drive backup with a key only this password makes. It's never stored or uploaded. " +
                        "If you forget it, backed-up logins can't be recovered by anyone; the ones on this phone stay.")
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (mode == "change") FdField("Current backup password") { SecretInput(current, { current = it }, "Current password") }
                    FdField(if (mode == "set") "Backup password" else "New backup password") { SecretInput(pw, { pw = it }, "e.g. mango river lotus 42") }
                    FdField("Again") { SecretInput(again, { again = it }, "Repeat it") }
                    error?.let { Text(it, style = fdText(12.5f, color = c.danger)) }
                    FdButton(if (busy) "Locking your logins…" else if (mode == "set") "Turn on" else "Change", {
                        work {
                            newOk() ?: run {
                                if (mode == "change" && !currentOk()) return@work "That's not the current backup password"
                                withContext(Dispatchers.Default) { app.loginLock.set(pw.toCharArray()) }
                                toast.show(if (mode == "set") "Backup password on: the next backup locks your logins with it" else "Backup password changed")
                                null
                            }
                        }
                    }, enabled = !busy)
                }
            }
            "off" -> {
                SheetTitle("Turn off the backup password?", "Logins in your Drive backup are then protected by the backup's normal encryption only, so anyone who can sign in to your Google account could restore them.")
                FdField("Current backup password") { SecretInput(current, { current = it }, "Current password") }
                error?.let { Text(it, style = fdText(12.5f, color = c.danger), modifier = Modifier.padding(top = 8.dp)) }
                Spacer(Modifier.height(14.dp))
                FdButton("Turn off", {
                    work {
                        if (!currentOk()) "That's not the current backup password"
                        else { app.loginLock.turnOff(); toast.show("Backup password off"); null }
                    }
                }, kind = BtnKind.Danger, enabled = !busy)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** After restoring on a new phone: enter the backup password to bring the logins back. */
@Composable
fun UnlockLoginsSheet(onDismiss: () -> Unit) {
    val app = LocalContainer.current
    val c = Fd.c
    val toast = LocalToast.current
    val scope = rememberCoroutineScope()
    var pw by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    FdSheet(onDismiss) {
        SheetTitle("Bring back your logins", "Your backup's logins (yours and the Common ones) are locked with your backup password. Everything else is already here.")
        SecretInput(pw, { pw = it }, "Backup password")
        error?.let { Text(it, style = fdText(12.5f, color = c.danger), modifier = Modifier.padding(top = 8.dp)) }
        Spacer(Modifier.height(14.dp))
        FdButton(if (busy) "Unlocking…" else "Unlock logins", {
            scope.launch {
                busy = true; error = null
                runCatching { app.backup.unlockLogins(pw.toCharArray()) }
                    .onSuccess { n -> toast.show(if (n == 1) "1 login is back" else "$n logins are back"); onDismiss() }
                    .onFailure { error = "That's not the backup password. Try again (it's the one you set in Settings › Backup on your other phone)." }
                busy = false
            }
        }, enabled = pw.isNotEmpty() && !busy)
        Spacer(Modifier.height(8.dp))
    }
}
