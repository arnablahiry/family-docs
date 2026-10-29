package com.familydocs.app.ui.passwords

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.familydocs.app.data.PasswordEntity
import com.familydocs.app.security.BiometricHelper
import com.familydocs.app.security.PasscodeManager
import com.familydocs.app.ui.components.LocalContainer
import com.familydocs.app.ui.components.copySensitive
import com.familydocs.app.ui.ds.BtnKind
import com.familydocs.app.ui.ds.Fd
import com.familydocs.app.ui.ds.FdButton
import com.familydocs.app.ui.ds.FdChip
import com.familydocs.app.ui.ds.FdDots
import com.familydocs.app.ui.ds.FdEmpty
import com.familydocs.app.ui.ds.FdFab
import com.familydocs.app.ui.ds.FdField
import com.familydocs.app.ui.ds.FdIcon
import com.familydocs.app.ui.ds.FdIconButton
import com.familydocs.app.ui.ds.FdInput
import com.familydocs.app.ui.ds.FdKeypad
import com.familydocs.app.ui.ds.FdLic
import com.familydocs.app.ui.ds.FdSectionTitle
import com.familydocs.app.ui.ds.FdSheet
import com.familydocs.app.ui.ds.FdTopBar
import com.familydocs.app.ui.ds.LocalToast
import com.familydocs.app.ui.ds.SheetTitle
import com.familydocs.app.ui.ds.fdText
import com.familydocs.app.ui.ds.tap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.SecureRandom

/** After the passwords PIN, seeing or copying more logins on this screen doesn't ask again for this long. */
private const val UNLOCKED_MS = 60_000L

/**
 * A person's saved logins: each card shows the site, the username and the password hidden. Tap one to lift it
 * (highlighted, the others glide down) and View, Copy and Delete appear under it. Seeing or copying a password needs the
 * passwords PIN, a second PIN set with the first login you save (not the app passcode); deleting needs typing DELETE.
 */
@Composable
fun PasswordsScreen(memberId: String, onBack: () -> Unit) {
    val app = LocalContainer.current
    val c = Fd.c
    val context = LocalContext.current
    val toast = LocalToast.current
    val scope = rememberCoroutineScope()
    val member by app.repo.member(memberId).collectAsStateWithLifecycle(null)
    val items by app.repo.passwords(memberId).collectAsStateWithLifecycle(emptyList())
    var open by remember { mutableStateOf<String?>(null) }
    var revealed by remember { mutableStateOf(setOf<String>()) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<PasswordEntity?>(null) }
    var unlockedUntil by remember { mutableLongStateOf(0L) }
    // what to do once the PIN is entered (or set up)
    var pinFor by remember { mutableStateOf<(() -> Unit)?>(null) }
    var settingPin by remember { mutableStateOf<(() -> Unit)?>(null) }

    // One passwords PIN per phone, for every login you can see (yours, Common's, anyone's). The first time a
    // passwords page has logins and there's no PIN yet (say someone added a Common login and it just synced), set it.
    LaunchedEffect(items.isNotEmpty()) { if (items.isNotEmpty() && !app.pwPin.isSet && settingPin == null) settingPin = {} }

    fun withPin(then: () -> Unit) {
        when {
            System.currentTimeMillis() < unlockedUntil -> then()
            !app.pwPin.isSet -> settingPin = then // logins saved before the PIN existed: set it now
            else -> pinFor = then
        }
    }

    Box(Modifier.fillMaxSize().background(c.bg)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            FdTopBar("${member?.name.orEmpty()} · Passwords", onBack)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                FdSectionTitle("${items.size} saved")
                if (items.isEmpty()) FdEmpty("No passwords yet.")
                else Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items.forEach { p ->
                        PwCard(p, selected = p.id == open, show = p.id in revealed,
                            onTap = { open = if (open == p.id) null else p.id },
                            onView = { if (p.id in revealed) revealed = revealed - p.id else withPin { revealed = revealed + p.id } },
                            onCopy = { withPin { copySensitive(context, p.site, p.password); toast.show("Copied the password for ${p.site}") } },
                            onDelete = {
                                if (p.origin != null) toast.show("Shared by another member of the group: they can remove it on their phone")
                                else deleting = p
                            })
                    }
                }
                Spacer(Modifier.height(110.dp))
            }
        }
        FdFab("Add login", onClick = { adding = true }, modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 18.dp, bottom = 22.dp))
    }

    if (adding) {
        var site by remember { mutableStateOf("") }
        var user by remember { mutableStateOf("") }
        var pass by remember { mutableStateOf("") }
        FdSheet({ adding = false }) {
            SheetTitle("Add login", "For ${member?.name.orEmpty()}")
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                FdField("Website or app") { FdInput(site, { site = it }, placeholder = "e.g. DigiLocker") }
                FdField("Username or phone") { FdInput(user, { user = it }) }
                FdField("Password") {
                    FdInput(pass, { pass = it }, mono = true, trailing = {
                        FdIconButton("dice", { pass = generatePassword() }, size = 36.dp, iconSize = 19.dp, tint = c.muted, description = "Generate password")
                    })
                }
                FdButton("Save login", {
                    if (site.isBlank()) { toast.show("Add the website or app name"); return@FdButton }
                    val save = { scope.launch { app.repo.savePassword(memberId, site.trim(), user.trim(), pass); adding = false; toast.show("Saved ${site.trim()}") }; Unit }
                    // the first login: set the passwords PIN that will protect it
                    if (!app.pwPin.isSet) settingPin = save else save()
                })
            }
        }
    }

    pinFor?.let { then ->
        PinSheet(app.pwPin, onOk = { pinFor = null; unlockedUntil = System.currentTimeMillis() + UNLOCKED_MS; then() },
            onForgot = {
                val activity = context as? androidx.fragment.app.FragmentActivity
                if (activity == null || !BiometricHelper.deviceSecure(activity)) toast.show("Set a screen lock on this phone first, then reset the PIN")
                else {
                    app.appLock.expectExternalActivity()
                    BiometricHelper.confirmDevice(activity, onSuccess = { pinFor = null; settingPin = then }, onFail = { toast.show("The phone's screen lock wasn't confirmed ($it)") })
                }
            },
            onDismiss = { pinFor = null })
    }
    settingPin?.let { then ->
        SetPinSheet(app.pwPin, app.passcode, onDone = { settingPin = null; unlockedUntil = System.currentTimeMillis() + UNLOCKED_MS; toast.show("Passwords PIN set"); then() },
            onDismiss = { settingPin = null })
    }
    deleting?.let { p ->
        var typed by remember(p.id) { mutableStateOf("") }
        FdSheet({ deleting = null }) {
            SheetTitle("Delete ${p.site}?", "The login is removed from this phone and from your backup. Type DELETE to confirm.")
            FdInput(typed, { typed = it.uppercase().take(6) }, placeholder = "DELETE", mono = true)
            Spacer(Modifier.height(14.dp))
            FdButton("Delete", {
                scope.launch { app.repo.deletePassword(p.id); deleting = null; open = null; toast.show("Deleted ${p.site}") }
            }, kind = BtnKind.Danger, enabled = typed == "DELETE")
            Spacer(Modifier.height(10.dp))
            FdButton("Cancel", { deleting = null }, kind = BtnKind.Ghost)
        }
    }
}

/** One login: site, username, password hidden. Lifted: a highlighted border and View · Copy · Delete under it. */
@Composable
private fun PwCard(p: PasswordEntity, selected: Boolean, show: Boolean, onTap: () -> Unit, onView: () -> Unit, onCopy: () -> Unit, onDelete: () -> Unit) {
    val c = Fd.c
    val shape = RoundedCornerShape(18.dp)
    val border by animateColorAsState(if (selected) c.primary else c.line, tween(220), label = "pwBorder")
    val bg by animateColorAsState(if (selected) c.surface2 else c.surface, tween(220), label = "pwBg")
    Column(
        Modifier.fillMaxWidth().animateContentSize(spring(dampingRatio = .86f, stiffness = Spring.StiffnessMediumLow))
            .clip(shape).background(bg).border(if (selected) 2.dp else 1.dp, border, shape).tap(onClick = onTap).padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            FdLic("key")
            Column(Modifier.weight(1f)) {
                Text(p.site, style = fdText(17f, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (p.username.isNotBlank()) Text(p.username, style = fdText(14.5f, color = c.muted), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp))
                Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (!show) FdIcon("lock", 13.dp, tint = c.muted)
                    Text(if (show) p.password else "••••••••••", style = fdText(15f, color = if (show) c.fg else c.muted).copy(fontFamily = FontFamily.Monospace),
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        AnimatedVisibility(selected, enter = expandVertically(spring(dampingRatio = .86f, stiffness = Spring.StiffnessMediumLow)) + fadeIn(tween(220, 80)),
            exit = shrinkVertically(tween(200)) + fadeOut(tween(120))) {
            Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionKey(if (show) "eyeoff" else "eye", if (show) "Hide password" else "View password", c.fg, onView, Modifier.weight(1f))
                ActionKey("copy", "Copy password", c.fg, onCopy, Modifier.weight(1f))
                ActionKey("trash", "Delete login", c.danger, onDelete, Modifier.weight(1f))
            }
        }
    }
}

/** An icon-only pill under a lifted login (View, Copy, Delete). */
@Composable
private fun ActionKey(icon: String, description: String, tint: androidx.compose.ui.graphics.Color, onClick: () -> Unit, modifier: Modifier) {
    val c = Fd.c
    Box(modifier.height(46.dp).clip(RoundedCornerShape(14.dp)).background(c.surface).border(1.dp, c.line, RoundedCornerShape(14.dp))
        .tap(onClick = onClick).semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        FdIcon(icon, 21.dp, tint = tint)
    }
}

/** Enter the passwords PIN. */
@Composable
private fun PinSheet(pin: PasscodeManager, onOk: () -> Unit, onForgot: () -> Unit, onDismiss: () -> Unit) {
    val c = Fd.c
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var shake by remember { mutableIntStateOf(0) }
    var msg by remember { mutableStateOf<String?>(null) }
    var ok by remember { mutableStateOf(false) }
    val len = pin.length
    FdSheet(onDismiss) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Passwords PIN", style = fdText(18f, FontWeight.Bold))
            Text(msg ?: "Not your app passcode: the PIN you set for passwords", style = fdText(13f, color = if (msg != null) c.danger else c.muted),
                textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp, bottom = 22.dp))
            FdDots(len, code.length, shake, success = ok)
            Spacer(Modifier.height(26.dp))
            FdKeypad(onDigit = { d ->
                if (code.length >= len || ok) return@FdKeypad
                code += d
                if (code.length == len) scope.launch {
                    val r = withContext(Dispatchers.Default) { pin.check(code) }
                    when (r) {
                        PasscodeManager.Check.Ok -> { ok = true; delay(450); onOk() }
                        is PasscodeManager.Check.Wrong -> { msg = "Wrong PIN · ${r.triesLeft} ${if (r.triesLeft == 1) "try" else "tries"} left"; shake++; code = "" }
                        is PasscodeManager.Check.LockedOut -> { msg = "Too many tries · wait ${r.millisLeft / 1000 + 1} s"; shake++; code = "" }
                    }
                }
            }, onDelete = { code = code.dropLast(1) })
            Text("Forgot the PIN?", style = fdText(13f, FontWeight.Medium, c.primary),
                modifier = Modifier.padding(top = 18.dp, bottom = 8.dp).clip(RoundedCornerShape(10.dp)).tap(onClick = onForgot).padding(horizontal = 10.dp, vertical = 6.dp))
        }
    }
}

/** Choose the passwords PIN (twice); it can't be the app passcode. */
@Composable
private fun SetPinSheet(pin: PasscodeManager, appPasscode: PasscodeManager, onDone: () -> Unit, onDismiss: () -> Unit) {
    val c = Fd.c
    val scope = rememberCoroutineScope()
    var len by remember { mutableIntStateOf(4) }
    var first by remember { mutableStateOf<String?>(null) }
    var code by remember { mutableStateOf("") }
    var shake by remember { mutableIntStateOf(0) }
    var msg by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    FdSheet(onDismiss) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (first == null) "Set a passwords PIN" else "Enter it again", style = fdText(18f, FontWeight.Bold))
            Text(msg ?: "One PIN for every saved password on this phone (yours, Common's, everyone's), asked before one is shown or copied. Not your app passcode.",
                style = fdText(13f, color = if (msg != null) c.danger else c.muted), textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp, bottom = 14.dp))
            if (first == null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 16.dp)) {
                listOf(4, 6).forEach { n -> FdChip("$n digits", len == n, { len = n; code = "" }) }
            }
            FdDots(len, code.length, shake)
            Spacer(Modifier.height(26.dp))
            FdKeypad(onDigit = { d ->
                if (code.length >= len || busy) return@FdKeypad
                code += d
                if (code.length < len) return@FdKeypad
                val entered = code
                when {
                    first == null -> scope.launch {
                        busy = true
                        val same = withContext(Dispatchers.Default) { appPasscode.matches(entered) }
                        busy = false
                        if (same) { msg = "That's your app passcode. Choose a different PIN."; shake++; code = "" }
                        else { first = entered; msg = null; code = "" }
                    }
                    entered != first -> { msg = "The two PINs didn't match. Start again."; shake++; first = null; code = "" }
                    else -> scope.launch { busy = true; withContext(Dispatchers.Default) { pin.set(entered) }; busy = false; onDone() }
                }
            }, onDelete = { code = code.dropLast(1) })
            Spacer(Modifier.height(10.dp))
        }
    }
}

private val WORDS = listOf("mango", "river", "lotus", "monsoon", "cedar", "harbor", "saffron", "olive", "aegean", "tulip")
private fun generatePassword(): String {
    val r = SecureRandom()
    fun w() = WORDS[r.nextInt(WORDS.size)]
    return "${w()}-${w().replaceFirstChar { it.uppercase() }}-${r.nextInt(90) + 10}${"!#%&"[r.nextInt(4)]}"
}
