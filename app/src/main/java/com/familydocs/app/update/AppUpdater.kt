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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * In-app updates from this app's GitHub releases (the CI publishes a signed APK for every push).
 * Checks the latest release, downloads its APK into the app's cache and hands it to Android's installer.
 * Same signing key every release, so the installed app (and all its data) is simply updated.
 * The first time, Android asks once to allow "Install unknown apps" for Lararium.
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
        // a download that was going when the app closed carries on here
        if (r != null && _state.value is State.Available) own.launch { resume(r) }
    }

    /**
     * Downloads the APK with Android's download manager, so it keeps going (with a notification) when Lararium is in
     * the background or the screen is off; progress in [state]; then opens the installer. Asks for the install
     * permission first if needed. Run it in a scope that outlives the screen (AppContainer.appScope).
     */
    suspend fun downloadAndInstall(r: Release) {
        if (!canInstall()) { askInstallPermission(); return }
        if (_state.value is State.Downloading) return
        _state.value = State.Downloading(r, 0f)
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
        val file = apkFile(r)
        val id = runCatching {
            withContext(Dispatchers.IO) {
                // an earlier download of the same version that finished: install it straight away
                if (file.exists() && r.bytes > 0 && file.length() == r.bytes) return@withContext -1L
                prefs.getLong(KEY_ID, -1L).takeIf { it >= 0 }?.let { old -> runCatching { dm.remove(old) } }
                file.parentFile?.listFiles()?.forEach { it.delete() }
                dm.enqueue(android.app.DownloadManager.Request(Uri.parse(r.apkUrl))
                    .setTitle("Lararium ${r.version}").setDescription("Update")
                    .setMimeType("application/vnd.android.package-archive")
                    .setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE)
                    .setDestinationInExternalFilesDir(context, android.os.Environment.DIRECTORY_DOWNLOADS, file.name))
                    .also { prefs.edit().putLong(KEY_ID, it).putString(KEY_VERSION, r.version).apply() }
            }
        }.getOrElse { _state.value = State.Failed("Couldn't start the download. Check the connection and try again."); return }
        if (id >= 0 && !follow(dm, id, r)) return
        _state.value = State.Available(r)
        runCatching { install(file) }.onFailure { _state.value = State.Failed("Downloaded, but the installer didn't open. Tap to try again.") }
    }

    /** Watches download [id] until it's done; true when the file is complete. */
    private suspend fun follow(dm: android.app.DownloadManager, id: Long, r: Release): Boolean {
        while (true) {
            val (status, done, total, reason) = withContext(Dispatchers.IO) {
                dm.query(android.app.DownloadManager.Query().setFilterById(id))?.use { c ->
                    if (!c.moveToFirst()) null
                    else listOf(
                        c.getLong(c.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_STATUS)),
                        c.getLong(c.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),
                        c.getLong(c.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_TOTAL_SIZE_BYTES)),
                        c.getLong(c.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_REASON)),
                    )
                } ?: listOf(android.app.DownloadManager.STATUS_FAILED.toLong(), 0L, 0L, 0L)
            }
            when (status.toInt()) {
                android.app.DownloadManager.STATUS_SUCCESSFUL -> return true
                android.app.DownloadManager.STATUS_FAILED -> {
                    _state.value = State.Failed("Couldn't download the update (${reason}). Check the connection and try again.")
                    return false
                }
                else -> {
                    val t = if (total > 0) total else r.bytes
                    val p = if (t > 0) (done * 100 / t).toInt().coerceIn(0, 100) / 100f else 0f
                    if ((_state.value as? State.Downloading)?.progress != p) _state.value = State.Downloading(r, p)
                }
            }
            kotlinx.coroutines.delay(400)
        }
    }

    /** After the app was closed mid-download: pick the download up again (or install it if it finished meanwhile). */
    suspend fun resume(r: Release) {
        val id = prefs.getLong(KEY_ID, -1L)
        if (id < 0 || prefs.getString(KEY_VERSION, null) != r.version || _state.value is State.Downloading) return
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
        _state.value = State.Downloading(r, 0f)
        if (follow(dm, id, r)) _state.value = State.Available(r) // finished: tapping Update installs it (no download)
    }

    private val prefs = context.getSharedPreferences("updater", Context.MODE_PRIVATE)
    /** Its own scope for following a download, so no screen closing stops it. */
    private val own = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.Main)
    private fun apkFile(r: Release) = File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS) ?: File(context.filesDir, "updates"), "Lararium-${r.version}.apk")

    fun canInstall(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    /** Android's own switch: Settings › Install unknown apps › Lararium. */
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
        const val REPO = "arnablahiry/lararium"
        private val VERSION = Regex("\\d+\\.\\d+\\.\\d+")
        private const val KEY_ID = "download_id"; private const val KEY_VERSION = "download_version"

        /** "0.5.10" > "0.5.9" */
        fun newer(a: String, b: String): Boolean {
            val x = a.split('.').map { it.toIntOrNull() ?: 0 }; val y = b.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
            for (i in 0 until maxOf(x.size, y.size)) { val d = x.getOrElse(i) { 0 } - y.getOrElse(i) { 0 }; if (d != 0) return d > 0 }
            return false
        }
    }
}
