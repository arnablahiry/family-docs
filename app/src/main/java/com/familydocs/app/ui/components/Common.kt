package com.familydocs.app.ui.components

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.PersistableBundle
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.outlined.House
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.familydocs.app.AppContainer
import com.familydocs.app.ocr.Images
import com.familydocs.app.ui.theme.DisplayFont
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

private val FALLBACK = listOf(0xFF4B6BAF, 0xFFA0573C, 0xFF2F7F7A, 0xFF7A4FB5, 0xFF8A6D1F, 0xFFB04A6E)

/** Circular photo with a single soft ring (no gradient). Falls back to initials. */
@Composable
fun Avatar(name: String, photoFile: String?, size: Dp, modifier: Modifier = Modifier, ring: Boolean = false, bitmap: Bitmap? = null, shared: Boolean = false) {
    if (shared && photoFile == null && bitmap == null) {
        val accent = MaterialTheme.colorScheme.secondary
        Box(
            modifier.size(size).clip(CircleShape)
                .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(accent.copy(alpha = .32f), accent.copy(alpha = .12f))))
                .border(1.5.dp, accent.copy(alpha = .35f), CircleShape),
            contentAlignment = Alignment.Center,
        ) { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Outlined.House, "Common (shared)", Modifier.size(size * 0.44f), tint = accent) }
        return
    }
    val img = bitmap?.asImageBitmap() ?: rememberVaultImage(photoFile, 600)
    val ringColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.55f)
    Box(
        modifier
            .size(size)
            .then(if (ring) Modifier.border(2.5.dp, ringColor, CircleShape).padding(5.dp) else Modifier)
            .clip(CircleShape)
            .background(if (img == null) Color(FALLBACK[Math.floorMod(name.hashCode(), FALLBACK.size)]) else MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (img != null) Image(img, null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        else Text(
            name.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() },
            color = Color.White, fontFamily = DisplayFont, fontSize = (size.value * 0.36f).sp,
        )
    }
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
