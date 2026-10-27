package com.familydocs.app.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.familydocs.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * "Continue with Google": reads name, email and profile photo via Credential Manager.
 * Used at registration (backup email + profile photo) and to prove identity when the passcode is forgotten.
 *
 * Setup: create an OAuth "Web application" client in Google Cloud Console and put its ID in
 * res/values/strings.xml → google_web_client_id. Also create an "Android" client with this app's SHA-1.
 */
class GoogleAccountHelper(private val appContext: Context) {
    data class Profile(val email: String, val name: String?, val photoUrl: String?)

    /** [activityContext] must be an Activity so the account picker can show. */
    suspend fun signIn(activityContext: Context): Result<Profile> = runCatching {
        val option = GetSignInWithGoogleOption.Builder(appContext.getString(R.string.google_web_client_id)).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val credential = CredentialManager.create(activityContext).getCredential(activityContext, request).credential
        check(credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) { "Unexpected credential type" }
        val g = GoogleIdTokenCredential.createFrom(credential.data)
        Profile(email = g.id, name = g.displayName, photoUrl = g.profilePictureUri?.toString())
    }

    /** Downloads the Google profile photo at 512 px. */
    suspend fun downloadPhoto(url: String): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            val big = url.replace(Regex("=s\\d+(-c)?$"), "=s512-c")
            (URL(big).openConnection() as HttpURLConnection).run {
                connectTimeout = 10_000; readTimeout = 15_000
                try { inputStream.use { it.readBytes() } } finally { disconnect() }
            }
        }.getOrNull()
    }
}
