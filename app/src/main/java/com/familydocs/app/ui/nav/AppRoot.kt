package com.familydocs.app.ui.nav

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.familydocs.app.ui.components.LocalContainer
import com.familydocs.app.ui.components.appViewModel
import com.familydocs.app.ui.document.AddDocumentScreen
import com.familydocs.app.ui.document.DocumentScreen
import com.familydocs.app.ui.ds.Fd
import com.familydocs.app.ui.ds.FdEase
import com.familydocs.app.ui.ds.FdToastHost
import com.familydocs.app.ui.ds.LocalToast
import com.familydocs.app.ui.home.FamilyScreen
import com.familydocs.app.ui.member.EditMemberScreen
import com.familydocs.app.ui.member.MemberScreen
import com.familydocs.app.ui.onboarding.OnboardingFlow
import com.familydocs.app.ui.onboarding.OnboardingViewModel
import com.familydocs.app.ui.onboarding.RegisterScreen
import com.familydocs.app.ui.passwords.PasswordsScreen
import com.familydocs.app.ui.security.LockScreen
import com.familydocs.app.ui.security.PasscodeSetupScreen
import com.familydocs.app.ui.settings.SettingsScreen
import com.familydocs.app.ui.ds.cropBoxPx
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// The demo's screen change: the new screen slides in 24px and fades up from 20% (.28s, cubic-bezier(.2,.8,.2,1));
// going back it comes from the other side. The old screen is simply replaced.
private const val SLIDE_MS = 280
fun AnimatedContentTransitionScope<NavBackStackEntry>.fdEnter(): EnterTransition =
    slideInHorizontally(tween(SLIDE_MS, easing = FdEase)) { (24 * it / 390f).toInt().coerceAtLeast(24) } + fadeIn(tween(SLIDE_MS, easing = FdEase), initialAlpha = .2f)
fun AnimatedContentTransitionScope<NavBackStackEntry>.fdPopEnter(): EnterTransition =
    slideInHorizontally(tween(SLIDE_MS, easing = FdEase)) { -(24 * it / 390f).toInt().coerceAtLeast(24) } + fadeIn(tween(SLIDE_MS, easing = FdEase), initialAlpha = .2f)
@Suppress("UnusedReceiverParameter")
fun AnimatedContentTransitionScope<NavBackStackEntry>.fdExit(): ExitTransition = ExitTransition.None

/** Onboarding until an account + passcode exist; afterwards the vault, with the lock screen drawn on top while locked. */
@Composable
fun AppRoot(activity: FragmentActivity) {
    val c = LocalContainer.current
    val registeredFlow = remember { c.repo.account.map { it != null && c.passcode.isSet } }
    val registered by registeredFlow.collectAsStateWithLifecycle(initialValue = null)
    val locked by c.appLock.locked.collectAsStateWithLifecycle()
    val toast = LocalToast.current

    Box(Modifier.fillMaxSize().background(Fd.c.bg)) {
        when (registered) {
            null -> Unit
            false -> OnboardingFlow(onDone = { c.appLock.unlock() })
            true -> {
                VaultNav()
                if (locked) LockScreen(activity)
            }
        }
        FdToastHost(toast)
    }
}

@Composable
private fun VaultNav() {
    val c = LocalContainer.current
    val nav = rememberNavController()
    val scope = rememberCoroutineScope()
    val toast = LocalToast.current
    val idArg = listOf(navArgument("id") { type = NavType.StringType })
    val box = cropBoxPx()

    NavHost(nav, startDestination = "home", enterTransition = { fdEnter() }, exitTransition = { fdExit() },
        popEnterTransition = { fdPopEnter() }, popExitTransition = { fdExit() }) {
        composable("home") {
            FamilyScreen(
                onMember = { nav.navigate("member/$it") },
                onAddMember = { nav.navigate("member-edit") },
                onSettings = { nav.navigate("settings") },
                onDocument = { nav.navigate("doc/$it") },
            )
        }
        composable("member/{id}", idArg) { e ->
            val id = e.arguments!!.getString("id")!!
            MemberScreen(
                id, onBack = { nav.popBackStack() }, onEdit = { nav.navigate("member-edit?id=$id") },
                onPasswords = { nav.navigate("passwords/$id") }, onDocument = { nav.navigate("doc/$it") },
                onAddDocument = { nav.navigate("doc-edit/$id") },
            )
        }
        composable("member-edit?id={id}", listOf(navArgument("id") { type = NavType.StringType; nullable = true; defaultValue = null })) { e ->
            EditMemberScreen(e.arguments?.getString("id"), onBack = { nav.popBackStack() }, onDeleted = { nav.popBackStack("home", inclusive = false) })
        }
        composable("doc/{id}", idArg) { e ->
            val id = e.arguments!!.getString("id")!!
            DocumentScreen(id, onBack = { nav.popBackStack() }, onEdit = { memberId -> nav.navigate("doc-edit/$memberId?docId=$id") })
        }
        composable(
            "doc-edit/{memberId}?docId={docId}",
            listOf(navArgument("memberId") { type = NavType.StringType }, navArgument("docId") { type = NavType.StringType; nullable = true; defaultValue = null }),
        ) { e ->
            AddDocumentScreen(e.arguments!!.getString("memberId")!!, e.arguments?.getString("docId"), onBack = { nav.popBackStack() },
                onSaved = { nav.popBackStack(); toast.show("Saved") })
        }
        composable("passwords/{id}", idArg) { e -> PasswordsScreen(e.arguments!!.getString("id")!!, onBack = { nav.popBackStack() }) }
        composable("settings") {
            SettingsScreen(onBack = { nav.popBackStack() }, onEditAccount = { nav.navigate("account") }, onChangePasscode = { nav.navigate("passcode") })
        }
        composable("account") {
            val vm = appViewModel(key = "account-edit") { OnboardingViewModel(it) }
            RegisterScreen(vm, onBack = { nav.popBackStack() }, title = "Your account", showStep = false) {
                vm.finish(null, box) { nav.popBackStack(); toast.show("Account updated") }
            }
        }
        composable("passcode") {
            PasscodeSetupScreen("Change passcode", onBack = { nav.popBackStack() }) { code ->
                scope.launch { withContext(Dispatchers.Default) { c.passcode.set(code) }; nav.popBackStack(); toast.show("Passcode updated") }
            }
        }
    }
}
