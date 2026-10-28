package com.familydocs.app.ui.ds

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.draw.shadow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.familydocs.app.domain.Countries
import kotlin.math.max

// ---------------- passcode ----------------

/**
 * `.dots`: 13px rings that fill and grow slightly; change [shakeKey] to shake (wrong code).
 * [success]: the unlock moment (demo `.dots.ok`): dots turn green, gather into the middle and a check pops in.
 */
@Composable
fun FdDots(length: Int, filled: Int, shakeKey: Int, success: Boolean = false) {
    val c = Fd.c
    val shake = remember { Animatable(0f) }
    val gather = remember { Animatable(0f) }
    val check = remember { Animatable(0f) }
    LaunchedEffect(shakeKey) { if (shakeKey > 0) shake.animateTo(0f, keyframes { durationMillis = 400; -8f at 80; 8f at 160; -8f at 240; 8f at 320; 0f at 400 }) }
    LaunchedEffect(success) {
        if (!success) { gather.snapTo(0f); check.snapTo(0f); return@LaunchedEffect }
        launch { gather.animateTo(1f, tween(340, easing = CubicBezierEasing(.55f, 0f, .25f, 1f))) }
        delay(220)
        check.animateTo(1f, tween(440, easing = CubicBezierEasing(.3f, 1.6f, .5f, 1f)))
    }
    Box(contentAlignment = Alignment.Center) {
        Row(
            Modifier.height(16.dp).offset { IntOffset(shake.value.dp.roundToPx(), 0) }.semantics { contentDescription = if (success) "Unlocked" else "$filled of $length digits entered" },
            horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(length) { i ->
                val on = i < filled
                val ok = success
                val fill by animateColorAsState(if (ok) c.ok else if (on) c.primary else Color.Transparent, tween(if (ok) 100 else 150), label = "dot")
                val s by animateFloatAsState(if (on) 1.1f else 1f, tween(150), label = "dotScale")
                val toCenter = ((length - 1) / 2f - i) * 31f // 13dp dot + 18dp gap
                Box(
                    Modifier.size(13.dp).graphicsLayer {
                        val g = gather.value
                        translationX = toCenter * g * density
                        val k = s * (1f - .65f * g); scaleX = k; scaleY = k
                        alpha = 1f - ((g - .4f) / .6f).coerceIn(0f, 1f)
                    }.clip(CircleShape).background(fill).border(1.5.dp, if (ok) c.ok else if (on) c.primary else c.muted, CircleShape),
                )
            }
        }
        if (success) Box(
            Modifier.size(36.dp).graphicsLayer { val v = check.value; scaleX = .3f + .7f * v; scaleY = .3f + .7f * v; alpha = v.coerceIn(0f, 1f) }
                .shadow(8.dp, CircleShape, ambientColor = c.ok, spotColor = c.ok).clip(CircleShape).background(c.ok),
            contentAlignment = Alignment.Center,
        ) { FdIcon("check", 21.dp, 2.6f, Color.White) }
    }
}

/** `.keys`: 3×4 grid of 76px round keys with letters under the numerals; fingerprint and delete are flat. */
@Composable
fun FdKeypad(onDigit: (Char) -> Unit, onDelete: () -> Unit, onBiometric: (() -> Unit)? = null) {
    val letters = listOf("", "ABC", "DEF", "GHI", "JKL", "MNO", "PQRS", "TUV", "WXYZ")
    Column(verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        for (row in 0 until 3) Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            for (col in 1..3) { val d = row * 3 + col; Key("$d", { onDigit('0' + d) }) { Numeral("$d", letters[d - 1]) } }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            if (onBiometric != null) Key("Use fingerprint", onBiometric, flat = true) { FdIcon("finger", 28.dp, 1.6f, Fd.c.muted) }
            else Spacer(Modifier.size(76.dp))
            Key("0", { onDigit('0') }) { Numeral("0", "") }
            Key("Delete", onDelete, flat = true) { FdIcon("bksp", 26.dp, 1.6f, Fd.c.muted) }
        }
    }
}

@Composable
private fun Numeral(n: String, l: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(n, style = fdText(30f, FontWeight.Light, lineHeight = 30f))
        Text(l, style = fdText(9.5f, FontWeight.SemiBold, Fd.c.muted, spacing = .22f, lineHeight = 12f), modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun Key(label: String, onClick: () -> Unit, flat: Boolean = false, content: @Composable () -> Unit) {
    val c = Fd.c
    val haptic = LocalHapticFeedback.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val bg by animateColorAsState(
        when { flat && pressed -> c.fg.copy(alpha = .06f); flat -> Color.Transparent; pressed -> c.key(.20f); else -> c.key(.08f) }, tween(120), label = "key")
    val s by animateFloatAsState(if (pressed && !flat) .95f else 1f, tween(120), label = "keyScale")
    Box(
        Modifier.size(76.dp).scale(s).clip(CircleShape).background(bg)
            .clickable(interactionSource = source, indication = null) { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick() }
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { content() }
}

// ---------------- photo crop (`.crop`) ----------------

class CropState { var zoom by mutableStateOf(1f); var pan by mutableStateOf(Offset.Zero) }

/**
 * 180px circle you drag the photo around in, plus the zoom slider underneath (as in the demo).
 * Panning is clamped so the circle is always filled. [placeholder] shows when there is no photo.
 */
@Composable
fun FdPhotoCropper(bitmap: Bitmap?, state: CropState, placeholder: @Composable () -> Unit) {
    val c = Fd.c
    val boxDp = 180.dp
    val boxPx = with(LocalDensity.current) { (boxDp - 6.dp).toPx() } // the photo area inside the border/padding
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        Box(
            Modifier.size(boxDp).border(1.5.dp, c.line, CircleShape).padding(3.dp).clip(CircleShape).background(c.surface2)
                .then(if (bitmap == null) Modifier else Modifier.pointerInput(bitmap) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val e = awaitPointerEvent()
                            state.zoom = (state.zoom * e.calculateZoom()).coerceIn(1f, 3f)
                            state.pan = clampPan(state.pan + e.calculatePan(), bitmap, boxPx, state.zoom)
                            e.changes.forEach { if (it.positionChanged()) it.consume() } // keeps the page from scrolling while you drag
                        } while (e.changes.any { it.pressed })
                    }
                }),
            contentAlignment = Alignment.Center,
        ) {
            if (bitmap != null) Image(bitmap.asImageBitmap(), "Photo", contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = state.zoom; scaleY = state.zoom; translationX = state.pan.x; translationY = state.pan.y })
            else placeholder()
        }
        if (bitmap != null) Column(Modifier.fillMaxWidth()) {
            Text("Zoom · drag the photo to position it", style = fdText(12f, FontWeight.Medium, c.muted))
            Slider(
                value = state.zoom, onValueChange = { state.zoom = it; state.pan = clampPan(state.pan, bitmap, boxPx, it) }, valueRange = 1f..3f,
                colors = SliderDefaults.colors(thumbColor = c.primary, activeTrackColor = c.primary, inactiveTrackColor = c.line),
            )
        }
    }
}

private fun clampPan(p: Offset, b: Bitmap, box: Float, zoom: Float): Offset {
    val base = max(box / b.width, box / b.height)
    val w = b.width * base * zoom; val h = b.height * base * zoom
    val mx = max(0f, (w - box) / 2); val my = max(0f, (h - box) / 2)
    return Offset(p.x.coerceIn(-mx, mx), p.y.coerceIn(-my, my))
}

/** Bakes what the circle shows into a 512px square (displayed as a circle everywhere). */
fun cropToSquare(src: Bitmap, state: CropState, boxPx: Float, out: Int = 512): Bitmap {
    val base = max(boxPx / src.width, boxPx / src.height)
    val w = src.width * base * state.zoom; val h = src.height * base * state.zoom
    val k = out / boxPx
    val left = (boxPx / 2 + state.pan.x - w / 2) * k; val top = (boxPx / 2 + state.pan.y - h / 2) * k
    return Bitmap.createBitmap(out, out, Bitmap.Config.ARGB_8888).also {
        Canvas(it).drawBitmap(src, null, RectF(left, top, left + w * k, top + h * k), Paint(Paint.FILTER_BITMAP_FLAG))
    }
}

/** Crop box size in px for [cropToSquare] (the visible circle inside the 3dp padding). */
@Composable
fun cropBoxPx(): Float = with(LocalDensity.current) { (180.dp - 6.dp).toPx() }

/** Row of the illustrated characters; tap one to use it as the default picture. */
@Composable
fun FdCharacterPicker(selected: String?, onPick: (String) -> Unit, googlePhoto: Bitmap? = null, onGooglePhoto: () -> Unit = {}, keys: List<String>? = null) {
    val c = Fd.c
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        // `.charb.gp`: your Google profile photo as one more option, with a small G badge.
        if (googlePhoto != null) item {
            Box(Modifier.size(58.dp)) {
                Box(Modifier.size(58.dp).padding(4.dp).clip(CircleShape).clickable(onClick = onGooglePhoto)) {
                    Image(googlePhoto.asImageBitmap(), "Your Google profile photo", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
                Box(Modifier.align(Alignment.BottomEnd).offset(2.dp, 2.dp).size(20.dp).clip(CircleShape).background(c.surface).border(1.dp, c.line, CircleShape),
                    contentAlignment = Alignment.Center) {
                    Image(painterResource(com.familydocs.app.R.drawable.ic_google), null, Modifier.size(12.dp))
                }
            }
        }
        items(if (keys == null) CHARACTERS else CHARACTERS.filter { it.first in keys }) { (key, res) ->
            val on = key == selected
            Box(
                Modifier.size(58.dp).border(2.5.dp, if (on) c.primary else Color.Transparent, CircleShape).padding(4.dp).clip(CircleShape).clickable { onPick(key) },
            ) { Image(painterResource(res), "Character", Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        }
    }
}

// ---------------- countries (`.clist` / `.crow`) ----------------

/**
 * Search bar + scrollable list of every country with its flag.
 * multi: selected countries show first as removable chips; single: [allowNone] adds "Other / none".
 */
@Composable
fun FdCountryPicker(selected: List<String>, onToggle: (String) -> Unit, multi: Boolean = true, pinned: List<String> = emptyList(), allowNone: Boolean = false) {
    val c = Fd.c
    var query by remember { mutableStateOf("") }
    val q = query.trim().lowercase()
    val matches = remember(q) { Countries.ALL.filter { q.isEmpty() || Countries.name(it).lowercase().contains(q) || it.lowercase() == q } }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (multi && selected.isNotEmpty()) FdChips {
            selected.forEach { code -> FdChip(Countries.name(code), true, { onToggle(code) }, leading = { FdFlag(code, 18.dp) },
                trailing = { FdIcon("close", 14.dp, 2f, Fd.c.onPrimary.copy(alpha = .7f)) }) }
        }
        FdInput(query, { query = it }, placeholder = "Search all countries", icon = "search")
        val top = if (q.isEmpty()) (if (multi) selected else selected + pinned).distinct().filter { it.isNotEmpty() } else emptyList()
        Column(
            Modifier.fillMaxWidth().heightIn(max = 264.dp).clip(RoundedCornerShape(16.dp)).background(c.surface).border(1.dp, c.line, RoundedCornerShape(16.dp))
                .verticalScroll(rememberScrollState()),
        ) {
            var first = true
            fun divider() = !first.also { first = false }
            if (allowNone && q.isEmpty()) CountryRow("", selected.firstOrNull() == "", divider()) { onToggle("") }
            top.forEach { code -> CountryRow(code, code in selected, divider()) { onToggle(code) } }
            if (top.isNotEmpty() && q.isEmpty() && !multi) Text("ALL COUNTRIES", style = fdText(10.5f, color = c.muted, spacing = .08f),
                modifier = Modifier.fillMaxWidth().background(c.surface).padding(start = 14.dp, top = 12.dp, bottom = 6.dp))
            matches.filter { it !in top }.forEach { code -> CountryRow(code, code in selected, divider()) { onToggle(code) } }
            if (matches.isEmpty()) Text("No country matches “$query”.", style = fdText(11.5f, color = c.muted), modifier = Modifier.padding(12.dp))
        }
    }
}

@Composable
private fun CountryRow(code: String, on: Boolean, divider: Boolean, onClick: () -> Unit) {
    val c = Fd.c
    Column {
        if (divider) Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
        Row(
            Modifier.fillMaxWidth().background(if (on) c.mix(c.primary, .07f, c.surface) else c.surface).clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (code.isEmpty()) Box(Modifier.size(24.dp, 16.dp).clip(RoundedCornerShape(3.dp)).background(c.surface2), contentAlignment = Alignment.Center) {
                FdIcon("close", 14.dp, 2f, c.muted)
            } else FdFlag(code, 24.dp)
            Text(if (code.isEmpty()) "Other / none" else Countries.name(code), style = fdText(14f, if (on) FontWeight.Bold else FontWeight.Normal), modifier = Modifier.weight(1f))
            Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) { if (on) FdIcon("check", 18.dp, 2.4f, c.primary) }
        }
    }
}

