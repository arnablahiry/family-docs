package com.familydocs.app.ui.home

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.familydocs.app.auth.GoogleAccountHelper
import com.familydocs.app.domain.GroupInvite
import com.familydocs.app.ui.components.LocalContainer
import com.familydocs.app.ui.components.rememberGoogleConnect
import com.familydocs.app.ui.ds.BtnKind
import com.familydocs.app.ui.ds.Fd
import com.familydocs.app.ui.ds.FdBanner
import com.familydocs.app.ui.ds.FdButton
import com.familydocs.app.ui.ds.FdIcon
import com.familydocs.app.ui.ds.FdIconButton
import com.familydocs.app.ui.ds.FdInput
import com.familydocs.app.ui.ds.FdSegmented
import com.familydocs.app.ui.ds.FdSheet
import com.familydocs.app.ui.ds.LocalToast
import com.familydocs.app.ui.ds.SheetTitle
import com.familydocs.app.ui.ds.fdText
import kotlinx.coroutines.launch

/**
 * Returns a function that sends the invite for a group: connects Google if needed, puts your encrypted share in Drive,
 * then opens the share sheet (WhatsApp, Messages…) with the invite message.
 */
@Composable
fun rememberInviteSender(onBusy: (Boolean) -> Unit = {}): (String) -> Unit {
    val app = LocalContainer.current
    val context = LocalContext.current
    val toast = LocalToast.current
    val scope = rememberCoroutineScope()
    var target by remember { mutableStateOf<String?>(null) }
    val connect = rememberGoogleConnect { r ->
        val g = target ?: return@rememberGoogleConnect
        r.onSuccess { s ->
            scope.launch {
                onBusy(true)
                runCatching { app.share.invite(s.token, g) }
                    .onSuccess { inv ->
                        app.appLock.expectExternalActivity()
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, inv.message()), "Send the invite"))
                    }
                    .onFailure { toast.show("Couldn't prepare the invite: ${it.message}") }
                onBusy(false)
            }
        }.onFailure { if (it !is GoogleAccountHelper.Cancelled) toast.show(GoogleAccountHelper.explain(it)) }
    }
    return { g -> target = g; connect() }
}

/**
 * Group settings: name and code, what this phone backs up to Drive for the group, and sharing with the other members'
 * accounts (invite, add the invite they send back, who you read, sync).
 * [intro]: "created" or "joined" adds a short explanation at the top (shown right after making or joining a group).
 */
@Composable
fun GroupSettingsSheet(groupId: String, intro: String? = null, joinedFrom: String? = null, onDismiss: () -> Unit) {
    val app = LocalContainer.current
    val c = Fd.c
    val toast = LocalToast.current
    val scope = rememberCoroutineScope()
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val group by app.repo.group(groupId).collectAsStateWithLifecycle(null)
    val g = group ?: return
    var busy by remember { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(false) }
    var pasted by remember { mutableStateOf("") }
    var pasteError by remember { mutableStateOf<String?>(null) }
    var forgetting by remember { mutableStateOf<String?>(null) }
    val sendInvite = rememberInviteSender { busy = if (it) "Preparing the invite…" else null }
    val sync = rememberGoogleConnect { r ->
        r.onSuccess { s ->
            scope.launch {
                busy = "Syncing…"
                runCatching { app.share.sync(s.token, g.id) { msg -> busy = msg } }.onSuccess { toast.show(it) }.onFailure { toast.show("Couldn't sync: ${it.message}") }
                busy = null
            }
        }.onFailure { if (it !is GoogleAccountHelper.Cancelled) toast.show(GoogleAccountHelper.explain(it)) }
    }

    FdSheet(onDismiss) {
        Column(Modifier.heightIn(max = 640.dp).verticalScroll(rememberScrollState())) {
            SheetTitle(g.name, null)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 14.dp)) {
                Text("Code ${g.code}", style = fdText(13f, color = c.fg.copy(alpha = .55f), spacing = .06f))
                FdIconButton("copy", { clipboard.setText(androidx.compose.ui.text.AnnotatedString(g.code)); toast.show("Code copied") }, size = 30.dp, iconSize = 15.dp,
                    tint = c.muted, description = "Copy code")
            }
            when (intro) {
                "created" -> FdBanner("info", "Backups: by default your Drive keeps you and this group's Common vault; you can switch to the whole group below, any time. " +
                    "To share the group with someone, tap Invite: your phone puts an encrypted copy in your Google Drive (allow Drive access when Google asks) and each person who joins does the same.",
                    Modifier.padding(bottom = 14.dp))
                "joined" -> FdBanner("people", "You joined ${g.name}. Send your invite back to ${joinedFrom ?: "the person who invited you"} so they can see your documents too " +
                    "(Google asks you to allow Drive access the first time).", Modifier.padding(bottom = 14.dp))
            }

            // ---- backup ----
            Label("Back up to your Google Drive")
            FdSegmented(listOf(Triple(false, "user", "Me + Common"), Triple(true, "people", "Whole group")), g.backupAll) { all ->
                scope.launch { app.repo.setGroupBackup(g.id, all) }
            }
            Text(if (g.backupAll) "Everyone in ${g.name} that's on this phone, with their documents. Logins: only yours and the Common ones, never other people's."
                 else "Your documents and the Common vault (with its logins). The others back up their own from their phones.",
                style = fdText(12.5f, color = c.muted), modifier = Modifier.padding(top = 8.dp, bottom = 18.dp))

            // ---- sharing ----
            Label("Shared with")
            val peers = g.peerList
            if (peers.isEmpty()) Text("No one yet. Invite the others: they paste your invite in Join group, then send theirs back.",
                style = fdText(13f, color = c.muted), modifier = Modifier.padding(bottom = 10.dp))
            else Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).border(1.dp, c.line, RoundedCornerShape(16.dp)).padding(vertical = 2.dp)) {
                peers.forEach { p ->
                    Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(p.name.ifBlank { "Group member" }, style = fdText(14.5f, FontWeight.Medium))
                            Text(if (p.at == 0L) "Not read yet" else "Updated " + android.text.format.DateUtils.getRelativeTimeSpanString(p.at), style = fdText(12f, color = c.muted))
                        }
                        FdIconButton("close", { forgetting = p.key }, size = 36.dp, iconSize = 16.dp, tint = c.muted, description = "Stop reading ${p.name}'s share")
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            FdButton(if (intro == "joined") "Send my invite back" else "Invite someone", { sendInvite(g.id) }, icon = "people", enabled = busy == null)
            Spacer(Modifier.height(10.dp))
            if (!adding) FdButton("Add an invite someone sent back", { adding = true }, kind = BtnKind.Ghost, icon = "plus")
            else {
                FdInput(pasted, { pasted = it; pasteError = null }, placeholder = "Paste their message (FD1.…)", singleLine = false, error = pasteError != null)
                pasteError?.let { Text(it, style = fdText(12.5f, color = c.danger), modifier = Modifier.padding(top = 6.dp)) }
                FdButton("Add", {
                    val inv = GroupInvite.find(pasted)
                    when {
                        inv == null -> pasteError = "That isn't a Family Docs invite. Paste the whole message they sent."
                        inv.groupId != g.id -> pasteError = "That invite is for another group (${inv.name}). Use Join group for it."
                        else -> scope.launch {
                            busy = "Reading ${inv.fromName}'s share…"
                            runCatching { app.share.accept(null, inv) }
                                .onSuccess { (_, problem) -> adding = false; pasted = ""; toast.show(problem?.let { "Added ${inv.fromName}. $it" } ?: "Added ${inv.fromName}") }
                                .onFailure { pasteError = it.message }
                            busy = null
                        }
                    }
                }, Modifier.padding(top = 10.dp), enabled = pasted.isNotBlank() && busy == null)
            }
            Spacer(Modifier.height(10.dp))
            if (peers.isNotEmpty() || g.shareIndex != null) FdButton("Sync now", sync, kind = BtnKind.Ghost, icon = "refresh", enabled = busy == null)
            busy?.let { Text(it, style = fdText(12.5f, color = c.muted), modifier = Modifier.padding(top = 10.dp)) }
            Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FdIcon("lock", 14.dp, tint = c.muted)
                Text("Each phone shares an encrypted copy from its own Drive. The Drive link alone is unreadable: it needs the group code, which travels only " +
                    "inside the invite, so send invites privately. Things shared with you are read-only here; logins shared are only the Common ones.",
                    style = fdText(11.5f, color = c.muted))
            }
            Spacer(Modifier.height(8.dp))
        }
    }
    forgetting?.let { key ->
        val name = g.peerList.firstOrNull { it.key == key }?.name ?: "them"
        com.familydocs.app.ui.ds.FdConfirmSheet("Stop reading $name's share?", "What $name shared is removed from this phone. Nothing in their Drive changes; you can add their invite again later.",
            "Remove", onConfirm = { scope.launch { app.share.forget(g.id, key); forgetting = null; toast.show("Removed") } }, onDismiss = { forgetting = null })
    }
}

@Composable
private fun Label(text: String) {
    Text(text.uppercase(), style = fdText(11f, FontWeight.SemiBold, Fd.c.muted, spacing = .08f), modifier = Modifier.padding(bottom = 8.dp))
}
