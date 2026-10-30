package com.familydocs.app.ui.ds

import android.content.Context
import android.util.LruCache
import android.util.TypedValue
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource

/**
 * The app's bundled pictures (card motifs, flags, characters, event art), decoded once and shared. painterResource
 * decodes a bitmap again for every card that shows it, on the main thread, so a vault with many cards stuttered while
 * opening. Same images, same drawing: only the decoding is shared.
 */
object ResImages {
    private val cache = object : LruCache<Int, ImageBitmap>((Runtime.getRuntime().maxMemory() / 1024 / 8).toInt().coerceAtLeast(16 * 1024)) {
        override fun sizeOf(key: Int, value: ImageBitmap) = (value.width * value.height * 4 / 1024).coerceAtLeast(1)
    }
    private val vector = HashMap<Int, Boolean>()

    fun isVector(context: Context, id: Int): Boolean = synchronized(vector) {
        vector.getOrPut(id) { TypedValue().also { context.resources.getValue(id, it, true) }.string?.endsWith(".xml") == true }
    }

    /** The decoded picture (decodes it the first time). Safe on any thread: [warm] uses it in the background. */
    fun get(context: Context, id: Int): ImageBitmap = cache.get(id) ?: run {
        // exactly what painterResource decodes
        (context.resources.getDrawable(id, null) as android.graphics.drawable.BitmapDrawable).bitmap.asImageBitmap().also { cache.put(id, it) }
    }

    /** Decodes these ahead of time, off the main thread, so the screen that shows them doesn't wait. */
    fun warm(context: Context, ids: Collection<Int>) {
        ids.forEach { id -> if (id != 0 && cache.get(id) == null && !isVector(context, id)) runCatching { get(context, id) } }
    }
}

/** painterResource, with bitmaps decoded once for the whole app ([ResImages]); vector drawables as before. */
@Composable
fun fdPainter(@DrawableRes id: Int): Painter {
    val ctx = LocalContext.current
    if (ResImages.isVector(ctx, id)) return painterResource(id)
    return remember(id) { BitmapPainter(ResImages.get(ctx.applicationContext, id)) }
}
