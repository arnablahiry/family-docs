package com.familydocs.app.ui.passwords

import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.familydocs.app.data.PasswordEntity
import com.familydocs.app.ui.components.LocalContainer
import com.familydocs.app.ui.components.copySensitive
import com.familydocs.app.ui.ds.Fd
import com.familydocs.app.ui.ds.FdButton
import com.familydocs.app.ui.ds.FdEmpty
import com.familydocs.app.ui.ds.FdFab
import com.familydocs.app.ui.ds.FdField
import com.familydocs.app.ui.ds.FdIconButton
import com.familydocs.app.ui.ds.FdInput
import com.familydocs.app.ui.ds.FdLic
import com.familydocs.app.ui.ds.FdList
import com.familydocs.app.ui.ds.FdSectionTitle
import com.familydocs.app.ui.ds.FdSheet
import com.familydocs.app.ui.ds.FdTopBar
import com.familydocs.app.ui.ds.LocalToast
import com.familydocs.app.ui.ds.SheetTitle
import com.familydocs.app.ui.ds.fdText
import kotlinx.coroutines.launch
import java.security.SecureRandom

/** Demo `passwords`: "<name> · Passwords", "N saved", a `.list` of logins with reveal / copy / delete, and an "Add login" fab. */
@Composable
fun PasswordsScreen(memberId: String, onBack: () -> Unit) {
    val app = LocalContainer.current
    val c = Fd.c
    val context = LocalContext.current
    val toast = LocalToast.current
    val scope = rememberCoroutineScope()
    val member by app.repo.member(memberId).collectAsStateWithLifecycle(null)
    val items by app.repo.passwords(memberId).collectAsStateWithLifecycle(emptyList())
    var revealed by remember { mutableStateOf(setOf<String>()) }
    var adding by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(c.bg)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            FdTopBar("${member?.name.orEmpty()} · Passwords", onBack)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                FdSectionTitle("${items.size} saved")
                if (items.isEmpty()) FdEmpty("No passwords yet.")
                else FdList {
                    items.forEachIndexed { i, p ->
                        PwRow(p, i == 0, p.id in revealed,
                            onReveal = { revealed = if (p.id in revealed) revealed - p.id else revealed + p.id },
                            onCopy = { copySensitive(context, p.site, p.password); toast.show("Copied") },
                            onDelete = { scope.launch { app.repo.deletePassword(p.id); toast.show("Deleted ${p.site}") } })
                    }
                }
                Spacer(Modifier.height(100.dp))
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
                    scope.launch { app.repo.savePassword(memberId, site.trim(), user.trim(), pass); adding = false; toast.show("Saved ${site.trim()}") }
                })
            }
        }
    }
}

@Composable
private fun PwRow(p: PasswordEntity, first: Boolean, show: Boolean, onReveal: () -> Unit, onCopy: () -> Unit, onDelete: () -> Unit) {
    val c = Fd.c
    Column {
        if (!first) Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FdLic("key")
            Column(Modifier.weight(1f)) {
                Text(p.site, style = fdText(14f, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (p.username.isNotBlank()) Text(p.username, style = fdText(12f, color = c.muted), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (show) p.password else "••••••••••", style = fdText(12f, color = c.muted).copy(fontFamily = FontFamily.Monospace),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            FdIconButton(if (show) "eyeoff" else "eye", onReveal, iconSize = 19.dp, tint = c.fg, description = if (show) "Hide password" else "Show password")
            FdIconButton("copy", onCopy, iconSize = 18.dp, tint = c.fg, description = "Copy password")
            FdIconButton("trash", onDelete, iconSize = 18.dp, tint = c.fg, description = "Delete")
        }
    }
}

private val WORDS = listOf("mango", "river", "lotus", "monsoon", "cedar", "harbor", "saffron", "olive", "aegean", "tulip")
private fun generatePassword(): String {
    val r = SecureRandom()
    fun w() = WORDS[r.nextInt(WORDS.size)]
    return "${w()}-${w().replaceFirstChar { it.uppercase() }}-${r.nextInt(90) + 10}${"!#%&"[r.nextInt(4)]}"
}
