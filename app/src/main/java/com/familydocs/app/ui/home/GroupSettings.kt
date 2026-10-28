package com.familydocs.app.ui.home

import android.content.Intent
import androidx.compose.foundation.background
import com.familydocs.app.ui.ds.dashedBorder
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
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
    var croppingFor by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    val sendInvite = rememberInviteSender { busy = if (it) "Preparing the invite…" else null }
    // Requests to join with the code (when the app was built with the request server)
    val incoming by app.requests.incoming.collectAsStateWithLifecycle()
    val waiting = incoming[g.id].orEmpty()
    var approving by remember { mutableStateOf<com.familydocs.app.backup.JoinRequests.Incoming?>(null) }
    androidx.compose.runtime.LaunchedEffect(g.id) { if (app.requests.enabled) runCatching { app.requests.checkGroups() } }
    val approve = rememberGoogleConnect { r ->
        val req = approving ?: return@rememberGoogleConnect
        r.onSuccess { s ->
            scope.launch {
                busy = "Letting ${req.name} in…"
                runCatching { app.requests.approve(s.token, g, req) }
                    .onSuccess { toast.show("${req.name} can join now. Their documents appear once their phone confirms.") }
                    .onFailure { toast.show("Couldn't approve: ${it.message}") }
                busy = null; approving = null
            }
        }.onFailure { approving = null; if (it !is GoogleAccountHelper.Cancelled) toast.show(GoogleAccountHelper.explain(it)) }
    }
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
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(bottom = 14.dp)) {
                GroupPic(g, 64.dp)
                Column {
                    Text(g.name, style = fdText(18f, FontWeight.Bold))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Code ${g.code}", style = fdText(13f, color = c.fg.copy(alpha = .55f), spacing = .06f))
                        FdIconButton("copy", { clipboard.setText(androidx.compose.ui.text.AnnotatedString(g.code)); toast.show("Code copied") }, size = 30.dp, iconSize = 15.dp,
                            tint = c.muted, description = "Copy code")
                    }
                }
            }
            // ---- name and picture ----
            Label("Name and picture")
            var newName by remember(g.id) { mutableStateOf(g.name) }
            FdInput(newName, { newName = it }, placeholder = "Group name", trailing = if (newName.trim() != g.name && newName.isNotBlank()) {
                { com.familydocs.app.ui.ds.FdTextButton("Save", { scope.launch { app.repo.renameGroup(g.id, newName); toast.show("Saved") } }) }
            } else null)
            Spacer(Modifier.height(10.dp))
            val context = LocalContext.current
            val ownPhoto = com.familydocs.app.ui.components.rememberVaultImage(g.photoFile, 300)
            GroupPicPicker(if (g.photoFile != null) "photo" else "default", ownPhoto,
                onTemplate = { k -> scope.launch { app.repo.setGroupPicture(g.id, k, null) } },
                onPhoto = { uri -> scope.launch { croppingFor = readPhoto(context, uri) } })
            Spacer(Modifier.height(18.dp))
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

            // ---- requests ----
            if (waiting.isNotEmpty()) {
                Label("Asking to join")
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).border(1.dp, c.line, RoundedCornerShape(16.dp)).padding(vertical = 2.dp)) {
                    waiting.forEach { r ->
                        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(r.name, style = fdText(14.5f, FontWeight.Medium))
                                Text(if (r.at > 0) "Asked " + android.text.format.DateUtils.getRelativeTimeSpanString(r.at) else "Asked with the group code", style = fdText(12f, color = c.muted))
                            }
                            com.familydocs.app.ui.ds.FdTextButton("Decline", { scope.launch { runCatching { app.requests.decline(g, r) }.onFailure { toast.show("Couldn't decline: ${it.message}") } } },
                                enabled = busy == null, color = c.muted)
                            com.familydocs.app.ui.ds.FdTextButton("Approve", { approving = r; approve() }, enabled = busy == null)
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
            }

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
                        inv == null -> pasteError = "That isn't a Lararium invite. Paste the whole message they sent."
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
    croppingFor?.let { bmp -> GroupPhotoCropSheet(bmp, onDone = { bytes, _ -> scope.launch { app.repo.setGroupPicture(g.id, null, bytes) }; croppingFor = null }, onDismiss = { croppingFor = null }) }
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


/**
 * Join a group: paste an invite, or type a code. A code for a group that isn't on this phone can ask the group's members
 * for access (when the app was built with the request server); they approve in their app and you join automatically.
 */
@Composable
fun JoinGroupSheet(onJoined: (groupId: String, invitedBy: String?) -> Unit, onDismiss: () -> Unit) {
    val app = LocalContainer.current
    val c = Fd.c
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var found by remember { mutableStateOf<String?>(null) }   // name of the group the code belongs to
    var sent by remember { mutableStateOf(false) }
    FdSheet(onDismiss) {
        SheetTitle("Join a group", if (app.requests.enabled) "Paste the invite someone sent you, or enter the group's code to ask its members for access."
            else "Paste the invite someone in the group sent you. (A code alone opens groups that are already on this phone.)")
        if (sent) {
            FdBanner("check", "Request sent to ${found}. Everyone in it gets a notification; as soon as someone approves, you join automatically and get a notification too.")
            Spacer(Modifier.height(14.dp))
            FdButton("Done", onDismiss)
            Spacer(Modifier.height(12.dp))
            return@FdSheet
        }
        FdInput(text, { text = it; error = null; found = null }, placeholder = "Invite (FD1.…) or group code, e.g. K7#-Q2@-XM4", singleLine = false, error = error != null)
        error?.let { Text(it, style = fdText(12.5f, color = c.danger), modifier = Modifier.padding(top = 8.dp)) }
        val f = found
        if (f != null) {
            Row(Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).border(1.dp, c.line, RoundedCornerShape(16.dp)).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FdIcon("people", 20.dp, tint = c.accent)
                Column(Modifier.weight(1f)) {
                    Text(f, style = fdText(15f, FontWeight.SemiBold))
                    Text("Its members decide whether to let you in", style = fdText(12f, color = c.muted))
                }
            }
            FdButton(if (busy) "Sending…" else "Request access", {
                scope.launch {
                    busy = true
                    runCatching { app.requests.request(text, f, app.repo.accountNow()?.name ?: "Someone") }
                        .onSuccess { sent = true; com.familydocs.app.backup.BackupWorker.scheduleChecks(app.appContext) }
                        .onFailure { error = "Couldn't send the request: ${it.message}" }
                    busy = false
                }
            }, Modifier.padding(top = 12.dp), enabled = !busy)
        } else FdButton(if (busy) "One moment…" else "Join", {
            scope.launch {
                busy = true
                val inv = GroupInvite.find(text)
                if (inv != null) {
                    runCatching { app.share.accept(null, inv) }.onSuccess { (g, problem) ->
                        if (problem != null) com.familydocs.app.backup.BackupWorker.syncShares(app.appContext)
                        onJoined(g.id, inv.fromName); onDismiss()
                    }.onFailure { error = it.message ?: "Couldn't use that invite" }
                } else {
                    val local = app.repo.groupByCode(text)
                    when {
                        local != null -> { onJoined(local.id, null); onDismiss() }
                        app.requests.enabled -> runCatching { app.requests.find(text) }
                            .onSuccess { name -> if (name != null) found = name else error = "No group with this code. Check it with someone in the group, or ask them for an invite." }
                            .onFailure { error = "Couldn't look up the code: ${it.message}" }
                        else -> error = "No group with this code on this phone. To join someone else's group, ask them for an invite (Group settings › Invite someone) and paste the whole message here."
                    }
                }
                busy = false
            }
        }, Modifier.padding(top = 14.dp), enabled = text.isNotBlank() && !busy)
        Spacer(Modifier.height(12.dp))
    }
}


/** The group's picture: your own photo if set, else its template illustration; round. */
@Composable
fun GroupPic(g: com.familydocs.app.data.GroupEntity, size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier, description: String? = null) {
    val photo = com.familydocs.app.ui.components.rememberVaultImage(g.photoFile, 600)
    val m = modifier.size(size).clip(androidx.compose.foundation.shape.CircleShape)
    if (photo != null) androidx.compose.foundation.Image(photo, description, m, contentScale = androidx.compose.ui.layout.ContentScale.Crop)
    else androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(groupArt(g.pic)), description, m, contentScale = androidx.compose.ui.layout.ContentScale.Crop)
}

@Suppress("UNUSED_PARAMETER")
fun groupArt(pic: String?): Int = com.familydocs.app.R.drawable.art_group_default

/**
 * Picture chooser: the illustration, or a photo from the phone's gallery. [selected] is "default" or "photo";
 * [photo] is a preview of the chosen photo.
 */
@Composable
fun GroupPicPicker(selected: String, photo: androidx.compose.ui.graphics.ImageBitmap?, onTemplate: (String) -> Unit, onPhoto: (android.net.Uri) -> Unit) {
    val c = Fd.c
    val app = LocalContainer.current
    val pick = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(onPhoto) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        val item: @Composable (Boolean, String, () -> Unit, @Composable () -> Unit) -> Unit = { on, label, click, pic ->
            Column(Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = click).padding(2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(60.dp).border(2.dp, if (on) c.accent else androidx.compose.ui.graphics.Color.Transparent, androidx.compose.foundation.shape.CircleShape).padding(3.dp)) { pic() }
                Text(label, style = fdText(10.5f, if (on) FontWeight.SemiBold else FontWeight.Normal, if (on) c.fg else c.muted), maxLines = 1)
            }
        }
        item(selected != "photo", "Illustration", { onTemplate(com.familydocs.app.domain.GroupPics.DEFAULT) }) {
            androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(groupArt(null)), "Illustration", Modifier.fillMaxSize().clip(androidx.compose.foundation.shape.CircleShape),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop)
        }
        item(selected == "photo", if (photo != null) "Your photo" else "Add photo", {
            app.appLock.expectExternalActivity()
            pick.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly))
        }) {
            if (photo != null) androidx.compose.foundation.Image(photo, "Your photo", Modifier.fillMaxSize().clip(androidx.compose.foundation.shape.CircleShape),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop)
            else Box(Modifier.fillMaxSize().dashedBorder(c.line, radius = 22.dp), contentAlignment = Alignment.Center) { FdIcon("image", 18.dp, tint = c.muted) }
        }
    }
}

/** Reads a picked image for cropping. */
suspend fun readPhoto(context: android.content.Context, uri: android.net.Uri): android.graphics.Bitmap? =
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() }?.let { com.familydocs.app.ocr.Images.decode(it, 1600) } }.getOrNull()
    }

/** Position and zoom a picked photo in the circle (the same cropper as people's photos); [onDone] gets the saved JPEG. */
@Composable
fun GroupPhotoCropSheet(bitmap: android.graphics.Bitmap, onDone: (ByteArray, androidx.compose.ui.graphics.ImageBitmap) -> Unit, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val crop = remember(bitmap) { com.familydocs.app.ui.ds.CropState() }
    val box = com.familydocs.app.ui.ds.cropBoxPx()
    FdSheet(onDismiss) {
        SheetTitle("Group picture", "Drag and zoom so the circle shows what you want.")
        com.familydocs.app.ui.ds.FdPhotoCropper(bitmap, crop) {}
        FdButton("Use this photo", {
            scope.launch {
                val sq = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { com.familydocs.app.ui.ds.cropToSquare(bitmap, crop, box) }
                onDone(com.familydocs.app.ocr.Images.jpeg(sq), sq.asImageBitmap())
            }
        }, Modifier.padding(top = 14.dp))
        Spacer(Modifier.height(12.dp))
    }
}

/** New group: name and picture (the illustration, or your own photo). */
@Composable
fun NewGroupSheet(onCreated: (com.familydocs.app.data.GroupEntity) -> Unit, onDismiss: () -> Unit) {
    val app = LocalContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    val pic = com.familydocs.app.domain.GroupPics.DEFAULT
    var photo by remember { mutableStateOf<Pair<ByteArray, androidx.compose.ui.graphics.ImageBitmap>?>(null) }
    var cropping by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var busy by remember { mutableStateOf(false) }
    FdSheet(onDismiss) {
        SheetTitle("New group", "Its own people, Common vault, events and code. You're in every group.")
        FdInput(name, { name = it }, placeholder = "Group name, e.g. Flatmates")
        Label2("Picture")
        GroupPicPicker(if (photo != null) "photo" else pic, photo?.second, onTemplate = { photo = null },
            onPhoto = { uri -> scope.launch { cropping = readPhoto(context, uri) } })
        FdButton(if (busy) "Creating…" else "Create group", {
            scope.launch { busy = true; val g = app.repo.createGroup(name, pic, photo?.first); busy = false; onCreated(g); onDismiss() }
        }, Modifier.padding(top = 16.dp), enabled = name.isNotBlank() && !busy)
        Spacer(Modifier.height(12.dp))
    }
    cropping?.let { bmp -> GroupPhotoCropSheet(bmp, onDone = { bytes, img -> photo = bytes to img; cropping = null }, onDismiss = { cropping = null }) }
}

@Composable
private fun Label2(text: String) {
    Text(text.uppercase(), style = fdText(11f, FontWeight.SemiBold, Fd.c.muted, spacing = .08f), modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
}
