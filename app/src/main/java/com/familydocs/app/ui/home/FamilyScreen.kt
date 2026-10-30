package com.familydocs.app.ui.home

import com.familydocs.app.ui.ds.FdEase
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.border
import androidx.compose.animation.core.spring
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.expandVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.scaleOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.togetherWith
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.familydocs.app.data.DocumentEntity
import com.familydocs.app.data.MemberEntity
import com.familydocs.app.domain.maskValue
import com.familydocs.app.security.ThemeMode
import com.familydocs.app.ui.components.LocalContainer
import com.familydocs.app.ui.components.LocalThemeReveal
import com.familydocs.app.ui.ds.FdBanner
import com.familydocs.app.ui.ds.Fd
import com.familydocs.app.ui.ds.FdAvatar
import com.familydocs.app.ui.ds.FdDocIcon
import com.familydocs.app.ui.ds.FdEmpty
import com.familydocs.app.ui.ds.FdIcon
import com.familydocs.app.ui.ds.FdIconButton
import com.familydocs.app.ui.ds.FdInput
import com.familydocs.app.ui.ds.dashedBorder
import com.familydocs.app.ui.ds.fdText
import com.familydocs.app.domain.Events
import com.familydocs.app.ui.events.EventsStrip
import com.familydocs.app.ui.events.visibleTo
import kotlinx.coroutines.launch
import java.util.Calendar

@Composable
fun FamilyScreen(onMember: (String) -> Unit, onSettings: () -> Unit, onDocument: (String) -> Unit, onEvent: (String) -> Unit, onNewEvent: () -> Unit,
                 onUpdates: () -> Unit = onSettings) {
    val app = LocalContainer.current
    val c = Fd.c
    val groups by app.repo.groups.collectAsStateWithLifecycle(emptyList())
    val gid by app.repo.groupId.collectAsStateWithLifecycle()
    // People are the group's members with their own accounts (and Common). Once: the people this phone made up earlier
    // (the old preset family) go; any documents they had move to Common.
    LaunchedEffect(Unit) {
        if (!app.settings.value.peopleCleanupDone) { app.repo.foldLocalPeople(); app.settings.update { it.copy(peopleCleanupDone = true, familyPresetDone = true) } }
    }
    // Whenever Home comes back to the screen: publish and read the group's shares.
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    androidx.compose.runtime.DisposableEffect(lifecycle) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                com.familydocs.app.backup.BackupWorker.syncShares(app.appContext)
            }
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
    var sheet by remember { mutableStateOf<String?>(null) } // "create" | "join"
    // Opened from an invite link (or an invite shared to Lararium): the join sheet, filled in.
    var joinText by remember { mutableStateOf("") }
    val incoming by app.incomingInvite.collectAsStateWithLifecycle()
    LaunchedEffect(incoming) { incoming?.let { joinText = it; sheet = "join"; app.incomingInvite.value = null } }
    // Group settings (with a short explanation right after creating / joining): group id, intro, who invited you
    var settingsFor by remember { mutableStateOf<Triple<String, String?, String?>?>(null) }
    // First time on Home with a group you made at sign-up: explain backups and sharing once.
    LaunchedEffect(groups.isNotEmpty()) {
        val first = groups.firstOrNull() ?: return@LaunchedEffect
        if (!app.settings.value.groupIntroDone) {
            app.settings.update { it.copy(groupIntroDone = true) }
            if (first.peerList.isEmpty()) settingsFor = Triple(first.id, "created", null)
        }
    }
    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding()) {
        GroupHeader(groups, gid, onSettings, onCreate = { sheet = "create" }, onJoin = { sheet = "join" },
            onGroupSettings = { id -> settingsFor = Triple(id, null, null) })
        // Restored on this phone, but the logins are locked with the backup password: ask for it.
        val loginsWaiting by app.loginLock.waiting.collectAsStateWithLifecycle()
        var unlocking by remember { mutableStateOf(false) }
        if (loginsWaiting) FdBanner("lock", "Your logins are locked with your backup password", Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 6.dp)) {
            com.familydocs.app.ui.ds.FdTextButton("Unlock", { unlocking = true })
        }
        if (unlocking) com.familydocs.app.ui.settings.UnlockLoginsSheet { unlocking = false }
        // What a group admin changed in your things (or about you): stays until you close it.
        val notes by app.share.notices.collectAsStateWithLifecycle()
        notes.takeLast(3).reversed().forEach { n ->
            FdBanner(n.icon, n.text, Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 6.dp)) {
                FdIconButton("close", { app.share.dismissNotice(n.id) }, size = 32.dp, iconSize = 15.dp, description = "Dismiss")
            }
        }
        // Who isn't synced with you yet (both ways). No one has added you: send your invite. Otherwise their phones
        // pick you up on their next sync; the note can be closed until the list changes.
        val shown = groups.firstOrNull { it.id == gid }
        val waiting = shown?.peerList.orEmpty().filter { !it.synced }
        if (shown != null && waiting.isNotEmpty()) {
            val nobody = shown.peerList.all { it.sees == false }
            var hiddenFor by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
            val key = shown.id + waiting.joinToString { it.key }
            if (nobody || hiddenFor != key) {
                var sending by remember { mutableStateOf(false) }
                val sendBack = com.familydocs.app.ui.home.rememberInviteSender { sending = it }
                val who = com.familydocs.app.data.Peer.names(waiting.map { it.name.ifBlank { "Someone" } })
                FdBanner("people", if (nobody) "No one in ${shown.name} has added you yet. Send them your invite: one tap on it adds you."
                    else "$who ${if (waiting.size == 1) "isn't" else "aren't"} synced yet. ${if (waiting.size == 1) "Their phone picks" else "Their phones pick"} you up the next time they open Lararium.",
                    Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 6.dp), accent = nobody) {
                    if (nobody) com.familydocs.app.ui.ds.FdTextButton(if (sending) "…" else "Send", { sendBack(shown.id) }, enabled = !sending)
                    else FdIconButton("close", { hiddenFor = key }, size = 32.dp, iconSize = 15.dp, description = "Dismiss")
                }
            }
        }
        // First time after an update: what changed, until it's closed.
        val news by app.whatsNew.entries.collectAsStateWithLifecycle()
        if (news.isNotEmpty()) WhatsNewBox(news, Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 6.dp)) { app.whatsNew.dismiss() }
        // Switching groups: the old page fades back a touch, the new one rises in and settles.
        AnimatedContent(gid, transitionSpec = {
            (fadeIn(tween(280, delayMillis = 90)) + slideInVertically(tween(380, easing = FdEase)) { it / 16 } + scaleIn(tween(380, easing = FdEase), initialScale = .97f)) togetherWith
                (fadeOut(tween(150)) + scaleOut(tween(200), targetScale = .985f))
        }, label = "groupSwitch") { g -> GroupHome(g, onMember, onSettings, onDocument, onEvent, onNewEvent, onUpdates) }
    }
    when (sheet) {
        "create" -> NewGroupSheet(onCreated = { g -> app.selectGroup(g.id); app.settings.update { it.copy(groupIntroDone = true) }; settingsFor = Triple(g.id, "created", null) },
            onDismiss = { sheet = null })
        "join" -> androidx.compose.runtime.key(joinText) { JoinGroupSheet(onJoined = { id, from ->
            app.selectGroup(id)
            if (from != null) settingsFor = Triple(id, "joined", from)
        }, onDismiss = { sheet = null; joinText = "" }, initial = joinText) }
    }
    settingsFor?.let { (id, intro, from) -> GroupSettingsSheet(id, intro, from, onMember = onMember) { settingsFor = null } }
}

/** One group's home: its people, search and events (keyed by group, so the switch animation shows the right page). */
@Composable
private fun GroupHome(g: String?, onMember: (String) -> Unit, onSettings: () -> Unit, onDocument: (String) -> Unit, onEvent: (String) -> Unit, onNewEvent: () -> Unit,
                      onUpdates: () -> Unit) {
    val app = LocalContainer.current
    val c = Fd.c
    val account by app.repo.account.collectAsStateWithLifecycle(null)
    val members by remember(g) { app.repo.membersOf(g) }.collectAsStateWithLifecycle(emptyList())
    val docs by remember(g) { app.repo.documentsOf(g) }.collectAsStateWithLifecycle(emptyList())
    val counts by app.repo.documentCounts.collectAsStateWithLifecycle(emptyList())
    val allEvents by remember(g) { app.repo.eventsOf(g) }.collectAsStateWithLifecycle(emptyList())
    // Card art, flags and photos for this group decoded in the background, so every vault opens smoothly.
    LaunchedEffect(docs, members) { if (docs.isNotEmpty() || members.isNotEmpty()) com.familydocs.app.ui.components.warmPictures(app, docs, members) }
    var query by rememberSaveable { mutableStateOf("") }
    // Searching: a back arrow in the box (and the back gesture) clears the search and returns to the home page.
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    val searching = query.isNotBlank()
    fun endSearch() { query = ""; focus.clearFocus() }
    androidx.activity.compose.BackHandler(enabled = searching) { endSearch() }
    val self = members.firstOrNull { it.isSelf }
    val events = allEvents.filter { it.visibleTo(self?.id) && Events.isCurrent(it.startDay, it.endDay) }
    val full = GridItemSpan(2)
    // worked out once per query (not on every recomposition while something animates)
    val hits = remember(query, members, docs) { if (query.isBlank()) emptyList() else search(query, members, docs) }

    LazyVerticalGrid(
        GridCells.Fixed(2), Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 36.dp), horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(span = { full }) {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp)) {
                Text("${greeting()}, ${account?.name?.substringBefore(' ').orEmpty()}", style = fdText(14f, color = c.muted))
                FdInput(query, { query = it }, Modifier.padding(top = 14.dp), placeholder = "Search documents, numbers…", icon = if (searching) null else "search",
                    leading = if (searching) { { FdIconButton("back", ::endSearch, size = 30.dp, iconSize = 20.dp, description = "Back to home") } } else null)
                // A newer version on GitHub: a quiet hint that opens Settings at the update; while it downloads (it keeps
                // going if you come back here), the same box shows the progress.
                val update by app.updater.state.collectAsStateWithLifecycle()
                when (val u = update) {
                    is com.familydocs.app.update.AppUpdater.State.Available ->
                        FdBanner("cloud", "Lararium ${u.release.version} is available", Modifier.padding(top = 12.dp).clickable(onClick = onUpdates)) {
                            Text("Update", style = fdText(12.5f, FontWeight.SemiBold, c.primary))
                        }
                    is com.familydocs.app.update.AppUpdater.State.Downloading -> {
                        val shown by androidx.compose.animation.core.animateFloatAsState(u.progress, tween(300), label = "updateProgress")
                        Column(Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.accentSoft).clickable(onClick = onUpdates)
                            .padding(horizontal = 12.dp, vertical = 10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                FdIcon("download", 18.dp)
                                Text("Downloading Lararium ${u.release.version}…", style = fdText(12.5f), modifier = Modifier.weight(1f))
                                Text("${(u.progress * 100).toInt()}%", style = fdText(12.5f, FontWeight.SemiBold))
                            }
                            Box(Modifier.padding(top = 8.dp).fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(c.surface)) {
                                Box(Modifier.fillMaxWidth(shown.coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(c.accent))
                            }
                        }
                    }
                    is com.familydocs.app.update.AppUpdater.State.Failed ->
                        FdBanner("info", "The update didn't finish", Modifier.padding(top = 12.dp).clickable(onClick = onUpdates)) {
                            Text("Retry", style = fdText(12.5f, FontWeight.SemiBold, c.primary))
                        }
                    else -> {}
                }
            }
        }

        if (query.isNotBlank()) {
            if (hits.isEmpty()) item(span = { full }) { FdEmpty("Nothing matches “$query”.", Modifier.padding(top = 10.dp)) }
            else items(hits, span = { full }) { h ->
                Row(
                    Modifier.padding(horizontal = 20.dp, vertical = 3.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.surface)
                        .clickable { if (h.doc != null) onDocument(h.doc.id) else onMember(h.member.id) }.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (h.doc != null) FdDocIcon(h.doc) else FdAvatar(h.member.name, 46.dp, photoFile = h.member.photoFile, character = h.member.avatar, shared = h.member.shared)
                    Column(Modifier.weight(1f)) {
                        Text(h.doc?.name ?: h.member.name, style = fdText(14f, FontWeight.Bold))
                        Text(listOfNotNull(if (h.doc != null) h.member.name else h.member.relation, h.match).joinToString(" · "),
                            style = fdText(12f, color = c.muted), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        } else {
            item(span = { full }) { Box(Modifier.size(28.dp)) }
            items(members, key = { it.id }) { m ->
                val n = counts.firstOrNull { it.memberId == m.id }?.n ?: 0
                Person(m.name, "${if (m.shared) "Shared" else m.relation} · $n doc${if (n == 1) "" else "s"}", { onMember(m.id) },
                    Modifier.padding(start = if (members.indexOf(m) % 2 == 0) 24.dp else 0.dp, end = if (members.indexOf(m) % 2 == 1) 24.dp else 0.dp, bottom = 30.dp)) {
                    FdAvatar(m.name, 132.dp, photoFile = m.photoFile, character = m.avatar, shared = m.shared, lift = true)
                }
            }
            // The + is for events now; adding family members lives in Settings › Family.
            item(span = { full }) { EventsStrip(events, onEvent, onNewEvent) }
            item(span = { full }) { com.familydocs.app.ui.ds.FdMadeWith(Modifier.padding(top = 30.dp, bottom = 6.dp)) }
        }
    }
}

/** `.mem`: big circle, name, relation · count. Presses scale the picture like the demo's hover. */
@Composable
private fun Person(title: String, subtitle: String?, onClick: () -> Unit, modifier: Modifier, muted: Boolean = false, picture: @Composable () -> Unit) {
    val c = Fd.c
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 1.03f else 1f, tween(200), label = "mem")
    Column(modifier.fillMaxWidth().clickable(interactionSource = source, indication = null, onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.scale(s)) { picture() }
        Text(title, style = fdText(17f, if (muted) FontWeight.SemiBold else FontWeight.Bold, if (muted) c.muted else c.fg, lineHeight = 19f),
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp))
        if (subtitle != null) Text(subtitle, style = fdText(12.5f, color = c.muted), maxLines = 1, modifier = Modifier.padding(top = 3.dp))
    }
}

/**
 * Sun/moon next to the lock: flips light ⇄ dark with the circular reveal from this button,
 * and the new icon spins in (demo `.spun`: from -120° and 40% size).
 */
@Composable
private fun ThemeButton() {
    val app = LocalContainer.current
    val reveal = LocalThemeReveal.current
    val scope = rememberCoroutineScope()
    val dark = Fd.c.dark
    val spin = remember { Animatable(0f) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(dark) {
        if (first) { first = false; return@LaunchedEffect }
        spin.snapTo(1f); spin.animateTo(0f, tween(550, easing = CubicBezierEasing(.3f, .7f, .2f, 1f)))
    }
    Box(Modifier.onGloballyPositioned { reveal.origin = it.boundsInRoot().center }) {
        FdIconButton(if (dark) "sun" else "moon", {
            scope.launch { reveal.play { app.settings.update { it.copy(theme = if (dark) ThemeMode.LIGHT else ThemeMode.DARK) } } }
        }, iconSize = 19.dp, size = 40.dp, background = Fd.c.surface, description = if (dark) "Switch to light mode" else "Switch to dark mode",
            modifier = Modifier.border(1.dp, Fd.c.line, CircleShape).graphicsLayer { val v = spin.value; rotationZ = -120f * v; scaleX = 1f - .6f * v; scaleY = 1f - .6f * v; alpha = 1f - v })
    }
}

private class Hit(val member: MemberEntity, val doc: DocumentEntity?, val match: String?)

private fun search(q: String, members: List<MemberEntity>, docs: List<DocumentEntity>): List<Hit> {
    val needle = q.trim().lowercase(); val compact = needle.replace(" ", "")
    val out = mutableListOf<Hit>()
    members.forEach { m ->
        if (m.name.lowercase().contains(needle)) out += Hit(m, null, null)
        docs.filter { it.memberId == m.id }.forEach { d ->
            val f = d.fields.firstOrNull { it.value.lowercase().replace(" ", "").contains(compact) }
            if (d.name.lowercase().contains(needle) || f != null) out += Hit(m, d, f?.let { "${it.label}: ${it.value}" })
        }
    }
    return out.take(30)
}

private fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 0..4 -> "Up late"; in 5..11 -> "Good morning"; in 12..16 -> "Good afternoon"; else -> "Good evening"
}

/**
 * Home header: the app's name (Bitcount Grid Double) on the left; on the right the theme and lock buttons, the group's
 * round picture (tap it for the group menu, or ＋ when there's no group yet) and your own picture (Settings).
 * The menu unfolds below: a big group picture, its name and code, Group settings, your other groups, New group, Join.
 */
@Composable
private fun GroupHeader(groups: List<com.familydocs.app.data.GroupEntity>, gid: String?, onSettings: () -> Unit, onCreate: () -> Unit, onJoin: () -> Unit,
                        onGroupSettings: (String) -> Unit) {
    val app = LocalContainer.current
    val c = Fd.c
    val account by app.repo.account.collectAsStateWithLifecycle(null)
    val self by app.repo.self().collectAsStateWithLifecycle(null)
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val toast = com.familydocs.app.ui.ds.LocalToast.current
    var open by remember { mutableStateOf(false) }
    val current = groups.firstOrNull { it.id == gid }
    val ring by androidx.compose.animation.animateColorAsState(if (open) c.accent else c.accent.copy(alpha = .55f), tween(200), label = "groupRing")
    Column {
        Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // The name shrinks to whatever room the four buttons leave (narrow phones, large font settings), never cut off.
            androidx.compose.foundation.layout.BoxWithConstraints(Modifier.weight(1f)) {
                val measurer = androidx.compose.ui.text.rememberTextMeasurer()
                val base = fdText(27f, color = c.fg, spacing = .02f).copy(fontFamily = com.familydocs.app.ui.ds.Bitcount)
                val room = constraints.maxWidth
                val fitted = remember(room, base) {
                    var k = 1f
                    fun at(k: Float) = base.copy(fontSize = base.fontSize * k, letterSpacing = base.letterSpacing * k)
                    while (k > .5f && measurer.measure("Lararium", at(k), softWrap = false, maxLines = 1).size.width > room) k -= .04f
                    at(k)
                }
                Text("Lararium", style = fitted, maxLines = 1, softWrap = false)
            }
            ThemeButton()
            FdIconButton("lock", { app.appLock.lock() }, Modifier.border(1.dp, c.line, CircleShape), size = 40.dp, iconSize = 19.dp, background = c.surface, description = "Lock app")
            // the group's picture: rolls to the new group when you switch
            Box(Modifier.size(40.dp).clip(CircleShape).clickable { if (groups.isEmpty()) onCreate() else open = !open }, contentAlignment = Alignment.Center) {
                if (current == null) Box(Modifier.size(38.dp).dashedBorder(c.line, radius = 19.dp), contentAlignment = Alignment.Center) { FdIcon("plus", 18.dp, tint = c.muted, description = "Add a group") }
                else AnimatedContent(current.id, transitionSpec = {
                    (scaleIn(spring(dampingRatio = .55f, stiffness = 420f), initialScale = .6f) + fadeIn(tween(160))) togetherWith (scaleOut(tween(140), targetScale = .7f) + fadeOut(tween(120)))
                }, label = "groupPic") { _ -> GroupPic(current, 35.dp, Modifier.border(1.5.dp, ring, CircleShape).padding(2.5.dp), description = "Group: ${current.name}") }
            }
            Box(Modifier.clip(CircleShape).clickable(onClick = onSettings)) {
                FdAvatar(account?.name ?: "", 40.dp, photoFile = self?.photoFile ?: account?.photoFile, character = self?.avatar)
            }
        }
        AnimatedVisibility(open, enter = expandVertically(spring(dampingRatio = .82f, stiffness = 420f), expandFrom = Alignment.Top) + fadeIn(tween(200)),
            exit = shrinkVertically(tween(220, easing = FdEase), shrinkTowards = Alignment.Top) + fadeOut(tween(150))) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.surface)
                .border(1.dp, c.line, RoundedCornerShape(18.dp)).padding(vertical = 4.dp)) {
                current?.let { g ->
                    Column(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        val pop = remember { Animatable(.7f) }
                        LaunchedEffect(g.id) { pop.snapTo(.7f); pop.animateTo(1f, spring(dampingRatio = .55f, stiffness = 380f)) }
                        GroupPic(g, 92.dp, Modifier.scale(pop.value))
                        Text(g.name, style = fdText(20f, FontWeight.Bold), modifier = Modifier.padding(top = 10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(g.code, style = fdText(12.5f, color = c.fg.copy(alpha = .5f), spacing = .08f))
                            FdIconButton("copy", { clipboard.setText(androidx.compose.ui.text.AnnotatedString(g.code)); toast.show("Code copied") }, size = 30.dp, iconSize = 14.dp,
                                tint = c.muted, description = "Copy code")
                        }
                    }
                    Row(Modifier.fillMaxWidth().clickable { open = false; onGroupSettings(g.id) }.padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FdIcon("sliders", 18.dp, tint = c.muted); Text("Group settings", style = fdText(14f), modifier = Modifier.weight(1f))
                        Text("name, picture, backup, sharing", style = fdText(11.5f, color = c.muted))
                    }
                }
                val others = groups.filter { it.id != gid }
                if (others.isNotEmpty()) {
                    Box(Modifier.padding(horizontal = 14.dp).fillMaxWidth().height(1.dp).background(c.line))
                    others.forEach { g ->
                        Row(Modifier.fillMaxWidth().clickable { open = false; app.selectGroup(g.id) }.padding(horizontal = 14.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            GroupPic(g, 30.dp)
                            Column(Modifier.weight(1f)) {
                                Text(g.name, style = fdText(15f, FontWeight.Medium))
                                Text(g.code, style = fdText(11.5f, color = c.fg.copy(alpha = .45f), spacing = .08f))
                            }
                        }
                    }
                }
                Box(Modifier.padding(horizontal = 14.dp).fillMaxWidth().height(1.dp).background(c.line))
                Row(Modifier.fillMaxWidth().clickable { open = false; onCreate() }.padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) { FdIcon("plus", 18.dp, tint = c.muted); Text("New group", style = fdText(14f)) }
                Row(Modifier.fillMaxWidth().clickable { open = false; onJoin() }.padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) { FdIcon("key", 18.dp, tint = c.muted); Text("Join a group", style = fdText(14f)) }
            }
        }
    }
}

/** New group / join with code: one field, one button; [onSubmit] returns an error to show, or null when done. */
@Composable
private fun GroupSheet(title: String, text: String, placeholder: String, action: String, onDismiss: () -> Unit, multiline: Boolean = false, onSubmit: suspend (String) -> String?) {
    val scope = rememberCoroutineScope()
    var value by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    com.familydocs.app.ui.ds.FdSheet(onDismiss) {
        com.familydocs.app.ui.ds.SheetTitle(title, text)
        FdInput(value, { value = it; error = null }, placeholder = placeholder, singleLine = !multiline)
        error?.let { Text(it, style = fdText(12.5f, color = Fd.c.danger), modifier = Modifier.padding(top = 8.dp)) }
        var busy by remember { mutableStateOf(false) }
        com.familydocs.app.ui.ds.FdButton(if (busy) "One moment…" else action, { scope.launch { busy = true; error = onSubmit(value); busy = false; if (error == null) onDismiss() } },
            Modifier.padding(top = 14.dp), enabled = value.isNotBlank() && !busy)
        Spacer(Modifier.height(12.dp))
    }
}

/**
 * "What's new in 0.8.6": the changes since the version you had, with ✕. Skipped versions are included: the newest
 * shows open, the earlier ones fold out under "Also new since you last updated".
 */
@Composable
private fun WhatsNewBox(entries: List<com.familydocs.app.domain.Changelog.Entry>, modifier: Modifier = Modifier, onClose: () -> Unit) {
    val c = Fd.c
    var all by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.accentSoft).animateContentSize(tween(300, easing = com.familydocs.app.ui.ds.FdEase))
        .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FdIcon("sparkle", 18.dp)
            Text("What's new in ${entries.first().version}", style = fdText(13.5f, FontWeight.SemiBold), modifier = Modifier.weight(1f))
            FdIconButton("close", onClose, size = 32.dp, iconSize = 15.dp, description = "Close")
        }
        Column(Modifier.padding(start = 28.dp, end = 10.dp)) {
            val shown = if (all) entries else entries.take(1)
            shown.forEach { e ->
                if (shown.size > 1) Text(e.version, style = fdText(11.5f, FontWeight.SemiBold, c.muted), modifier = Modifier.padding(top = 6.dp))
                e.notes.forEach { n ->
                    Row(Modifier.padding(top = 4.dp)) {
                        Text("•", style = fdText(12.5f, color = c.accent), modifier = Modifier.width(12.dp))
                        Text(n, style = fdText(12.5f), modifier = Modifier.weight(1f))
                    }
                }
            }
            if (entries.size > 1) Text(if (all) "Show less" else "Also new since you last updated: ${entries.drop(1).joinToString(", ") { it.version }} ▾",
                style = fdText(12f, FontWeight.SemiBold, c.primary), modifier = Modifier.padding(top = 8.dp).clickable { all = !all })
        }
    }
}
