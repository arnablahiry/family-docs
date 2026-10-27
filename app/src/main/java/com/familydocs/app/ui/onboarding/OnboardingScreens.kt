package com.familydocs.app.ui.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.familydocs.app.R
import com.familydocs.app.security.BiometricHelper
import com.familydocs.app.ui.components.LocalContainer
import com.familydocs.app.ui.components.appViewModel
import com.familydocs.app.ui.ds.BtnKind
import com.familydocs.app.ui.ds.Fd
import com.familydocs.app.ui.ds.FdAvatar
import com.familydocs.app.ui.ds.FdButton
import com.familydocs.app.ui.ds.FdCharacterPicker
import com.familydocs.app.ui.ds.FdChip
import com.familydocs.app.ui.ds.FdChips
import com.familydocs.app.ui.ds.FdCountryPicker
import com.familydocs.app.ui.ds.FdField
import com.familydocs.app.ui.ds.FdIcon
import com.familydocs.app.ui.ds.FdIconButton
import com.familydocs.app.ui.ds.FdInput
import com.familydocs.app.ui.ds.FdPhotoCropper
import com.familydocs.app.ui.ds.FdSavebar
import com.familydocs.app.ui.ds.FdTopBar
import com.familydocs.app.ui.ds.cropBoxPx
import com.familydocs.app.ui.ds.fdText
import com.familydocs.app.ui.ds.tap
import com.familydocs.app.ui.nav.fdEnter
import com.familydocs.app.ui.nav.fdExit
import com.familydocs.app.ui.nav.fdPopEnter
import com.familydocs.app.ui.security.PasscodeSetupScreen

@Composable
fun OnboardingFlow(onDone: () -> Unit) {
    val nav = rememberNavController()
    val vm = appViewModel { OnboardingViewModel(it) }
    val context = LocalContext.current
    val box = cropBoxPx()
    NavHost(nav, startDestination = "welcome", enterTransition = { fdEnter() }, exitTransition = { fdExit() },
        popEnterTransition = { fdPopEnter() }, popExitTransition = { fdExit() }) {
        composable("welcome") { WelcomeScreen { nav.navigate("register") } }
        composable("register") { RegisterScreen(vm, onBack = { nav.popBackStack() }) { nav.navigate("passcode") } }
        composable("passcode") {
            PasscodeSetupScreen(title = "Step 2 of 2", onBack = { nav.popBackStack() }) { code ->
                vm.pendingPasscode = code
                if (BiometricHelper.available(context)) nav.navigate("biometric") else vm.finish(false, box, onDone)
            }
        }
        composable("biometric") { BiometricOptIn(onBack = { nav.popBackStack() }) { vm.finish(it, box, onDone) } }
    }
}

/** `.welcome`: the demo's artwork (rendered from it, light + dark), heading, Create account, footnote. */
@Composable
fun WelcomeScreen(onStart: () -> Unit) {
    val c = Fd.c
    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 26.dp)) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Image(painterResource(if (c.dark) R.drawable.welcome_art_dark else R.drawable.welcome_art_light), null, Modifier.size(298.dp, 278.dp))
        }
        Text("I got tired of losing access to things, so here we are", style = fdText(30f, lineHeight = 33.6f, spacing = -.01f),
            modifier = Modifier.padding(bottom = 24.dp))
        FdButton("Create account", onStart)
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            FdIcon("lock", 13.dp, tint = c.muted)
            Text("  Encrypted on this phone · backups go to your Google Drive", style = fdText(11.5f, color = c.muted), textAlign = TextAlign.Center)
        }
    }
}

/** `.register`: name, phone, profile picture, Google account (or email until Google is set up), countries. Also "Your account". */
@Composable
fun RegisterScreen(vm: OnboardingViewModel, onBack: () -> Unit, title: String = "Create account", showStep: Boolean = true, onNext: () -> Unit) {
    val c = Fd.c
    val context = LocalContext.current
    val container = LocalContainer.current
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { vm.pickPhoto(context, it) } }
    val e = vm.errors
    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding().imePadding()) {
        FdTopBar(title, onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (showStep) Text("Step 1 of 2 · Your details", style = fdText(11.5f, color = c.muted))

            // ---- profile picture ----
            FdField("Profile picture", hint = if (vm.picked == null) "Shown on the home screen and lock screen. Pick a photo or one of the characters." else null) {
                if (vm.picked != null) {
                    FdPhotoCropper(vm.picked, vm.crop) {}
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        FdAvatar(vm.name.ifBlank { "You" }, 72.dp, photoFile = if (vm.hasPhoto) vm.savedPhoto else null, character = vm.avatar, lift = true)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            FdChips {
                                FdChip(if (vm.hasPhoto) "Change photo" else "Choose photo", false, {
                                    container.appLock.expectExternalActivity()
                                    photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                }, leading = { FdIcon("image", 16.dp, tint = c.fg) })
                                if (vm.hasPhoto) FdChip("Remove", false, vm::clearPhoto, leading = { FdIcon("trash", 16.dp, tint = c.fg) })
                            }
                        }
                    }
                }
                if (vm.picked != null) FdChips {
                    FdChip("Choose another", false, {
                        container.appLock.expectExternalActivity()
                        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }, leading = { FdIcon("image", 16.dp, tint = c.fg) })
                    FdChip("Use a character instead", false, vm::clearPhoto)
                }
                if (!vm.hasPhoto) FdCharacterPicker(vm.avatar, vm::chooseCharacter)
            }

            FdField("Full name", error = e["name"]) { FdInput(vm.name, { vm.name = it }, placeholder = "Your name", icon = "user", error = "name" in e) }

            FdField("Phone number", error = e["phone"]) {
                var open by remember { mutableStateOf(false) }
                FdInput(vm.phone, { vm.phone = it }, placeholder = "98765 43210", icon = "phone", error = "phone" in e, keyboardType = KeyboardType.Phone,
                    leading = {
                        Box {
                            Row(Modifier.clip(RoundedCornerShape(8.dp)).tap { open = true }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(vm.countryCode, style = fdText(15f), modifier = Modifier.padding(end = 2.dp))
                                FdIconButton("chev", { open = true }, size = 24.dp, iconSize = 14.dp, tint = c.muted, description = "Country code")
                            }
                            DropdownMenu(open, { open = false }) {
                                listOf("+91" to "India", "+30" to "Greece", "+44" to "UK", "+1" to "US / Canada", "+49" to "Germany", "+971" to "UAE", "+65" to "Singapore")
                                    .forEach { (cc, n) -> DropdownMenuItem({ Text("$cc  $n", style = fdText(14f)) }, { vm.countryCode = cc; open = false }) }
                            }
                        }
                    })
            }

            // ---- Google account (backup + passcode recovery) ----
            FdField(if (vm.googleReady) "Google account" else "Backup email", error = e["email"],
                hint = vm.message ?: when {
                    vm.googleReady ->"Used for Drive backups and to recover your passcode."
                    else -> "Google sign-in isn't set up in this build yet (README → Google setup). Type your email; you can link Google later."
                }) {
                when {
                    vm.googleLinked -> Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.accentSoft).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        FdAvatar(vm.name, 52.dp, photoFile = vm.savedPhoto, character = vm.avatar, bitmap = vm.picked)
                        Column(Modifier.weight(1f)) {
                            Text(vm.name.ifBlank { "Google account" }, style = fdText(14f, FontWeight.Bold))
                            Text(vm.email, style = fdText(12f, color = c.muted))
                            Text("Backups go to this Google Drive", style = fdText(12f, color = c.muted))
                        }
                        FdIconButton("close", vm::unlinkGoogle, iconSize = 18.dp, description = "Unlink Google")
                    }
                    vm.googleReady -> GoogleButton(busy = vm.busy) { vm.continueWithGoogle(context) }
                    else -> FdInput(vm.email, { vm.email = it }, placeholder = "name@gmail.com", icon = "mail", keyboardType = KeyboardType.Email, error = "email" in e)
                }
            }

            FdField("Countries you keep documents for", error = e["countries"],
                hint = "These become the country filters and the “Issued by” choices. You can change them later in Settings.") {
                FdCountryPicker(vm.countries, vm::toggleCountry)
            }
            Spacer(Modifier.height(8.dp))
        }
        FdSavebar { FdButton(if (vm.busy) "Saving…" else "Continue", { if (vm.validate()) onNext() }, enabled = !vm.busy) }
    }
}

/** `.gbtn` with the four-colour G. */
@Composable
fun GoogleButton(busy: Boolean, onClick: () -> Unit) {
    val c = Fd.c
    Row(
        Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(14.dp)).background(c.surface).border(1.5.dp, c.line, RoundedCornerShape(14.dp))
            .tap(enabled = !busy, onClick = onClick),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(painterResource(R.drawable.ic_google), null, Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(if (busy) "Connecting…" else "Continue with Google", style = fdText(14f, FontWeight.SemiBold))
    }
}

/** Fingerprint opt-in (demo "Almost done"). */
@Composable
fun BiometricOptIn(onBack: () -> Unit, onChoice: (Boolean) -> Unit) {
    val c = Fd.c
    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding()) {
        FdTopBar("Almost done", onBack)
        Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp).padding(top = 12.dp, bottom = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(112.dp).clip(CircleShape).background(c.accentSoft), contentAlignment = Alignment.Center) { FdIcon("finger", 60.dp, 1.5f, c.accent) }
            Text("Unlock with fingerprint?", style = fdText(20f, FontWeight.SemiBold), modifier = Modifier.padding(top = 26.dp, bottom = 4.dp))
            Text("Faster than typing your passcode. You can change this later in Settings.", style = fdText(13f, color = c.muted),
                textAlign = TextAlign.Center, modifier = Modifier.width(260.dp))
            Spacer(Modifier.weight(1f))
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                FdButton("Use fingerprint", { onChoice(true) })
                FdButton("Not now", { onChoice(false) }, kind = BtnKind.Ghost)
            }
        }
    }
}
