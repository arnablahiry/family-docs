package com.familydocs.app.ui.security

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.familydocs.app.security.BiometricHelper
import com.familydocs.app.security.PasscodeManager
import com.familydocs.app.ui.components.Avatar
import com.familydocs.app.ui.components.BackBar
import com.familydocs.app.ui.components.LocalContainer
import com.familydocs.app.ui.components.PinDots
import com.familydocs.app.ui.components.PinPad
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Choose a 4- or 6-digit passcode, then confirm it. */
@Composable
fun PasscodeSetupScreen(title: String, onBack: (() -> Unit)?, onComplete: (String) -> Unit) {
    var length by remember { mutableIntStateOf(6) }
    var first by remember { mutableStateOf<String?>(null) }
    var pin by remember { mutableStateOf("") }
    var shake by remember { mutableIntStateOf(0) }
    var sub by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().safeDrawingPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (onBack != null) BackBar(title, onBack) else Spacer(Modifier.height(56.dp))
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(60.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Lock, null, tint = MaterialTheme.colorScheme.secondary) }
        }
        Text(if (first == null) "Create a passcode" else "Type it once more", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 16.dp))
        Text(sub ?: if (first == null) "You'll use it every time you open Family Docs" else "Confirm your new passcode",
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
        if (first == null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 10.dp)) {
            listOf(4, 6).forEach { n -> FilterChip(selected = length == n, onClick = { length = n; pin = "" }, label = { Text("$n digits") }) }
        }
        Spacer(Modifier.height(22.dp))
        PinDots(length, pin.length, shake)
        Spacer(Modifier.weight(1f))
        PinPad(
            onDigit = { d ->
                if (pin.length >= length) return@PinPad
                pin += d
                if (pin.length == length) scope.launch {
                    delay(140)
                    val f = first
                    when {
                        f == null -> { first = pin; pin = ""; sub = null }
                        f == pin -> onComplete(pin)
                        else -> { shake++; sub = "Those didn't match. Start again."; delay(700); first = null; pin = "" }
                    }
                }
            },
            onDelete = { pin = pin.dropLast(1) },
        )
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
fun LockScreen(activity: FragmentActivity) {
    val c = LocalContainer.current
    val account by c.repo.account.collectAsStateWithLifecycle(null)
    val settings by c.settings.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var shake by remember { mutableIntStateOf(0) }
    var sub by remember { mutableStateOf("Enter your passcode") }
    var lockedFor by remember { mutableLongStateOf(c.passcode.lockoutLeft()) }
    var forgot by remember { mutableStateOf(false) }
    var resetting by remember { mutableStateOf(false) }
    val bio = settings.biometric && BiometricHelper.available(activity)
    val len = c.passcode.length

    fun biometric() = BiometricHelper.authenticate(activity, "Unlock Family Docs", onSuccess = { c.appLock.unlock() })
    LaunchedEffect(Unit) { if (bio) biometric() }
    LaunchedEffect(lockedFor > 0) {
        while (lockedFor > 0) { sub = "Too many tries. Wait ${(lockedFor + 999) / 1000} s"; delay(500); lockedFor = c.passcode.lockoutLeft() }
        sub = "Enter your passcode"
    }

    if (resetting) {
        PasscodeSetupScreen("Choose a new passcode", onBack = { resetting = false }) { code ->
            scope.launch { withContext(Dispatchers.Default) { c.passcode.set(code) }; c.appLock.unlock() }
        }
        return
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(top = 36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Avatar(account?.name ?: "", account?.photoFile, 84.dp)
            Text("Welcome back${account?.name?.let { ", " + it.substringBefore(' ') } ?: ""}", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 16.dp))
            Text(sub, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(22.dp))
            PinDots(len, pin.length, shake)
            Spacer(Modifier.weight(1f))
            PinPad(
                onDigit = { d ->
                    if (lockedFor > 0 || pin.length >= len) return@PinPad
                    pin += d
                    if (pin.length == len) scope.launch {
                        when (val r = withContext(Dispatchers.Default) { c.passcode.check(pin) }) {
                            PasscodeManager.Check.Ok -> c.appLock.unlock()
                            is PasscodeManager.Check.Wrong -> { shake++; sub = "Wrong passcode · ${r.triesLeft} tries left"; pin = "" }
                            is PasscodeManager.Check.LockedOut -> { shake++; pin = ""; lockedFor = r.millisLeft }
                        }
                    }
                },
                onDelete = { pin = pin.dropLast(1) },
                onBiometric = if (bio) ({ biometric() }) else null,
            )
            TextButton({ forgot = true }, Modifier.padding(vertical = 12.dp)) { Text("Forgot passcode?") }
        }
    }

    if (forgot) {
        val email = account?.backupEmail.orEmpty()
        var error by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { forgot = false },
            title = { Text("Forgot your passcode?") },
            text = { Text(error ?: "Sign in with Google as ${mask(email)} to prove it's you. Your documents stay as they are.") },
            confirmButton = {
                TextButton({
                    scope.launch {
                        c.appLock.expectExternalActivity()
                        c.google.signIn(activity)
                            .onSuccess { p -> if (p.email.equals(email, ignoreCase = true)) { forgot = false; resetting = true } else error = "That Google account (${p.email}) isn't the backup email for this vault." }
                            .onFailure { error = "Sign-in didn't finish. Try again." }
                    }
                }) { Text("Verify with Google") }
            },
            dismissButton = { TextButton({ forgot = false }) { Text("Cancel") } },
        )
    }
}

private fun mask(email: String): String {
    val at = email.indexOf('@'); if (at < 2) return email
    return email.first() + "•".repeat(minOf(at - 1, 6)) + email.substring(at)
}
