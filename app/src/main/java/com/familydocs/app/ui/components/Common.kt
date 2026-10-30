package com.familydocs.app.ui.components

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.familydocs.app.AppContainer
import com.familydocs.app.ocr.Images
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

val LocalContainer = staticCompositionLocalOf<AppContainer> { error("No AppContainer") }

@Composable
inline fun <reified VM : ViewModel> appViewModel(key: String? = null, crossinline create: (AppContainer) -> VM): VM {
    val c = LocalContainer.current
    return viewModel(key = key, factory = viewModelFactory { initializer { create(c) } })
}

/** Decrypts an image from the vault off the main thread. */
@Composable
fun rememberVaultImage(fileId: String?, maxPx: Int = 1400): ImageBitmap? {
    val files = LocalContainer.current.files
    // Already decrypted (this screen or one before it): straight away, no fade-in a frame later.
    val state by produceState(fileId?.let { VaultImages.get(it, maxPx) }, fileId) {
        value = fileId?.let { id -> VaultImages.get(id, maxPx) ?: withContext(Dispatchers.IO) {
            VaultImages.get(id, maxPx) ?: files.get(id)?.let { Images.decode(it, maxPx)?.asImageBitmap() }?.also { VaultImages.put(id, maxPx, it) }
        } }
    }
    return state
}

/**
 * Pictures from the vault, decrypted once while the app is unlocked and shared by every card and avatar that shows
 * them (a vault with many cards decrypted the holder's photo once per card). File ids never change (an edited scan
 * is a new file). Emptied when the app locks and when the phone is wiped.
 */
object VaultImages {
    private val cache = object : android.util.LruCache<String, ImageBitmap>((Runtime.getRuntime().maxMemory() / 1024 / 8).toInt().coerceAtLeast(16 * 1024)) {
        override fun sizeOf(key: String, value: ImageBitmap) = (value.width * value.height * 4 / 1024).coerceAtLeast(1)
    }
    fun get(id: String, maxPx: Int): ImageBitmap? = cache.get("$id@$maxPx")
    fun put(id: String, maxPx: Int, image: ImageBitmap) { cache.put("$id@$maxPx", image) }
    fun clear() = cache.evictAll()
}

/** Copies as "sensitive" (hidden from clipboard previews on Android 13+) and clears it after 45 s. */
@OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
fun copySensitive(context: Context, label: String, value: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(label, value)
    if (Build.VERSION.SDK_INT >= 33) clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
    cm.setPrimaryClip(clip)
    GlobalScope.launch {
        delay(45_000)
        if (Build.VERSION.SDK_INT >= 28 && cm.primaryClip?.getItemAt(0)?.text == value) cm.clearPrimaryClip()
    }
}

/**
 * Gets the pictures of [docs] and [members] ready in the background (card art, flags, photos), so the vaults open
 * without decoding anything on the way in. Results are the same pictures the screens would decode themselves.
 */
suspend fun warmPictures(app: AppContainer, docs: List<com.familydocs.app.data.DocumentEntity>, members: List<com.familydocs.app.data.MemberEntity>) =
    withContext(Dispatchers.Default) {
        if (app.appLock.locked.value) return@withContext
        val ctx = app.appContext
        com.familydocs.app.ui.ds.ResImages.warm(ctx, docs.flatMap { com.familydocs.app.ui.ds.cardImageIds(ctx, it) }.toSet())
        // the photos the cards and avatars show (same sizes as they ask for)
        val wanted = members.mapNotNull { it.photoFile?.let { f -> f to 700 } } +
            docs.filter { it.display != com.familydocs.app.data.DisplayMode.ICON }.mapNotNull { it.pictureFile?.let { f -> f to 900 } }
        wanted.distinct().forEach { (id, px) ->
            if (VaultImages.get(id, px) == null) runCatching { app.files.get(id)?.let { Images.decode(it, px)?.asImageBitmap() }?.let { VaultImages.put(id, px, it) } }
        }
    }
