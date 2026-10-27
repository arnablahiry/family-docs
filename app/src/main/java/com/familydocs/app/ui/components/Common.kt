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
    val state by produceState<ImageBitmap?>(null, fileId) {
        value = fileId?.let { id -> withContext(Dispatchers.IO) { files.get(id)?.let { Images.decode(it, maxPx)?.asImageBitmap() } } }
    }
    return state
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
