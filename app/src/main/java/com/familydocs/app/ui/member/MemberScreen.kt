package com.familydocs.app.ui.member

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.ui.draw.alpha
import com.familydocs.app.domain.Countries
import com.familydocs.app.ui.components.Flag
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Reorder
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.familydocs.app.data.DocumentEntity
import com.familydocs.app.domain.DocIcon
import com.familydocs.app.security.VaultView
import com.familydocs.app.ui.components.Avatar
import com.familydocs.app.ui.components.BackBar
import com.familydocs.app.ui.components.DocIconTile
import com.familydocs.app.ui.components.DocumentIcon
import com.familydocs.app.ui.components.LocalContainer
import com.familydocs.app.ui.components.SectionTitle
import com.familydocs.app.ui.components.WalletStack
import com.familydocs.app.ui.components.outlinedCard
import com.familydocs.app.ui.components.tail
import com.familydocs.app.ui.components.validUntil
import com.familydocs.app.ui.theme.MonoFont
import kotlinx.coroutines.launch

private const val PASSWORD_HUE = 0xFF8C6A2B

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MemberScreen(memberId: String, onBack: () -> Unit, onEdit: () -> Unit, onPasswords: () -> Unit, onDocument: (String) -> Unit, onAddDocument: () -> Unit) {
    val c = LocalContainer.current
    val member by c.repo.member(memberId).collectAsStateWithLifecycle(null)
    val docs by c.repo.documents(memberId).collectAsStateWithLifecycle(emptyList())
    val passwords by c.repo.passwords(memberId).collectAsStateWithLifecycle(emptyList())
    val settings by c.settings.state.collectAsStateWithLifecycle()
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var hidden by rememberSaveable(memberId) { mutableStateOf(listOf<String>()) }
    val account by c.repo.account.collectAsStateWithLifecycle(null)
    val everyone by c.repo.members.collectAsStateWithLifecycle(emptyList())
    val order = Countries.decode(account?.countries)
    val m = member ?: return
    val codes = docs.map { it.issuer }.filter { it.isNotEmpty() }.distinct()
        .sortedBy { order.indexOf(it).let { i -> if (i < 0) 99 else i } }
    val shown = if (codes.size > 1) docs.filter { it.issuer !in hidden } else docs
    val open: (DocumentEntity) -> Unit = { expanded = null; onDocument(it.id) }

    Scaffold(
        topBar = { BackBar("", onBack) { IconButton(onEdit) { Icon(Icons.Outlined.Edit, "Edit ${m.name}") } } },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddDocument, icon = { Icon(Icons.Outlined.Add, null) }, text = { Text("Add document") },
                containerColor = MaterialTheme.colorScheme.secondary, contentColor = MaterialTheme.colorScheme.onSecondary,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), state = list, contentPadding = PaddingValues(bottom = 110.dp)) {
            item {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Avatar(m.name, m.photoFile, 104.dp, shared = m.shared)
                    Text(m.name, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 8.dp))
                    Text("${if (m.shared) "Shared" else m.relation} · ${docs.size} document${if (docs.size == 1) "" else "s"}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    if (m.shared) {
                        // Who can see this vault: every person in the family.
                        Row(
                            Modifier.padding(top = 8.dp).outlinedCard(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.outlineVariant, 50.dp).padding(start = 6.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            everyone.filter { !it.shared }.forEachIndexed { i, p -> Avatar(p.name, p.photoFile, 24.dp, Modifier.offset(x = (-6 * i).dp)) }
                            Text("Everyone in the family can see and edit", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item {
                SectionTitle("Vault") {
                    val options = listOf(VaultView.CARDS to "Cards", VaultView.LIST to "List", VaultView.ICONS to "Icons")
                    SingleChoiceSegmentedButtonRow {
                        options.forEachIndexed { i, (v, label) ->
                            SegmentedButton(
                                selected = settings.vaultView == v,
                                onClick = { expanded = null; c.settings.update { it.copy(vaultView = v) } },
                                shape = SegmentedButtonDefaults.itemShape(i, options.size),
                                icon = {},
                                label = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(when (v) { VaultView.CARDS -> Icons.Outlined.ViewAgenda; VaultView.LIST -> Icons.Outlined.Reorder; VaultView.ICONS -> Icons.Outlined.GridView }, null, Modifier.size(15.dp))
                                        Spacer(Modifier.size(4.dp)); Text(label, fontSize = 12.sp)
                                    }
                                },
                            )
                        }
                    }
                }
            }
            if (codes.size > 1) item {
                // Country pills: tap to show/hide that country's documents.
                FlowRow(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    codes.forEach { code ->
                        val on = code !in hidden
                        FilterChip(
                            selected = on,
                            onClick = { hidden = if (on) hidden + code else hidden - code },
                            label = { Text("${Countries.name(code)}  ${docs.count { it.issuer == code }}") },
                            leadingIcon = { Box(Modifier.alpha(if (on) 1f else 0.4f)) { Flag(code, 20.dp) } },
                            shape = RoundedCornerShape(50),
                        )
                    }
                }
            }
            when (settings.vaultView) {
                VaultView.CARDS -> {
                    item { PasswordsRow(passwords.size, onPasswords) }
                    item {
                        WalletStack(
                            shown, if (m.shared) "Family" else m.name, expanded,
                            onExpand = { id, topPx ->
                                expanded = id
                                // Bring the lifted card near the top of the screen (stack is item #3).
                                scope.launch { list.animateScrollToItem(3, (topPx - 120).toInt().coerceAtLeast(0)) }
                            },
                            onOpen = open,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        )
                    }
                }
                VaultView.LIST -> {
                    item { PasswordsRow(passwords.size, onPasswords) }
                    item { Spacer(Modifier.height(12.dp)) }
                    items(shown, key = { it.id }) { d -> DocListRow(d) { open(d) } }
                }
                VaultView.ICONS -> {
                    val tiles: List<DocumentEntity?> = listOf(null) + shown // null = Passwords tile
                    items(tiles.chunked(3)) { row ->
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEach { d ->
                                Column(
                                    Modifier.weight(1f).outlinedCard(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.outlineVariant, 20.dp)
                                        .clickable { if (d == null) onPasswords() else open(d) }.padding(vertical = 14.dp, horizontal = 6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    if (d == null) DocIconTile("Passwords", iconOverride = DocIcon.KEY, hueOverride = PASSWORD_HUE)
                                    else Box { DocumentIcon(d); Box(Modifier.align(Alignment.TopEnd).offset(x = 10.dp, y = (-6).dp)) { Flag(d.issuer, 16.dp) } }
                                    Text(d?.name ?: "Passwords", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
                                    Text(d?.tail() ?: "${passwords.size} saved", fontSize = 10.sp, fontFamily = MonoFont, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
            if (shown.isEmpty()) item {
                Text(if (docs.isEmpty()) "No documents yet. Tap Add document to scan or upload ${m.name}'s first card." else "No documents for the selected countries.", color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(24.dp))
            }
        }
    }
}

@Composable
private fun PasswordsRow(count: Int, onClick: () -> Unit) {
    Row(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().outlinedCard(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.outlineVariant, 16.dp)
            .clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DocIconTile("Passwords", size = 40.dp, iconOverride = DocIcon.KEY, hueOverride = PASSWORD_HUE)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text("Passwords", fontWeight = FontWeight.SemiBold)
            Text("$count saved login${if (count == 1) "" else "s"}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DocListRow(d: DocumentEntity, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        DocumentIcon(d, 42.dp)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(d.name, fontWeight = FontWeight.SemiBold)
            Text(d.tail(), fontSize = 12.sp, fontFamily = MonoFont, color = MaterialTheme.colorScheme.onSurfaceVariant)
            d.validUntil()?.let { Text("Valid until $it", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Flag(d.issuer, 20.dp)
        Spacer(Modifier.size(6.dp))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

