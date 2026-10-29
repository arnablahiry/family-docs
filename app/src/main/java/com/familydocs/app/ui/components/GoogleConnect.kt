package com.familydocs.app.ui.components

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import com.familydocs.app.auth.GoogleAccountHelper
import kotlinx.coroutines.launch

/**
 * Returns a function that runs "Continue with Google": shows Google's account picker and consent screen
 * when needed, then hands back an access token (Drive + profile) and the account's name, email and photo.
 */
@Composable
fun rememberGoogleConnect(onResult: (Result<GoogleAccountHelper.Session>) -> Unit): () -> Unit {
    val app = LocalContainer.current
    val scope = rememberCoroutineScope()
    val latest by rememberUpdatedState(onResult)

    // A cached token Google no longer accepts (401: the grant expired, e.g. after 7 days while the OAuth app is in
    // "Testing", or it was revoked): forget it and go through Google once more instead of failing.
    val retried = remember { booleanArrayOf(false) }
    val start = remember { arrayOfNulls<() -> Unit>(1) }
    fun finish(token: String?) {
        if (token == null) { latest(Result.failure(GoogleAccountHelper.Cancelled())); return }
        scope.launch {
            val r = runCatching { GoogleAccountHelper.Session(token, app.google.profile(token)) }
            if (r.exceptionOrNull() is GoogleAccountHelper.Unauthorized && !retried[0]) { retried[0] = true; app.google.clearToken(token); start[0]?.invoke(); return@launch }
            retried[0] = false
            latest(r)
        }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        // Google returns its real error (e.g. "app not registered") inside the result even when it closes itself,
        // so always read it; only a plain back-out counts as cancelled.
        runCatching { app.google.resultFromIntent(r.data).accessToken }
            .onSuccess { token -> if (r.resultCode == Activity.RESULT_OK || token != null) finish(token) else latest(Result.failure(GoogleAccountHelper.Cancelled())) }
            .onFailure { e ->
                val code = (e as? com.google.android.gms.common.api.ApiException)?.statusCode
                latest(Result.failure(if (r.resultCode != Activity.RESULT_OK && (code == null || code == 16 || code == 12501)) GoogleAccountHelper.Cancelled() else e))
            }
    }

    start[0] = {
        scope.launch {
            val auth = runCatching { app.google.authorize() }.getOrElse { latest(Result.failure(it)); return@launch }
            val pending = auth.pendingIntent
            if (auth.hasResolution() && pending != null) {
                app.appLock.expectExternalActivity()
                launcher.launch(IntentSenderRequest.Builder(pending.intentSender).build())
            } else finish(auth.accessToken)
        }
        Unit
    }
    return remember(launcher) { { retried[0] = false; start[0]?.invoke(); Unit } }
}
