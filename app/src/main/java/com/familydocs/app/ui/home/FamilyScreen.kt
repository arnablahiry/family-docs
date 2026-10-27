package com.familydocs.app.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Lock
import com.familydocs.app.security.ThemeMode
import com.familydocs.app.ui.theme.LocalExtra
import com.familydocs.app.ui.components.LocalThemeReveal
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import kotlinx.coroutines.launch
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.familydocs.app.data.DocumentEntity
import com.familydocs.app.data.MemberEntity
import com.familydocs.app.domain.maskValue
import com.familydocs.app.ui.components.Avatar
import com.familydocs.app.ui.components.DocumentIcon
import com.familydocs.app.ui.components.LocalContainer
import com.familydocs.app.ui.theme.BrandFont
import java.util.Calendar

@Composable
fun FamilyScreen(onMember: (String) -> Unit, onAddMember: () -> Unit, onSettings: () -> Unit, onDocument: (String) -> Unit) {
    val c = LocalContainer.current
    val account by c.repo.account.collectAsStateWithLifecycle(null)
    val members by c.repo.members.collectAsStateWithLifecycle(emptyList())
    val docs by c.repo.allDocuments.collectAsStateWithLifecycle(emptyList())
    val counts by c.repo.documentCounts.collectAsStateWithLifecycle(emptyList())
    var query by rememberSaveable { mutableStateOf("") }
    val full = GridItemSpan(2)

    LazyVerticalGrid(
        GridCells.Fixed(2),
        Modifier.fillMaxSize().safeDrawingPadding(),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(30.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(span = { full }) {
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Family Docs", fontFamily = BrandFont, fontWeight = FontWeight.Bold, fontSize = 22.sp, modifier = Modifier.weight(1f))
                // Theme switch: flips between light and dark (starts from whatever is showing now).
                val dark = LocalExtra.current.dark
                val reveal = LocalThemeReveal.current
                val scope = rememberCoroutineScope()
                IconButton(
                    onClick = {
                        // The new theme is revealed in a circle growing from this button.
                        scope.launch {
                            reveal.play { c.settings.update { it.copy(theme = if (dark) ThemeMode.LIGHT else ThemeMode.DARK) } }
                        }
                    },
                    modifier = Modifier.onGloballyPositioned { reveal.origin = it.boundsInRoot().center },
                ) {
                    // Sun/moon spins and scales in when the theme flips.
                    AnimatedContent(
                        dark, label = "themeIcon",
                        transitionSpec = {
                            (fadeIn(tween(300)) + scaleIn(tween(450), initialScale = .4f) + slideInVertically(tween(450)) { it / 2 }) togetherWith
                                (fadeOut(tween(200)) + scaleOut(tween(250), targetScale = .4f))
                        },
                    ) { isDark ->
                        Icon(if (isDark) Icons.Outlined.LightMode else Icons.Outlined.DarkMode, if (isDark) "Switch to light mode" else "Switch to dark mode")
                    }
                }
                IconButton({ c.appLock.lock() }) { Icon(Icons.Outlined.Lock, "Lock app") }
                Box(Modifier.clip(CircleShape).clickable(onClick = onSettings)) { Avatar(account?.name ?: "", account?.photoFile, 36.dp) }
            }
        }
        item(span = { full }) {
            Column {
                Text("${greeting()}, ${account?.name?.substringBefore(' ').orEmpty()}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                OutlinedTextField(
                    query, { query = it }, Modifier.fillMaxWidth().padding(top = 12.dp),
                    placeholder = { Text("Search documents, numbers…") }, leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    singleLine = true, shape = RoundedCornerShape(14.dp),
                )
            }
        }
        if (query.isNotBlank()) {
            val hits = search(query, members, docs)
            if (hits.isEmpty()) item(span = { full }) { Text("Nothing matches “$query”.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(hits, span = { full }) { (m, d, match) ->
                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth().clickable { if (d != null) onDocument(d.id) else onMember(m.id) }) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (d != null) DocumentIcon(d, 44.dp) else Avatar(m.name, m.photoFile, 44.dp, shared = m.shared)
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(d?.name ?: m.name, fontWeight = FontWeight.SemiBold)
                            Text(listOfNotNull(m.name.takeIf { d != null } ?: m.relation, match).joinToString(" · "), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        } else {
            items(members, key = { it.id }) { m ->
                val n = counts.firstOrNull { it.memberId == m.id }?.n ?: 0
                PersonCircle(m.name, "${if (m.shared) "Shared" else m.relation} · $n doc${if (n == 1) "" else "s"}", { onMember(m.id) }) { Avatar(m.name, m.photoFile, 132.dp, shared = m.shared) }
            }
            item {
                PersonCircle("Add another", null, onAddMember, muted = true) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.background, border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline), modifier = Modifier.size(132.dp)) {
                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Add, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        }
    }
}

/** Plain circular photo, no ring or highlight box. */
@Composable
private fun PersonCircle(title: String, subtitle: String?, onClick: () -> Unit, muted: Boolean = false, photo: @Composable () -> Unit) {
    Column(Modifier.clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        photo()
        Spacer(Modifier.size(10.dp))
        Text(title, fontWeight = if (muted) FontWeight.SemiBold else FontWeight.Bold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onBackground)
        if (subtitle != null) Text(subtitle, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

private data class Hit(val member: MemberEntity, val doc: DocumentEntity?, val match: String?)

private fun search(q: String, members: List<MemberEntity>, docs: List<DocumentEntity>): List<Hit> {
    val needle = q.trim().lowercase()
    val compact = needle.replace(" ", "")
    val out = mutableListOf<Hit>()
    members.forEach { m ->
        if (m.name.lowercase().contains(needle)) out += Hit(m, null, null)
        docs.filter { it.memberId == m.id }.forEach { d ->
            val f = d.fields.firstOrNull { it.value.lowercase().replace(" ", "").contains(compact) }
            if (d.name.lowercase().contains(needle) || f != null) out += Hit(m, d, f?.let { "${it.label}: ${if (it.sensitive) maskValue(it.value) else it.value}" })
        }
    }
    return out.take(40)
}

private fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 0..4 -> "Up late"; in 5..11 -> "Good morning"; in 12..16 -> "Good afternoon"; else -> "Good evening"
}

