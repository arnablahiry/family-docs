package com.familydocs.app.ui.home

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
    val account by app.repo.account.collectAsStateWithLifecycle(null)
    val members by app.repo.members.collectAsStateWithLifecycle(emptyList())
    val docs by app.repo.allDocuments.collectAsStateWithLifecycle(emptyList())
    val counts by app.repo.documentCounts.collectAsStateWithLifecycle(emptyList())
    val allEvents by app.repo.events.collectAsStateWithLifecycle(emptyList())
    // Once per phone: if you're one of the preset family, the other two appear with the right relation and picture.
    LaunchedEffect(Unit) {
        if (!app.settings.value.familyPresetDone) { app.repo.ensureFamily(); app.settings.update { it.copy(familyPresetDone = true) } }
    }
    var query by rememberSaveable { mutableStateOf("") }
    val self = members.firstOrNull { it.isSelf }
    val events = allEvents.filter { it.visibleTo(self?.id) && Events.isCurrent(it.startDay, it.endDay) }
    val full = GridItemSpan(2)

    LazyVerticalGrid(
        GridCells.Fixed(2), Modifier.fillMaxSize().background(c.bg).statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 36.dp), horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // ---- header: wordmark, theme switch, lock, your picture ----
        item(span = { full }) {
            Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Family Docs", style = fdText(22f, FontWeight.Bold, spacing = .01f), modifier = Modifier.weight(1f))
                ThemeButton()
                FdIconButton("lock", { app.appLock.lock() }, iconSize = 21.dp, description = "Lock app")
                Box(Modifier.clip(CircleShape).clickable(onClick = onSettings)) {
                    FdAvatar(account?.name ?: "", 34.dp, photoFile = self?.photoFile ?: account?.photoFile, character = self?.avatar)
                }
            }
        }
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

