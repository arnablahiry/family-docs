package com.familydocs.app.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * In-app updates from this app's GitHub releases (the CI publishes a signed APK for every push).
 * Checks the latest release, downloads its APK into the app's cache and hands it to Android's installer.
 * Same signing key every release, so the installed app (and all its data) is simply updated.
 * The first time, Android asks once to allow "Install unknown apps" for Family Docs.
 */
class AppUpdater(private val context: Context) {
    data class Release(val version: String, val tag: String, val apkUrl: String, val bytes: Long)

    sealed interface State {
        data object Idle : State
        data object Checking : State
        data object UpToDate : State
        data class Available(val release: Release) : State
        data class Downloading(val release: Release, val progress: Float) : State
        data class Failed(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state

    val currentVersion: String = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()

    /** Looks for a newer release. Quiet on failure (no network etc.): the state just goes back to Idle. */
    suspend fun check() {
        if (_state.value is State.Downloading) return
        _state.value = State.Checking
        val r = withContext(Dispatchers.IO) { runCatching { latest() }.getOrNull() }
        _state.value = when {
            r == null -> State.Idle
            newer(r.version, currentVersion) -> State.Available(r)
            else -> State.UpToDate
        }
    }

    /** Downloads the APK (progress in [state]) and opens the installer; asks for the install permission first if needed. */
    suspend fun downloadAndInstall(r: Release) {
        if (!canInstall()) { askInstallPermission(); return }
        _state.value = State.Downloading(r, 0f)
        val file = runCatching {
            withContext(Dispatchers.IO) {
                val dir = File(context.cacheDir, "updates").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
                val out = File(dir, "FamilyDocs-${r.version}.apk")
                val c = (URL(r.apkUrl).openConnection() as HttpURLConnection).apply { connectTimeout = 15_000; readTimeout = 30_000; instanceFollowRedirects = true }
                val total = c.contentLengthLong.takeIf { it > 0 } ?: r.bytes
                c.inputStream.use { input ->
                    out.outputStream().use { o ->
                        val buf = ByteArray(64 * 1024); var done = 0L; var n: Int
                        while (input.read(buf).also { n = it } >= 0) {
                            o.write(buf, 0, n); done += n
                            if (total > 0) _state.value = State.Downloading(r, (done.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
                out
            }
        }.getOrElse { _state.value = State.Failed("Couldn't download the update. Check the connection and try again."); return }
        _state.value = State.Available(r)
        install(file)
    }

    fun canInstall(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    /** Android's own switch: Settings › Install unknown apps › Family Docs. */
    fun askInstallPermission() {
        context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun install(apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", apk)
        context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun latest(): Release? {
        val c = (URL("https://api.github.com/repos/$REPO/releases/latest").openConnection() as HttpURLConnection).apply {
            setRequestProperty("Accept", "application/vnd.github+json"); connectTimeout = 10_000; readTimeout = 15_000
        }
        if (c.responseCode != 200) return null
        val j = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
        val tag = j.getString("tag_name")
        val version = VERSION.find(tag)?.value ?: return null
        val assets = j.getJSONArray("assets")
        val apk = (0 until assets.length()).map { assets.getJSONObject(it) }.firstOrNull { it.getString("name").endsWith(".apk") } ?: return null
        return Release(version, tag, apk.getString("browser_download_url"), apk.optLong("size"))
    }

    companion object {
        const val REPO = "arnablahiry/family-docs"
        private val VERSION = Regex("\\d+\\.\\d+\\.\\d+")

        /** "0.5.10" > "0.5.9" */
        fun newer(a: String, b: String): Boolean {
            val x = a.split('.').map { it.toIntOrNull() ?: 0 }; val y = b.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
            for (i in 0 until maxOf(x.size, y.size)) { val d = x.getOrElse(i) { 0 } - y.getOrElse(i) { 0 }; if (d != 0) return d > 0 }
            return false
        }
    }
}
