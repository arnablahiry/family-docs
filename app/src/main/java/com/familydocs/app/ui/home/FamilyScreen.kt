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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
fun FamilyScreen(onMember: (String) -> Unit, onSettings: () -> Unit, onDocument: (String) -> Unit, onEvent: (String) -> Unit, onNewEvent: () -> Unit) {
    val app = LocalContainer.current
    val c = Fd.c
    val groups by app.repo.groups.collectAsStateWithLifecycle(emptyList())
    val gid by app.repo.groupId.collectAsStateWithLifecycle()
    // Once per phone: if you're one of the preset family, the other two appear with the right relation and picture.
    LaunchedEffect(Unit) {
        if (!app.settings.value.familyPresetDone) { app.repo.ensureFamily(); app.settings.update { it.copy(familyPresetDone = true) } }
    }
    var sheet by remember { mutableStateOf<String?>(null) } // "create" | "join"
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
        // Switching groups: the old page fades back a touch, the new one rises in and settles.
        AnimatedContent(gid, transitionSpec = {
            (fadeIn(tween(280, delayMillis = 90)) + slideInVertically(tween(380, easing = FdEase)) { it / 16 } + scaleIn(tween(380, easing = FdEase), initialScale = .97f)) togetherWith
                (fadeOut(tween(150)) + scaleOut(tween(200), targetScale = .985f))
        }, label = "groupSwitch") { g -> GroupHome(g, onMember, onSettings, onDocument, onEvent, onNewEvent) }
    }
    when (sheet) {
        "create" -> GroupSheet("New group", "Its own people, Common vault, events and code. You're in every group.", "Group name, e.g. Flatmates", "Create group",
            onDismiss = { sheet = null }) { name -> val g = app.repo.createGroup(name); app.selectGroup(g.id); app.settings.update { it.copy(groupIntroDone = true) }; settingsFor = Triple(g.id, "created", null); null }
        "join" -> GroupSheet("Join a group", "Paste the invite someone in the group sent you. (A code alone opens groups that are already on this phone.)",
            "Invite (FD1.…) or group code", "Join", onDismiss = { sheet = null }, multiline = true) { text ->
            val inv = com.familydocs.app.domain.GroupInvite.find(text)
            if (inv != null) {
                runCatching { app.share.accept(null, inv) }.fold({ (g, problem) ->
                    app.selectGroup(g.id); settingsFor = Triple(g.id, "joined", inv.fromName)
                    problem?.let { com.familydocs.app.backup.BackupWorker.syncShares(app.appContext) }
                    null
                }, { it.message ?: "Couldn't use that invite" })
            } else {
                val g = app.repo.groupByCode(text)
                if (g != null) { app.selectGroup(g.id); null }
                else "No group with this code on this phone. To join someone else's group, ask them for an invite (Group settings › Invite someone) and paste the whole message here."
            }
        }
    }
    settingsFor?.let { (id, intro, from) -> GroupSettingsSheet(id, intro, from) { settingsFor = null } }
}

/** One group's home: its people, search and events (keyed by group, so the switch animation shows the right page). */
@Composable
private fun GroupHome(g: String?, onMember: (String) -> Unit, onSettings: () -> Unit, onDocument: (String) -> Unit, onEvent: (String) -> Unit, onNewEvent: () -> Unit) {
    val app = LocalContainer.current
    val c = Fd.c
    val account by app.repo.account.collectAsStateWithLifecycle(null)
    val members by remember(g) { app.repo.membersOf(g) }.collectAsStateWithLifecycle(emptyList())
    val docs by remember(g) { app.repo.documentsOf(g) }.collectAsStateWithLifecycle(emptyList())
    val counts by app.repo.documentCounts.collectAsStateWithLifecycle(emptyList())
    val allEvents by remember(g) { app.repo.eventsOf(g) }.collectAsStateWithLifecycle(emptyList())
    var query by rememberSaveable { mutableStateOf("") }
    val self = members.firstOrNull { it.isSelf }
    val events = allEvents.filter { it.visibleTo(self?.id) && Events.isCurrent(it.startDay, it.endDay) }
    val full = GridItemSpan(2)

    LazyVerticalGrid(
        GridCells.Fixed(2), Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 36.dp), horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(span = { full }) {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp)) {
                Text("${greeting()}, ${account?.name?.substringBefore(' ').orEmpty()}", style = fdText(14f, color = c.muted))
                FdInput(query, { query = it }, Modifier.padding(top = 14.dp), placeholder = "Search documents, numbers…", icon = "search")
                // A newer version on GitHub: a quiet hint; Settings › About downloads and installs it.
                val update by app.updater.state.collectAsStateWithLifecycle()
                (update as? com.familydocs.app.update.AppUpdater.State.Available)?.let { a ->
                    FdBanner("cloud", "Family Docs ${a.release.version} is available", Modifier.padding(top = 12.dp).clickable(onClick = onSettings)) {
                        Text("Update", style = fdText(12.5f, FontWeight.SemiBold, c.primary))
                    }
                }
            }
        }

        if (query.isNotBlank()) {
            val hits = search(query, members, docs)
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
        }, iconSize = 21.dp, description = if (dark) "Switch to light mode" else "Switch to dark mode",
            modifier = Modifier.graphicsLayer { val v = spin.value; rotationZ = -120f * v; scaleX = 1f - .6f * v; scaleY = 1f - .6f * v; alpha = 1f - v })
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
 * Home header: the group's name (always, instead of "Family Docs"), its code underneath (fainter) and a ▾ to switch
 * groups (or ＋ to add one when there are none). The name and code roll to the new group; the menu unfolds below.
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
    val turn by animateFloatAsState(if (open) 180f else 0f, spring(dampingRatio = .6f, stiffness = 380f), label = "groupChevron")
    Column {
        Row(Modifier.padding(start = 14.dp, end = 20.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { if (groups.isEmpty()) onCreate() else open = !open }
                .padding(horizontal = 6.dp, vertical = 2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AnimatedContent(current?.name ?: "Family Docs", Modifier.weight(1f, fill = false), transitionSpec = {
                        (slideInVertically(tween(320, easing = FdEase)) { it / 2 } + fadeIn(tween(240, 60))) togetherWith (slideOutVertically(tween(260)) { -it / 2 } + fadeOut(tween(160)))
                    }, label = "groupName") { name -> Text(name, style = fdText(22f, FontWeight.Bold, spacing = .01f), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    if (groups.isEmpty()) FdIcon("plus", 18.dp, tint = c.muted, description = "Add a group")
                    else FdIcon("chev", 16.dp, tint = c.muted, description = "Switch group", modifier = Modifier.graphicsLayer { rotationZ = 90f + turn })
                }
                current?.let { cur ->
                    AnimatedContent(cur.code, transitionSpec = {
                        (slideInVertically(tween(320, 40, FdEase)) { it } + fadeIn(tween(240, 100))) togetherWith (slideOutVertically(tween(220)) { -it } + fadeOut(tween(140)))
                    }, label = "groupCode") { code -> Text(code, style = fdText(11.5f, color = c.fg.copy(alpha = .45f), spacing = .08f)) }
                }
            }
            ThemeButton()
            FdIconButton("lock", { app.appLock.lock() }, iconSize = 21.dp, description = "Lock app")
            Box(Modifier.clip(CircleShape).clickable(onClick = onSettings)) {
                FdAvatar(account?.name ?: "", 34.dp, photoFile = self?.photoFile ?: account?.photoFile, character = self?.avatar)
            }
        }
        AnimatedVisibility(open, enter = expandVertically(spring(dampingRatio = .82f, stiffness = 420f), expandFrom = Alignment.Top) + fadeIn(tween(200)),
            exit = shrinkVertically(tween(220, easing = FdEase), shrinkTowards = Alignment.Top) + fadeOut(tween(150))) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.surface)
                .border(1.dp, c.line, RoundedCornerShape(18.dp)).padding(vertical = 4.dp)) {
                groups.forEach { g ->
                    Row(Modifier.fillMaxWidth().clickable { open = false; if (g.id != gid) app.selectGroup(g.id) }.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(g.name, style = fdText(15f, if (g.id == gid) FontWeight.Bold else FontWeight.Medium))
                            Text(g.code, style = fdText(11.5f, color = c.fg.copy(alpha = .45f), spacing = .08f))
                        }
                        if (g.id == gid) {
                            FdIconButton("copy", { clipboard.setText(androidx.compose.ui.text.AnnotatedString(g.code)); toast.show("Code copied") }, size = 34.dp, iconSize = 17.dp,
                                tint = c.muted, description = "Copy code")
                            FdIcon("check", 18.dp, tint = c.accent)
                        }
                    }
                }
                Box(Modifier.padding(horizontal = 14.dp).fillMaxWidth().height(1.dp).background(c.line))
                gid?.let { cur ->
                    Row(Modifier.fillMaxWidth().clickable { open = false; onGroupSettings(cur) }.padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) { FdIcon("sliders", 18.dp, tint = c.muted); Text("Group settings · backup & sharing", style = fdText(14f)) }
                }
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
