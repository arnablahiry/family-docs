package com.familydocs.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.familydocs.app.domain.Countries

/**
 * Every country with its flag, behind a search bar.
 * [pinned] are listed first (e.g. the account's countries); [allowNone] adds an "Other / none" row (single choice).
 * With [multi], the selection is also shown as removable chips above the search bar.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CountryPicker(
    selected: List<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
    multi: Boolean = true,
    pinned: List<String> = emptyList(),
    allowNone: Boolean = false,
) {
    var query by remember { mutableStateOf("") }
    val q = query.trim().lowercase()
    val matches = Countries.ALL.filter { q.isEmpty() || Countries.name(it).lowercase().contains(q) || it.lowercase() == q }
    val top = if (q.isEmpty()) (selected + pinned).distinct().filter { it.isNotEmpty() } else emptyList()
    val rest = matches.filter { it !in top }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (multi && selected.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            selected.forEach { c ->
                InputChip(true, { onToggle(c) }, label = { Text(Countries.name(c)) }, leadingIcon = { Flag(c, 18.dp) },
                    trailingIcon = { Icon(Icons.Outlined.Close, "Remove ${Countries.name(c)}", Modifier.size(16.dp)) })
            }
        }
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), placeholder = { Text("Search all countries") },
            leadingIcon = { Icon(Icons.Outlined.Search, null) }, singleLine = true, shape = RoundedCornerShape(14.dp))
        Column(
            Modifier.fillMaxWidth().height(264.dp)
                .outlinedCard(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.outlineVariant, 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            if (allowNone && q.isEmpty()) CountryRow(None, selected.firstOrNull() == "", { onToggle("") })
            top.forEach { CountryRow(it, it in selected, { onToggle(it) }) }
            if (top.isNotEmpty() || (allowNone && q.isEmpty())) Text("ALL COUNTRIES", fontSize = 10.5.sp, letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 14.dp, top = 12.dp, bottom = 6.dp))
            rest.forEach { CountryRow(it, it in selected, { onToggle(it) }) }
            if (matches.isEmpty()) Text("No country matches “$query”.", Modifier.padding(14.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private const val None = ""

@Composable
private fun CountryRow(code: String, on: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
            .background(if (on) MaterialTheme.colorScheme.primary.copy(alpha = .07f) else androidx.compose.ui.graphics.Color.Transparent)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(26.dp, 20.dp), contentAlignment = Alignment.Center) {
            if (code.isEmpty()) Icon(Icons.Outlined.Close, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) else Flag(code, 24.dp)
        }
        Text(if (code.isEmpty()) "Other / none" else Countries.name(code), Modifier.weight(1f).padding(start = 12.dp), fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
        if (on) Icon(Icons.Outlined.Check, "Selected", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}
