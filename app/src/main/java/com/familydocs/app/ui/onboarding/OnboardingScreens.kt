package com.familydocs.app.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import com.familydocs.app.domain.Countries
import com.familydocs.app.ui.components.CountryPicker
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.foundation.Image
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.familydocs.app.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.familydocs.app.security.BiometricHelper
import com.familydocs.app.ui.components.Avatar
import com.familydocs.app.ui.components.BackBar
import com.familydocs.app.ui.components.DocIconTile
import com.familydocs.app.ui.components.appViewModel
import com.familydocs.app.ui.security.PasscodeSetupScreen
import com.familydocs.app.ui.theme.BrandFont

@Composable
fun OnboardingFlow(onDone: () -> Unit) {
    val nav = rememberNavController()
    val vm = appViewModel { OnboardingViewModel(it) }
    val context = LocalContext.current
    NavHost(nav, startDestination = "welcome") {
        composable("welcome") { WelcomeScreen { nav.navigate("register") } }
        composable("register") { RegisterScreen(vm, onBack = { nav.popBackStack() }) { nav.navigate("passcode") } }
        composable("passcode") {
            PasscodeSetupScreen(title = "Step 2 of 2", onBack = { nav.popBackStack() }) { code ->
                vm.pendingPasscode = code
                if (BiometricHelper.available(context)) nav.navigate("biometric") else vm.finish(false, onDone)
            }
        }
        composable("biometric") { BiometricOptIn(onChoice = { vm.finish(it, onDone) }) }
    }
}

@Composable
fun WelcomeScreen(onStart: () -> Unit) {
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp)) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("Passport", "Aadhaar", "PAN Card", "Residence Permit").forEach { DocIconTile(it, size = 64.dp) }
            }
        }
        Text("Family Docs", fontFamily = BrandFont, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(8.dp))
        Text("I got tired of losing access to things, so here we are", style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(22.dp))
        Button(onStart, Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp)) { Text("Create account", fontWeight = FontWeight.SemiBold) }
        Text(
            "Encrypted on this phone · backups go to your Google Drive",
            Modifier.fillMaxWidth().padding(top = 14.dp), textAlign = TextAlign.Center, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RegisterScreen(vm: OnboardingViewModel, onBack: () -> Unit, title: String = "Create account", showStep: Boolean = true, onNext: () -> Unit) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
        BackBar(title, onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (showStep) Text("Step 1 of 2 · Your details", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            vm.message?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            OutlinedTextField(vm.name, { vm.name = it }, Modifier.fillMaxWidth(), label = { Text("Full name") }, leadingIcon = { Icon(Icons.Outlined.Person, null) },
                isError = "name" in vm.errors, supportingText = vm.errors["name"]?.let { { Text(it) } }, singleLine = true, shape = RoundedCornerShape(14.dp))
            Row(verticalAlignment = Alignment.Top) {
                var open by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton({ open = true }, Modifier.height(56.dp).padding(top = 6.dp), shape = RoundedCornerShape(14.dp)) { Text(vm.countryCode) }
                    DropdownMenu(open, { open = false }) {
                        listOf("+91" to "India", "+30" to "Greece", "+44" to "UK", "+1" to "US/Canada", "+49" to "Germany", "+971" to "UAE", "+65" to "Singapore").forEach { (cc, n) ->
                            DropdownMenuItem({ Text("$cc  $n") }, { vm.countryCode = cc; open = false })
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(vm.phone, { vm.phone = it }, Modifier.weight(1f), label = { Text("Phone number") }, leadingIcon = { Icon(Icons.Outlined.PhoneAndroid, null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), isError = "phone" in vm.errors,
                    supportingText = vm.errors["phone"]?.let { { Text(it) } }, singleLine = true, shape = RoundedCornerShape(14.dp))
            }
            // Google account is required: it is the backup email, the Drive backup target and the passcode-recovery identity.
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Google account", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (vm.googleLinked) {
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(16.dp)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Avatar(vm.name, null, 52.dp, bitmap = vm.photoBitmap)
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(vm.name.ifBlank { "Google account" }, fontWeight = FontWeight.SemiBold)
                                Text(vm.email, fontSize = 12.sp)
                                Text("Backups go to this Google Drive", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = vm::unlinkGoogle) { Icon(Icons.Outlined.Close, "Unlink Google") }
                        }
                    }
                } else {
                    OutlinedButton({ vm.continueWithGoogle(context) }, Modifier.fillMaxWidth().height(52.dp), enabled = !vm.busy, shape = RoundedCornerShape(14.dp)) {
                        Image(painterResource(R.drawable.ic_google), null, Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp)); Text(if (vm.busy) "Connecting…" else "Continue with Google")
                    }
                }
                Text(vm.errors["email"] ?: "Used for Drive backups and to recover your passcode.", fontSize = 12.sp,
                    color = if ("email" in vm.errors) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // Countries the family keeps documents for: become the vault's country pills and "Issued by" choices.
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Countries you keep documents for", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                CountryPicker(vm.countries, vm::toggleCountry)
                Text(vm.errors["countries"] ?: "These become the country filters and the \u201cIssued by\u201d choices. Change them any time in Settings.", fontSize = 12.sp,
                    color = if ("countries" in vm.errors) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Button({ if (vm.validate()) onNext() }, Modifier.fillMaxWidth().padding(20.dp).height(54.dp), shape = RoundedCornerShape(16.dp)) { Text("Continue", fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
fun BiometricOptIn(onChoice: (Boolean) -> Unit) {
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(1f))
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(112.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Fingerprint, null, Modifier.size(60.dp), tint = MaterialTheme.colorScheme.secondary) }
        }
        Spacer(Modifier.height(26.dp))
        Text("Unlock with fingerprint?", style = MaterialTheme.typography.titleLarge)
        Text("Faster than typing your passcode. You can change this later in Settings.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
        Spacer(Modifier.weight(1f))
        Button({ onChoice(true) }, Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp)) { Text("Use fingerprint") }
        TextButton({ onChoice(false) }, Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Not now") }
    }
}

