package com.familydocs.app.ui.ds

import android.annotation.SuppressLint
import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.familydocs.app.R
import com.familydocs.app.data.DisplayMode
import com.familydocs.app.data.DocumentEntity
import com.familydocs.app.domain.Countries
import com.familydocs.app.domain.DocIcon
import com.familydocs.app.domain.DocTypes
import com.familydocs.app.domain.maskValue
import com.familydocs.app.ui.components.rememberVaultImage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------------- characters (the demo's illustrated people) ----------------

/** Default pictures, rendered from the demo's faceSVG so they are identical. Keys are stored in MemberEntity.avatar. */
val CHARACTERS: List<Pair<String, Int>> = listOf(
    "char_blue_short" to R.drawable.char_blue_short,
    "char_peach_bun" to R.drawable.char_peach_bun,
    "char_lilac_long" to R.drawable.char_lilac_long,
    "char_mint_glasses" to R.drawable.char_mint_glasses,
    "char_sand_bun" to R.drawable.char_sand_bun,
    "char_rose_long" to R.drawable.char_rose_long,
    "char_sky_short" to R.drawable.char_sky_short,
    "char_slate_glasses" to R.drawable.char_slate_glasses,
)

fun characterRes(key: String?): Int? = CHARACTERS.firstOrNull { it.first == key }?.second

/** The demo's fallback hues for initials avatars and unknown document types. */
private val FALLBACK_HUES = listOf(0xFF4B6BAF, 0xFFA0573C, 0xFF2F7F7A, 0xFF7A4FB5, 0xFF8A6D1F, 0xFFB04A6E, 0xFF3F7C4B, 0xFF5868A8)

/** FNV-1a, same as the demo's hash(), so the same name gets the same colour. */
fun fnv(s: String): Long {
    var h = 2166136261L
    for (ch in s) { h = h xor ch.code.toLong(); h = (h * 16777619L) and 0xFFFFFFFFL }
    return h
}

/**
 * `.av`: circular picture. Order: chosen photo → shared house icon → illustrated character → coloured initials.
 * [lift] adds the demo's soft drop shadow (`.ring .av`).
 */
@Composable
fun FdAvatar(
    name: String, size: Dp, modifier: Modifier = Modifier, photoFile: String? = null, character: String? = null,
    shared: Boolean = false, bitmap: Bitmap? = null, lift: Boolean = false,
) {
    val c = Fd.c
    val photo: ImageBitmap? = bitmap?.asImageBitmap() ?: rememberVaultImage(photoFile, 700)
    val base = modifier.size(size).then(if (lift) Modifier.shadow(10.dp, CircleShape, ambientColor = Color(0x730A0F1E), spotColor = Color(0x730A0F1E)) else Modifier).clip(CircleShape)
    when {
        photo != null -> Image(photo, name, base.background(c.surface2), contentScale = ContentScale.Crop)
        shared -> Box(
            base.background(Brush.linearGradient(listOf(c.mix(c.accent, .32f, c.surface), c.mix(c.accent, .12f, c.surface)), Offset.Zero, Offset.Infinite))
                .border(1.5.dp, c.accent.copy(alpha = .35f), CircleShape),
            contentAlignment = Alignment.Center,
        ) { FdIcon("house", size * .44f, 1.6f, c.mix(c.accent, .7f, c.fg), description = name) }
        characterRes(character) != null -> Image(painterResource(characterRes(character)!!), name, base, contentScale = ContentScale.Crop)
        else -> {
            val n = name.trim().ifEmpty { "?" }
            val ini = n.split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2).joinToString("") { it.first().uppercase() }
            Box(base.background(Color(FALLBACK_HUES[(fnv(n) % FALLBACK_HUES.size).toInt()])), contentAlignment = Alignment.Center) {
                Text(ini, style = fdText(size.value * .38f, color = Color.White))
            }
        }
    }
}

// ---------------- flags ----------------

/** Same flag artwork as the demo (flag-icons, rendered to small images). 3:2 with 3px corners and a hairline edge. */
@SuppressLint("DiscouragedApi")
@Composable
fun FdFlag(code: String, width: Dp = 22.dp, ring: Color = Color(0x1F000000), greyscale: Boolean = false) {
    if (code.isEmpty()) return
    val ctx = LocalContext.current
    val id = remember(code) { ctx.resources.getIdentifier("flag_" + code.lowercase(), "drawable", ctx.packageName) }
    val shape = RoundedCornerShape(3.dp)
    val m = Modifier.size(width, width * 2 / 3).border(1.dp, ring, shape).clip(shape).semantics { contentDescription = Countries.name(code) }
    if (id != 0) Image(painterResource(id), null, m, contentScale = ContentScale.Crop,
        colorFilter = if (greyscale) ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) else null)
    else Box(m.background(Color(0x33808080)), contentAlignment = Alignment.Center) { Text(code, style = fdText(9f, FontWeight.Bold)) }
}

// ---------------- document icons ----------------

fun DocIcon.demoName(): String = when (this) {
    DocIcon.HOUSE -> "house"; DocIcon.BOLT -> "bolt"; DocIcon.FINGERPRINT -> "finger"; DocIcon.CARD -> "card"; DocIcon.PASSPORT -> "passport"
    DocIcon.PLANE -> "plane"; DocIcon.RESIDENCE -> "homeid"; DocIcon.CAR -> "car"; DocIcon.BALLOT -> "ballot"; DocIcon.SHIELD -> "shield"
    DocIcon.SYRINGE -> "syringe"; DocIcon.BANK -> "bank"; DocIcon.CAP -> "cap"; DocIcon.CERTIFICATE -> "cert"; DocIcon.MEDICAL -> "medical"
    DocIcon.RECEIPT -> "receipt"; DocIcon.ID -> "idcard"; DocIcon.FILE -> "file"; DocIcon.KEY -> "key"
}

/** `.dicon`: hue-tinted rounded tile with the type icon (or the chosen picture), initials badge for unknown types. */
@Composable
fun FdDocTile(name: String, size: Dp = 54.dp, picture: ImageBitmap? = null, iconOverride: String? = null, hueOverride: Long? = null) {
    val c = Fd.c
    val t = DocTypes.forName(name)
    val hue = Color(hueOverride ?: t.hue)
    val radius = if (size >= 80.dp) 24.dp else if (size <= 44.dp) 13.dp else 16.dp
    Box(Modifier.size(size).clip(RoundedCornerShape(radius)).background(c.tileBg(hue)), contentAlignment = Alignment.Center) {
        if (picture != null) Image(picture, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else {
            FdIcon(iconOverride ?: t.icon.demoName(), if (size >= 80.dp) 38.dp else size * .48f, 1.7f, c.tileFg(hue))
            if (!t.matched && t.initials.isNotEmpty() && iconOverride == null) Box(
                Modifier.align(Alignment.BottomEnd).padding(6.dp).clip(RoundedCornerShape(5.dp)).background(c.surface).padding(horizontal = 3.dp),
            ) { Text(t.initials, style = fdText(9f, FontWeight.Bold, lineHeight = 14f)) }
        }
    }
}

@Composable
fun FdDocIcon(doc: DocumentEntity, size: Dp = 54.dp) {
    val pic = if (doc.display != DisplayMode.ICON) rememberVaultImage(doc.pictureFile, 400) else null
    FdDocTile(doc.name, size, pic)
}

/** Masked preview of the main number, e.g. "•••• 8237". */
fun DocumentEntity.tail(): String {
    val f = fields.firstOrNull { it.sensitive && it.value.isNotBlank() } ?: fields.firstOrNull { it.value.isNotBlank() }
    return when { f == null -> "No details yet"; f.sensitive -> maskValue(f.value); else -> f.value.take(14) }
}

fun DocumentEntity.validUntil(): String? =
    fields.firstOrNull { Regex("expiry|valid until|valid till", RegexOption.IGNORE_CASE).containsMatchIn(it.label) && it.value.isNotBlank() }?.value

fun DocumentEntity.holder(fallback: String): String {
    fun get(re: String) = fields.firstOrNull { Regex(re, RegexOption.IGNORE_CASE).containsMatchIn(it.label) && it.value.isNotBlank() }?.value
    return get("^name$|insured|elector") ?: listOfNotNull(get("given"), get("surname")).joinToString(" ").ifBlank { fallback }
}

fun fmtDate(t: Long): String = SimpleDateFormat("d MMM yyyy", Locale.UK).format(Date(t))

// ---------------- wallet cards ----------------

/** `.wcard`: credit-card shaped document (1.586:1) with hue gradient, type icon, flag, masked number, holder and expiry. */
@Composable
fun FdWalletCard(doc: DocumentEntity, holderFallback: String, open: Boolean, modifier: Modifier = Modifier) {
    val t = DocTypes.forName(doc.name)
    val hue = Color(t.hue)
    val deep = lerp(Color(0xFF0B1020), hue, .55f)
    val glow = lerp(Color.White, hue, .55f)
    val pic = if (doc.display != DisplayMode.ICON) rememberVaultImage(doc.pictureFile, 900) else null
    val shadow by animateDpAsState(if (open) 18.dp else 6.dp, tween(350, easing = FdEase), label = "cardShadow")
    Box(
        modifier.fillMaxWidth().shadow(shadow, RoundedCornerShape(18.dp), ambientColor = deep, spotColor = deep).clip(RoundedCornerShape(18.dp))
            .background(Brush.linearGradient(listOf(hue, deep), Offset.Zero, Offset(900f, 1100f))),
    ) {
        if (pic != null) {
            Image(pic, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to lerp(Color.Black, hue, .88f), .22f to lerp(Color.Black, hue, .88f), .45f to Color(0x40050A19), 1f to Color(0xC7050A19))))
        } else {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val w = with(LocalDensity.current) { maxWidth.toPx() }
                Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(glow, Color.Transparent), center = Offset(w, 0f), radius = w * .66f)))
            }
        }
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(Modifier.height(30.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                FdIcon(t.icon.demoName(), 20.dp, tint = Color.White)
                Text(doc.name, style = fdText(15f, FontWeight.SemiBold, Color.White), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(doc.tail(), style = fdText(11.5f, color = Color.White.copy(alpha = .85f)))
                if (doc.issuer.isNotEmpty()) FdFlag(doc.issuer, 24.dp, ring = Color.White.copy(alpha = .55f))
            }
            Spacer(Modifier.weight(1f))
            Text(doc.tail(), style = fdText(19f, color = Color.White, spacing = .06f))
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                CardLabel("HOLDER", doc.holder(holderFallback).uppercase(), Modifier.weight(1f, fill = false))
                Box(contentAlignment = Alignment.BottomEnd) {
                    val until = doc.validUntil()
                    val right = until ?: fmtDate(doc.createdAt)
                    val fade by animateFloatAsState(if (open) 0f else 1f, tween(150), label = "rightLabel")
                    CardLabel(if (until != null) "VALID UNTIL" else "ADDED", right.uppercase(), Modifier.alpha(fade), end = true)
                    // Fully qualified: inside Box-in-Row-in-Column the RowScope/ColumnScope overloads would be picked and rejected (DSL scope).
                    androidx.compose.animation.AnimatedVisibility(open, enter = fadeIn(tween(250, 200)), exit = fadeOut(tween(120))) {
                        Box(Modifier.clip(RoundedCornerShape(99.dp)).background(Color.White.copy(alpha = .22f)).padding(horizontal = 9.dp, vertical = 3.dp)) {
                            Text("Tap again to open", style = fdText(10.5f, color = Color.White))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CardLabel(label: String, value: String, modifier: Modifier = Modifier, end: Boolean = false) {
    Column(modifier, horizontalAlignment = if (end) Alignment.End else Alignment.Start) {
        Text(label, style = fdText(9f, FontWeight.Medium, Color.White.copy(alpha = .75f), spacing = .08f))
        Text(value, style = fdText(12f, FontWeight.SemiBold, Color.White, spacing = .03f), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * The stacked wallet. Cards overlap leaving a 58dp strip. The first tap on ANY card (the last one too) lifts it out
 * whole; the cards below slide down out of its way and the other cards dim. A second tap opens it.
 */
@Composable
fun FdWalletStack(
    docs: List<DocumentEntity>, holderFallback: String, expandedId: String?,
    onExpand: (id: String?, topPx: Float) -> Unit, onOpen: (DocumentEntity) -> Unit, modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val cardH = maxWidth / 1.586f
        val strip = 58.dp
        val gap = 16.dp
        val openIndex = docs.indexOfFirst { it.id == expandedId }
        val tops = ArrayList<Dp>(docs.size)
        docs.indices.forEach { i -> tops += if (i == 0) 0.dp else tops[i - 1] + if (openIndex == i - 1) cardH + gap else strip }
        val total = if (docs.isEmpty()) 0.dp else tops.last() + cardH
        val height by animateDpAsState(total, tween(450, easing = FdEase), label = "stackH")
        val density = LocalDensity.current
        Box(Modifier.fillMaxWidth().height(height)) {
            docs.forEachIndexed { i, d ->
                val top by animateDpAsState(tops[i], tween(450, easing = FdEase), label = "cardTop")
                val isOpen = i == openIndex
                val dim by animateFloatAsState(if (openIndex >= 0 && !isOpen) .45f else 1f, tween(300), label = "cardDim")
                FdWalletCard(
                    d, holderFallback, isOpen,
                    Modifier.zIndex(i.toFloat()).offset(y = top).height(cardH).alpha(dim)
                        .tap { if (isOpen) onOpen(d) else onExpand(d.id, with(density) { tops[i].toPx() }) },
                )
            }
        }
    }
}

