package com.familydocs.app.ui.passwords

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.familydocs.app.domain.DocIcon
import com.familydocs.app.ui.components.BackBar
import com.familydocs.app.ui.components.DocIconTile
import com.familydocs.app.ui.components.LocalContainer
import com.familydocs.app.ui.components.copySensitive
import com.familydocs.app.ui.theme.MonoFont
import kotlinx.coroutines.launch
import java.security.SecureRandom

@Composable
fun PasswordsScreen(memberId: String, onBack: () -> Unit) {
    val c = LocalContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val member by c.repo.member(memberId).collectAsStateWithLifecycle(null)
    val items by c.repo.passwords(memberId).collectAsStateWithLifecycle(emptyList())
    var revealed by remember { mutableStateOf(setOf<String>()) }
    var adding by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { BackBar("${member?.name ?: ""} · Passwords", onBack) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { adding = true }, icon = { Icon(Icons.Outlined.Add, null) }, text = { Text("Add login") },
                containerColor = MaterialTheme.colorScheme.secondary, contentColor = MaterialTheme.colorScheme.onSecondary)
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(bottom = 100.dp)) {
            if (items.isEmpty()) item { Text("No passwords yet.", Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(items, key = { it.id }) { p ->
                val show = p.id in revealed
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 4.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    DocIconTile("Passwords", size = 38.dp, iconOverride = DocIcon.KEY, hueOverride = 0xFF8C6A2B)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(p.site, fontWeight = FontWeight.SemiBold)
                        Text(p.username, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(if (show) p.password else "••••••••••", fontFamily = MonoFont, fontSize = 13.sp)
                    }
                    IconButton({ revealed = if (show) revealed - p.id else revealed + p.id }) { Icon(if (show) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, if (show) "Hide" else "Show") }
                    IconButton({ copySensitive(context, p.site, p.password) }) { Icon(Icons.Outlined.ContentCopy, "Copy password", Modifier.size(19.dp)) }
                    IconButton({ scope.launch { c.repo.deletePassword(p.id) } }) { Icon(Icons.Outlined.Delete, "Delete", Modifier.size(19.dp)) }
                }
                HorizontalDivider(Modifier.padding(start = 70.dp), color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }

    if (adding) {
        var site by remember { mutableStateOf("") }
        var user by remember { mutableStateOf("") }
        var pass by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text("Add login") },
            text = {
                Column {
                    OutlinedTextField(site, { site = it }, label = { Text("Website or app") }, singleLine = true)
                    OutlinedTextField(user, { user = it }, label = { Text("Username or phone") }, singleLine = true)
                    OutlinedTextField(pass, { pass = it }, label = { Text("Password") }, singleLine = true,
                        trailingIcon = { IconButton({ pass = generatePassword() }) { Icon(Icons.Outlined.Casino, "Generate password") } })
                }
            },
            confirmButton = { TextButton({ scope.launch { c.repo.savePassword(memberId, site.trim(), user.trim(), pass); adding = false } }, enabled = site.isNotBlank()) { Text("Save") } },
            dismissButton = { TextButton({ adding = false }) { Text("Cancel") } },
        )
    }
}

private val WORDS = listOf("mango", "river", "lotus", "monsoon", "cedar", "harbor", "saffron", "olive", "aegean", "tulip", "teesta", "lantern")
private fun generatePassword(): String {
    val r = SecureRandom()
    fun w() = WORDS[r.nextInt(WORDS.size)]
    return "${w()}-${w().replaceFirstChar { it.uppercase() }}-${r.nextInt(90) + 10}${"!#%&"[r.nextInt(4)]}"
}
