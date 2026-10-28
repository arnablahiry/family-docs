package com.familydocs.app.auth

import android.content.Context
import android.content.Intent
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * "Continue with Google" in one step: Google's own consent screen picks the account and grants
 * Drive access (only files this app creates, plus a hidden app folder) together with name, email and photo.
 *
 * Uses Google Identity's AuthorizationClient, which only needs an *Android* OAuth client in Google Cloud
 * (package com.familydocs.app + the release SHA-1). No web client ID, no server.
 */
class GoogleAccountHelper(private val appContext: Context) {
    data class Profile(val email: String, val name: String?, val photoUrl: String?)
    data class Session(val token: String, val profile: Profile)

    private val client get() = Identity.getAuthorizationClient(appContext)

    fun request(): AuthorizationRequest = AuthorizationRequest.Builder().setRequestedScopes(SCOPES.map { Scope(it) }).build()

    /** A result with a resolution needs the UI to launch its PendingIntent (account picker + consent) once. */
    suspend fun authorize(): AuthorizationResult = client.authorize(request()).await()

    fun resultFromIntent(data: Intent?): AuthorizationResult = client.getAuthorizationResultFromIntent(data)

    /** Name, email and photo of the account that granted [token]. */
    suspend fun profile(token: String): Profile = withContext(Dispatchers.IO) {
        val conn = URL("https://www.googleapis.com/oauth2/v3/userinfo").openConnection() as HttpURLConnection
        try {
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.connectTimeout = 10_000; conn.readTimeout = 15_000
            check(conn.responseCode == 200) { "Google profile ${conn.responseCode}" }
            val j = JSONObject(conn.inputStream.use { it.readBytes() }.decodeToString())
            Profile(j.getString("email"), j.optString("name").ifBlank { null }, j.optString("picture").ifBlank { null })
        } finally { conn.disconnect() }
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

    /** Forgets the Google account on this phone: revokes the app's Drive grant and clears the remembered account. */
    suspend fun signOut() {
        val token = runCatching { authorize().takeIf { !it.hasResolution() }?.accessToken }.getOrNull()
        if (token != null) withContext(Dispatchers.IO) {
            runCatching {
                (URL("https://oauth2.googleapis.com/revoke").openConnection() as HttpURLConnection).run {
                    requestMethod = "POST"; doOutput = true; connectTimeout = 10_000; readTimeout = 10_000
                    setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                    outputStream.use { it.write("token=${URLEncoder.encode(token, "UTF-8")}".toByteArray()) }
                    responseCode; disconnect()
                }
            }
        }
        runCatching { CredentialManager.create(appContext).clearCredentialState(ClearCredentialStateRequest()) }
    }

    companion object {
        const val DRIVE_FILE = "https://www.googleapis.com/auth/drive.file"
        const val DRIVE_APPDATA = "https://www.googleapis.com/auth/drive.appdata"
        val SCOPES = listOf(DRIVE_FILE, DRIVE_APPDATA, "https://www.googleapis.com/auth/userinfo.email", "https://www.googleapis.com/auth/userinfo.profile", "openid")

        /** Short, human explanation of why Google didn't connect. */
        fun explain(t: Throwable): String {
            val code = (t as? ApiException)?.statusCode ?: (t.cause as? ApiException)?.statusCode
            return when {
                t is Cancelled -> "Google sign-in was cancelled"
                code == 10 || t.message?.contains("10:") == true ->
                    "Google hasn't registered this app yet. Finish the Google Cloud setup (README → Google setup), then try again."
                code == 7 || t is java.io.IOException -> "No connection to Google. Check the internet and try again."
                else -> "Google didn't connect: ${t.message ?: t.javaClass.simpleName}"
            }
        }
    }

    class Cancelled : Exception("cancelled")
}
