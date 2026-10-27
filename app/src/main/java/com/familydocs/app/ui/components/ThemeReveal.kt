package com.familydocs.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import kotlin.math.hypot
import kotlin.math.max

/**
 * Circular theme reveal (Telegram-style):
 *  1. take a picture of the screen as it is now (old theme),
 *  2. switch the theme for real underneath,
 *  3. draw the old picture on top with a hole that grows from the tapped button until nothing of it is left.
 * Nothing goes blank and nothing snaps, because the old picture and the live screen are pixel-identical at the start.
 */
class ThemeReveal {
    /** Centre of the button that started the switch, in root coordinates. */
    var origin by mutableStateOf(Offset.Zero)
    var oldFrame by mutableStateOf<ImageBitmap?>(null)
        private set
    val progress = Animatable(0f)
    /** Set by the root: captures the current screen. */
    var capture: (suspend () -> ImageBitmap)? = null

    suspend fun play(from: Offset? = null, apply: () -> Unit) {
        if (oldFrame != null) return
        from?.let { origin = it }
        val shot = runCatching { capture?.invoke() }.getOrNull()
        if (shot == null) { apply(); return }
        progress.snapTo(0f)
        oldFrame = shot
        apply()
        try {
            progress.animateTo(1f, tween(1000, easing = SlowPauseFast))
        } finally {
            oldFrame = null
        }
    }
}

/** Slow start → eases to a near-pause at 45% of the radius → accelerates out to the edges. Same curve as the prototype. */
val SlowPauseFast = Easing { t ->
    if (t < 0.5f) {
        val u = 2 * t
        0.45f * (if (u < 0.5f) 4 * u * u * u else 1 - (-2 * u + 2).let { it * it * it } / 2)
    } else {
        val u = 2 * t - 1
        0.45f + 0.55f * u * u * u
    }
}

val LocalThemeReveal = staticCompositionLocalOf { ThemeReveal() }

/** Draw this as a sibling *above* the recorded app content. */
@Composable
fun ThemeRevealOverlay(reveal: ThemeReveal) {
    val frame = reveal.oldFrame ?: return
    Canvas(Modifier.fillMaxSize()) {
        val o = reveal.origin
        val r = (hypot(max(o.x, size.width - o.x), max(o.y, size.height - o.y)) + 2f) * reveal.progress.value
        val hole = Path().apply { addOval(Rect(center = o, radius = r)) }
        clipPath(hole, clipOp = ClipOp.Difference) { drawImage(frame) }
    }
}
