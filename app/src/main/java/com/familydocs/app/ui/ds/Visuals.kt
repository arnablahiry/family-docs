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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.animation.core.spring
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import kotlin.math.roundToInt
import androidx.compose.ui.unit.IntOffset
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.key
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
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

// ---------------- event pictures (the demo's ART, rendered to images) ----------------

/** Key (stored in EventEntity.art), label, picture. Same order as the demo's EVENT_ARTS. */
val EVENT_ARTS: List<Triple<String, String, Int>> = listOf(
    Triple("mountain", "Mountains", R.drawable.art_mountain),
    Triple("beach", "Beach", R.drawable.art_beach),
    Triple("heritage", "Heritage", R.drawable.art_heritage),
    Triple("desert", "Desert", R.drawable.art_desert),
    Triple("jungle", "Jungle safari", R.drawable.art_jungle),
    Triple("trip", "Trip", R.drawable.art_trip),
    Triple("birthday", "Birthday", R.drawable.art_birthday),
    Triple("appointment", "Appointment", R.drawable.art_appointment),
    Triple("health", "Doctor", R.drawable.art_health),
    Triple("anniversary", "Anniversary", R.drawable.art_anniversary),
    Triple("festival", "Festival", R.drawable.art_festival),
)

fun artRes(key: String?): Int = EVENT_ARTS.firstOrNull { it.first == key }?.third ?: R.drawable.art_trip

/** Round event picture: the user's photo if there is one, otherwise the event's illustration. */
@Composable
fun FdEventPic(art: String, photoFile: String?, size: Dp, modifier: Modifier = Modifier, lift: Boolean = false) {
    val photo = rememberVaultImage(photoFile, 700)
    val base = modifier.size(size).then(if (lift) Modifier.shadow(10.dp, CircleShape, ambientColor = Color(0x730A0F1E), spotColor = Color(0x730A0F1E)) else Modifier).clip(CircleShape)
    if (photo != null) Image(photo, null, base, contentScale = ContentScale.Crop)
    else Image(painterResource(artRes(art)), null, base, contentScale = ContentScale.Crop)
}

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
        shared -> Image(painterResource(R.drawable.art_home), name, base, contentScale = ContentScale.Crop)
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
    DocIcon.RECEIPT -> "receipt"; DocIcon.ID -> "idcard"; DocIcon.FILE -> "file"; DocIcon.KEY -> "key"; DocIcon.PEOPLE -> "people"
    DocIcon.TICKET -> "ticket"; DocIcon.BED -> "bed"; DocIcon.TRAIN -> "train"
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

/** Value of the field that means [key] ("subject", "specialisation", "degree"…), if filled. */
fun DocumentEntity.fieldValue(key: String): String? =
    fields.firstOrNull { com.familydocs.app.ocr.FieldKeys.canonical(it.label) == key && it.value.isNotBlank() }?.value?.trim()

/** The document's main number, shown in full (e.g. "9649 3578 0824"); otherwise its first filled detail. Degrees: the year. */
fun DocumentEntity.tail(): String {
    if (DocTypes.forName(name).key == "education") fieldValue("year of passing")?.let { return it }
    val f = fields.firstOrNull { it.sensitive && it.value.isNotBlank() } ?: fields.firstOrNull { it.value.isNotBlank() }
    return when { f == null -> "No details yet"; f.sensitive -> f.value.trim(); else -> f.value.take(14) }
}

fun DocumentEntity.validUntil(): String? = fieldValue("expiry")

/**
 * Dates printed on the wallet card: the expiry date if there is one, otherwise the issue date; a passport shows both.
 * Nothing when neither is filled in (no "Added" date).
 */
fun DocumentEntity.cardDates(): List<Pair<String, String>> {
    val expiry = fieldValue("expiry")?.let { "VALID UNTIL" to it }
    val issued = fieldValue("issued")?.let { "ISSUED" to it }
    return if (DocTypes.forName(name).key == "passport") listOfNotNull(issued, expiry) else listOfNotNull(expiry ?: issued)
}

fun DocumentEntity.holder(fallback: String): String {
    fun get(re: String) = fields.firstOrNull { Regex(re, RegexOption.IGNORE_CASE).containsMatchIn(it.label) && it.value.isNotBlank() }?.value
    return get("^name$|insured|elector") ?: listOfNotNull(get("given"), get("surname")).joinToString(" ").ifBlank { fallback }
}

fun fmtDate(t: Long): String = SimpleDateFormat("d MMM yyyy", Locale.UK).format(Date(t))

// ---------------- wallet cards ----------------

/** Minimal card templates per kind of document (demo `CARD_TPL`). */
private enum class CardTpl { PASSPORT, ID, VISA, PAPER, MEMBER, PLAIN }

private fun cardTpl(key: String): CardTpl = when (key) {
    "passport" -> CardTpl.PASSPORT
    "aadhaar", "pan", "residence", "driving", "voter", "id" -> CardTpl.ID
    else -> CardTpl.PLAIN // every card now sits on its illustration; only passports and ID cards change layout
}

/** Passport-style machine-readable lines, built from the holder and the masked number (never the full number). */
private fun mrzLines(holder: String, cc: String, masked: String): Pair<String, String> {
    val name = holder.uppercase().replace(Regex("[^A-Z]+"), "<")
    fun pad(x: String) = (x + "<".repeat(44)).take(44)
    return pad("P<${cc.ifEmpty { "XXX" }}<$name") to pad("<<<<<${masked.filter(Char::isDigit)}<$cc")
}

/**
 * `.wcard`: credit-card shaped document (1.586:1) in one of the minimal templates:
 * passport (line globe + machine-readable lines), ID card (photo box + fields), visa (guilloche waves + stamp),
 * paper (light sheet, colour edge, folded corner, text lines), member card (big faint icon) or plain.
 */
@Composable
fun FdWalletCard(
    doc: DocumentEntity, holderFallback: String, open: Boolean, modifier: Modifier = Modifier,
    photoFile: String? = null, character: String? = null, shared: Boolean = false,
    /** Cast a shadow up onto the card behind it (every card in a stack but the first). */
    shadeAbove: Boolean = false,
) {
    val c = Fd.c
    val t = DocTypes.forName(doc.name)
    val hue = Color(t.hue)
    val deep = lerp(Color(0xFF0B1020), hue, .55f)
    val glow = lerp(Color.White, hue, .55f)
    val pic = if (doc.display != DisplayMode.ICON) rememberVaultImage(doc.pictureFile, 900) else null
    val tpl = if (pic != null) CardTpl.PLAIN else cardTpl(t.key)
    val paper = tpl == CardTpl.PAPER
    val ink = if (paper) c.mix(hue, .45f, c.fg) else Color.White
    val sheet = c.mix(hue, .10f, c.surface)
    val shadow by animateDpAsState(if (open) 22.dp else 14.dp, tween(350, easing = FdEase), label = "cardShadow")
    // The lifted (selected) card gets a soft light rim.
    val rim by animateFloatAsState(if (open) 1f else 0f, tween(300, easing = FdEase), label = "cardRim")
    val shape = RoundedCornerShape(18.dp)
    val dates = doc.cardDates()
    val holder = doc.holder(holderFallback)

    BoxWithConstraints(
        modifier.fillMaxWidth()
            // Elevation shadows fall downwards, where the next card covers them; so each card also darkens the strip
            // of the card behind it, just above its own top edge. Drawn by hand: it looks the same on every phone.
            .then(if (shadeAbove) Modifier.drawBehind {
                val h = 26.dp.toPx(); val inset = 4.dp.toPx(); val r = 18.dp.toPx()
                drawRoundRect(Brush.verticalGradient(0f to Color.Transparent, .55f to Color.Black.copy(alpha = if (c.dark) .32f else .16f),
                    1f to Color.Black.copy(alpha = if (c.dark) .62f else .38f), startY = -h, endY = r * .6f),
                    topLeft = Offset(inset, -h), size = androidx.compose.ui.geometry.Size(size.width - inset * 2, h + r),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r))
            } else Modifier)
            .shadow(shadow, shape, ambientColor = Color(0xB3000000), spotColor = Color(0xCC000000)).clip(shape)
            .border(2.dp, (if (paper) hue else Color.White).copy(alpha = (if (paper) .7f else .75f) * rim), shape)
            .then(if (paper) Modifier.background(sheet).border(1.dp, hue.copy(alpha = .22f), shape)
            else Modifier.background(Brush.linearGradient(listOf(hue, deep), Offset.Zero, Offset(900f, 1100f)))),
    ) {
        val w = maxWidth
        val wPx = with(LocalDensity.current) { w.toPx() }
        // ---- background art ----
        when {
            pic != null -> {
                Image(pic, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to lerp(Color.Black, hue, .88f), .22f to lerp(Color.Black, hue, .88f), .45f to Color(0x40050A19), 1f to Color(0xC7050A19))))
            }
            else -> {
                // `.wcard.art`: sky, hills and the type's motif; national documents in their country's flag colours.
                CardScene(t.key, t.hue, doc.issuer.takeIf { it.isNotEmpty() }, Modifier.fillMaxSize(), showFlag = doc.flagShown)
                Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(
                    if (c.dark) listOf(Color(0x42000000), Color(0x1A000000)) else listOf(Color(0x290A0F1E), Color.Transparent))))
            }
        }

        // ---- content ----
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(Modifier.height(30.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                FdIcon(t.icon.demoName(), 20.dp, tint = ink)
                Text(doc.name, style = fdText(15f, FontWeight.SemiBold, ink), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(doc.tail(), style = fdText(11.5f, color = ink.copy(alpha = .85f)), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = w * .45f))
                if (doc.issuer.isNotEmpty()) FdFlag(doc.issuer, 24.dp, ring = if (paper) Color(0x1F000000) else Color.White.copy(alpha = .55f))
            }
            when (tpl) {
                CardTpl.ID -> {
                    Spacer(Modifier.weight(1f))
                    // No holder photo on the card: holder, number and validity only.
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        CardLabel("HOLDER", holder.uppercase(), ink)
                        Text(doc.tail(), style = fdText(16f, color = ink, spacing = .06f), maxLines = 1)
                        dates.forEach { (l, v) -> CardLabel(l, v.uppercase(), ink) }
                    }
                }
                CardTpl.PAPER -> {
                    Column(Modifier.padding(top = 16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        listOf(.62f, .50f, .36f).forEach { f -> Box(Modifier.fillMaxWidth(f).height(5.dp).clip(RoundedCornerShape(3.dp)).background(ink.copy(alpha = .13f))) }
                    }
                    Spacer(Modifier.weight(1f))
                    CardBottom(holder, dates, open, ink)
                }
                CardTpl.PASSPORT -> {
                    Spacer(Modifier.weight(1f))
                    CardBottom(holder, dates, open, ink)
                    val (l1, l2) = mrzLines(holder, doc.issuer, doc.tail())
                    Column(Modifier.padding(top = 9.dp).alpha(.55f)) {
                        Text(l1, style = fdText(10f, color = ink, spacing = .14f, lineHeight = 14.5f), maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
                        Text(l2, style = fdText(10f, color = ink, spacing = .14f, lineHeight = 14.5f), maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
                    }
                }
                else -> {
                    Spacer(Modifier.weight(1f))
                    val subject = doc.fieldValue("subject")
                    if (t.key == "education" && subject != null) {
                        // Degree cards: the degree, then subject and specialisation instead of a number.
                        doc.fieldValue("degree")?.let { Text(it, style = fdText(12.5f, color = ink.copy(alpha = .85f)), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        Text(subject, style = fdText(19f, FontWeight.SemiBold, ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        doc.fieldValue("specialisation")?.let { Text(it, style = fdText(13.5f, color = ink.copy(alpha = .9f)), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    } else Text(doc.tail(), style = fdText(19f, color = ink, spacing = .06f))
                    Box(Modifier.padding(top = 8.dp)) { CardBottom(holder, dates, open, ink) }
                }
            }
        }
        // `.wopen`: shown on the lifted card, bottom-right, over every template.
        // Just under the header row, so the card's dates at the bottom stay visible while it's lifted.
        androidx.compose.animation.AnimatedVisibility(open, Modifier.align(Alignment.TopStart).padding(start = 16.dp, top = 50.dp),
            enter = fadeIn(tween(250, 200)), exit = fadeOut(tween(120))) {
            Box(Modifier.clip(RoundedCornerShape(99.dp)).background(if (paper) hue.copy(alpha = .16f) else Color.White.copy(alpha = .22f)).padding(horizontal = 9.dp, vertical = 3.dp)) {
                Text("Tap again to open", style = fdText(10.5f, color = ink))
            }
        }
    }
}

/** `.wbot`: Holder on the left, the card's dates on the right. */
@Composable
private fun CardBottom(holder: String, dates: List<Pair<String, String>>, open: Boolean, ink: Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
        CardLabel("HOLDER", holder.uppercase(), ink, Modifier.weight(1f, fill = false))
        if (dates.isNotEmpty()) Row(Modifier.padding(start = 12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            dates.forEach { (l, v) -> CardLabel(l, v.uppercase(), ink, end = true) }
        }
    }
}

@Composable
private fun CardLabel(label: String, value: String, ink: Color, modifier: Modifier = Modifier, end: Boolean = false) {
    Column(modifier, horizontalAlignment = if (end) Alignment.End else Alignment.Start) {
        Text(label, style = fdText(9f, FontWeight.Medium, ink.copy(alpha = .75f), spacing = .08f))
        Text(value, style = fdText(12f, FontWeight.SemiBold, ink, spacing = .03f), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** `.wemb`: thin-line globe (circle, two meridians, equator, two parallels), white at 17%. */
@Composable
private fun CardGlobe(modifier: Modifier) {
    androidx.compose.foundation.Canvas(modifier.alpha(.17f)) {
        val k = size.width / 100f
        val st = androidx.compose.ui.graphics.drawscope.Stroke(1.3.dp.toPx())
        drawCircle(Color.White, 46 * k, center, style = st)
        drawOval(Color.White, Offset(30 * k, 4 * k), androidx.compose.ui.geometry.Size(40 * k, 92 * k), style = st)
        drawOval(Color.White, Offset(14 * k, 4 * k), androidx.compose.ui.geometry.Size(72 * k, 92 * k), style = st)
        drawLine(Color.White, Offset(4 * k, 50 * k), Offset(96 * k, 50 * k), st.width)
        drawLine(Color.White, Offset(10.2f * k, 27 * k), Offset(89.8f * k, 27 * k), st.width)
        drawLine(Color.White, Offset(10.2f * k, 73 * k), Offset(89.8f * k, 73 * k), st.width)
    }
}

/** `.wwaves`: seven guilloche-like wave lines across the card, white at 13%. */
@Composable
private fun CardWaves(modifier: Modifier) {
    androidx.compose.foundation.Canvas(modifier.alpha(.13f)) {
        val sx = size.width / 300f; val sy = size.height / 100f
        listOf(38f, 47f, 56f, 65f, 74f, 83f, 92f).forEach { y ->
            val p = androidx.compose.ui.graphics.Path().apply {
                moveTo(0f, y * sy)
                for (i in 0 until 4) {
                    val x0 = i * 75f
                    quadraticBezierTo((x0 + 37.5f) * sx, (y + if (i % 2 == 0) -6f else 6f) * sy, (x0 + 75f) * sx, y * sy)
                }
            }
            drawPath(p, Color.White, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
        }
    }
}

/** `.wstamp`: round double-ring stamp, tilted −14°, "VISA" and the issuing country code. */
@Composable
private fun CardStamp(cc: String, modifier: Modifier) {
    Box(modifier.graphicsLayer { rotationZ = -14f }, contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val outer = 1.6.dp.toPx()
            drawCircle(Color.White.copy(alpha = .5f), size.minDimension / 2 - outer / 2, style = androidx.compose.ui.graphics.drawscope.Stroke(outer))
            val inner = 1.4.dp.toPx()
            drawCircle(Color.White.copy(alpha = .32f), size.minDimension / 2 - outer - 3.dp.toPx() - inner / 2, style = androidx.compose.ui.graphics.drawscope.Stroke(inner))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("VISA", style = fdText(13f, FontWeight.Bold, Color.White.copy(alpha = .75f), spacing = .2f))
            if (cc.isNotEmpty()) Text(cc, style = fdText(10f, color = Color.White.copy(alpha = .64f)))
        }
    }
}

/** Folded top-right corner of a paper card. */
@Composable
private fun CardFold(back: Color, fold: Color, modifier: Modifier) {
    androidx.compose.foundation.Canvas(modifier) {
        val s = size.width; val r = 5.dp.toPx()
        drawPath(androidx.compose.ui.graphics.Path().apply { moveTo(0f, 0f); lineTo(s, 0f); lineTo(s, s); close() }, back)
        drawPath(androidx.compose.ui.graphics.Path().apply {
            moveTo(0f, 0f); lineTo(s, s); lineTo(r, s); quadraticBezierTo(0f, s, 0f, s - r); close()
        }, fold)
    }
}

/**
 * The stacked wallet. Cards overlap leaving a 58dp strip. The first tap on ANY card (the last one too) lifts it out
 * whole; the cards below slide down out of its way and the other cards dim. A second tap opens it; a tap anywhere else
 * (handled by the screen, see MemberScreen) or Back puts it back.
 * Hold a card and drag it up or down to move it; [onReorder] gets the new order, top to bottom.
 */
@Composable
fun FdWalletStack(
    docs: List<DocumentEntity>, holderFallback: String, expandedId: String?,
    onExpand: (id: String?, topPx: Float) -> Unit, onOpen: (DocumentEntity) -> Unit, modifier: Modifier = Modifier,
    owner: com.familydocs.app.data.MemberEntity? = null,
    onOpenBounds: (androidx.compose.ui.geometry.Rect) -> Unit = {},
    onReorder: ((List<String>) -> Unit)? = null,
    stretch: () -> Float = { 0f },
    tilt: () -> Float = { 0f },
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val cardH = maxWidth / 1.586f
        val strip = 58.dp
        val gap = 16.dp
        val density = LocalDensity.current
        val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
        val stripPx = with(density) { strip.toPx() }

        // ---- hold and drag ----
        var dragId by remember { mutableStateOf<String?>(null) }
        var dragDy by remember { mutableFloatStateOf(0f) }
        // The new order stays on screen until the database sends it back, so cards don't jump back for a frame.
        var pending by remember { mutableStateOf<List<String>?>(null) }
        val ids = docs.map { it.id }
        LaunchedEffect(ids) { if (pending == ids || pending?.toSet() != ids.toSet()) pending = null }
        val base = pending?.takeIf { it.toSet() == ids.toSet() }?.map { id -> docs.first { it.id == id } } ?: docs
        val from = base.indexOfFirst { it.id == dragId }
        val target = if (from >= 0) (from + (dragDy / stripPx).roundToInt()).coerceIn(0, base.lastIndex) else -1
        val order = if (from >= 0 && target != from) base.toMutableList().apply { add(target, removeAt(from)) } else base
        val latestBase by rememberUpdatedState(base)
        val animScope = rememberCoroutineScope()
        // a light tick each time the held card passes another one
        var lastTarget by remember { mutableStateOf(-1) }
        LaunchedEffect(target) {
            if (dragId != null && lastTarget >= 0 && target != lastTarget) haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
            lastTarget = if (dragId != null) target else -1
        }
        val latestReorder by rememberUpdatedState(onReorder)

        // Just dropped: for a moment the cards below make room so it lands fully visible, then they slide back.
        var landingId by remember { mutableStateOf<String?>(null) }
        val openIndex = when {
            dragId != null -> -1
            landingId != null -> order.indexOfFirst { it.id == landingId }
            else -> order.indexOfFirst { it.id == expandedId }
        }
        val tops = ArrayList<Dp>(order.size)
        order.indices.forEach { i -> tops += if (i == 0) 0.dp else tops[i - 1] + if (openIndex == i - 1) cardH + gap else strip }
        // tap on a card: lift it, or open it when it's already lifted (looked up by id: positions change when reordering)
        val latestTap by rememberUpdatedState<(DocumentEntity, Int) -> Unit> { d, _ ->
            val at = order.indexOfFirst { it.id == d.id }
            when {
                d.id == expandedId -> onOpen(d)
                // another card is lifted: this tap only puts that one back
                expandedId != null -> onExpand(null, 0f)
                else -> onExpand(d.id, with(density) { tops.getOrNull(at)?.toPx() ?: 0f })
            }
        }
        val total = if (order.isEmpty()) 0.dp else tops.last() + cardH
        val height by animateDpAsState(total, tween(450, easing = FdEase), label = "stackH")
        // Overscroll "magnet": pulled down at the top ([stretch] > 0) the cards spread apart, a little more further down
        // the stack; pushed up at the bottom (< 0) they squish together. Read in the layout phase only, so it's smooth.
        val minGapPx = with(density) { 26.dp.toPx() }
        fun extra(i: Int): Float {
            val s = stretch()
            // the cards move with the list; only at the ends do they spread a little (top) or squish (bottom)
            return if (s >= 0f) s * .2f * i else maxOf(s * .22f, -(stripPx - minGapPx)) * i
        }
        Box(Modifier.fillMaxWidth().layout { m, c ->
            // Stretching grows the stack; squishing keeps its height, so the cards squeeze up towards the first one
            // and the space opens below them (shrinking it would let the list pull everything down instead).
            val h = (height.toPx() + maxOf(0f, extra(order.lastIndex.coerceAtLeast(0)))).roundToInt().coerceAtLeast(0)
            val p = m.measure(c.copy(minHeight = h, maxHeight = h)); layout(p.width, h) { p.place(0, 0) }
        }) {
            order.forEachIndexed { i, d ->
                key(d.id) {
                    val dragging = d.id == dragId
                    // While a card is held the others glide out of its way (smooth, no bounce).
                    val top by animateDpAsState(tops[i], if (dragId != null) tween(260, easing = FdEase) else tween(450, easing = FdEase), label = "cardTop")
                    val isOpen = i == openIndex
                    val dim by animateFloatAsState(if (openIndex >= 0 && !isOpen) .45f else 1f, tween(300), label = "cardDim")
                    // Pick-up: a springy pop to 1.05×; put-down: back to 1× with a soft squash as it lands
                    // (the under-damped spring dips just below 1 before settling).
                    val lift by animateFloatAsState(if (dragging) 1.05f else 1f,
                        if (dragging) spring(dampingRatio = .48f, stiffness = 520f) else spring(dampingRatio = .4f, stiffness = 330f), label = "cardLift")
                    // On drop the card glides from the finger into its slot and lands with a small overshoot.
                    var dropOffset by remember { mutableFloatStateOf(0f) }
                    // The held card follows the finger; the others slide to make room.
                    val place = if (dragging) Modifier.offset { IntOffset(0, (from * stripPx + dragDy).roundToInt()) }
                        else Modifier.offset { IntOffset(0, (top.toPx() + extra(i) + dropOffset).roundToInt()) }
                    FdWalletCard(
                        d, holderFallback, isOpen || dragging,
                        // Stays on top while held and while landing; then takes its place in the stack so the cards
                        // below close up over it (no snap behind them at the end).
                        Modifier.zIndex(if (dragging || d.id == landingId) 1000f else i.toFloat()).then(place).height(cardH).alpha(dim)
                            .graphicsLayer { scaleX = lift; scaleY = lift }
                            .onGloballyPositioned { if (isOpen) onOpenBounds(it.boundsInRoot()) }
                            // One gesture handler for both (a separate clickable would grab the touch first):
                            // quick tap → lift / open; hold still ~0.4 s → pick up and drag; move straight away → the list scrolls.
                            .semantics { role = Role.Button; onClick { latestTap(d, i); true } }
                            .pointerInput(d.id) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    var tapped = false
                                    var moved = false
                                    withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                                        while (true) {
                                            val ch = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                            if (!ch.pressed) { tapped = true; ch.consume(); break }
                                            if ((ch.position - down.position).getDistance() > viewConfiguration.touchSlop) { moved = true; break }
                                        }
                                    }
                                    when {
                                        tapped -> latestTap(d, i)
                                        moved || latestReorder == null -> Unit   // a scroll (or reordering is off): leave it to the list
                                        else -> {
                                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                            onExpand(null, 0f); dragId = d.id; dragDy = 0f
                                            // follows the finger exactly while held: no tilt, no wobble
                                            val ended = drag(down.id) { ch -> dragDy += ch.positionChange().y; ch.consume() }
                                            val b = latestBase
                                            val f = b.indexOfFirst { it.id == dragId }
                                            val t = if (f >= 0) (f + (dragDy / stripPx).roundToInt()).coerceIn(0, b.lastIndex) else -1
                                            if (ended && f >= 0 && t != f) {
                                                val newIds = b.map { it.id }.toMutableList().apply { add(t, removeAt(f)) }
                                                pending = newIds
                                                latestReorder?.invoke(newIds)
                                            }
                                            // where the card is now (under the finger) relative to where it will rest
                                            val fingerY = (if (f >= 0) f else 0) * stripPx + dragDy
                                            // (measured from the card's own animated slot position, so the hand-over is seamless)
                                            dropOffset = fingerY - top.toPx()
                                            dragId = null; dragDy = 0f
                                            landingId = d.id
                                            animScope.launch { kotlinx.coroutines.delay(560); if (landingId == d.id) landingId = null }
                                            animScope.launch {
                                                androidx.compose.animation.core.animate(dropOffset, 0f, animationSpec = spring(dampingRatio = .55f, stiffness = 320f)) { v, _ -> dropOffset = v }
                                            }
                                        }
                                    }
                                }
                            },
                        photoFile = owner?.photoFile, character = owner?.avatar, shared = owner?.shared == true, shadeAbove = i > 0 || dragging,
                    )
                }
            }
        }
    }
}
