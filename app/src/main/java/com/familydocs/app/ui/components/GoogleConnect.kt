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

    fun finish(token: String?) {
        if (token == null) { latest(Result.failure(GoogleAccountHelper.Cancelled())); return }
        scope.launch { latest(runCatching { GoogleAccountHelper.Session(token, app.google.profile(token)) }) }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        if (r.resultCode != Activity.RESULT_OK) { latest(Result.failure(GoogleAccountHelper.Cancelled())); return@rememberLauncherForActivityResult }
        runCatching { app.google.resultFromIntent(r.data).accessToken }
            .onSuccess { finish(it) }
            .onFailure { latest(Result.failure(it)) }
    }

    return remember(launcher) {
        {
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
    }
}
