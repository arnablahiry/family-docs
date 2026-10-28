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
    val shadow by animateDpAsState(if (open) 18.dp else 6.dp, tween(350, easing = FdEase), label = "cardShadow")
    val shape = RoundedCornerShape(18.dp)
    val until = doc.validUntil()
    val holder = doc.holder(holderFallback)

    BoxWithConstraints(
        modifier.fillMaxWidth().shadow(shadow, shape, ambientColor = deep, spotColor = deep).clip(shape)
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
                CardScene(t.key, t.hue, doc.issuer.takeIf { it.isNotEmpty() }, Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(
                    if (c.dark) listOf(Color(0x42000000), Color(0x1A000000)) else listOf(Color(0x290A0F1E), Color.Transparent))))
            }
        }

        // ---- content ----
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(Modifier.height(30.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                FdIcon(t.icon.demoName(), 20.dp, tint = ink)
                Text(doc.name, style = fdText(15f, FontWeight.SemiBold, ink), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(doc.tail(), style = fdText(11.5f, color = ink.copy(alpha = .85f)))
                if (doc.issuer.isNotEmpty()) FdFlag(doc.issuer, 24.dp, ring = if (paper) Color(0x1F000000) else Color.White.copy(alpha = .55f))
            }
            when (tpl) {
                CardTpl.ID -> {
                    Spacer(Modifier.weight(1f))
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Bottom) {
                        CardPhoto(photoFile, character, shared, Modifier.width(w * .26f).height(w * .26f / .78f))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            CardLabel("HOLDER", holder.uppercase(), ink)
                            Text(doc.tail(), style = fdText(16f, color = ink, spacing = .06f), maxLines = 1)
                            CardLabel(if (until != null) "VALID UNTIL" else "ADDED", (until ?: fmtDate(doc.createdAt)).uppercase(), ink)
                        }
                    }
                }
                CardTpl.PAPER -> {
                    Column(Modifier.padding(top = 16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        listOf(.62f, .50f, .36f).forEach { f -> Box(Modifier.fillMaxWidth(f).height(5.dp).clip(RoundedCornerShape(3.dp)).background(ink.copy(alpha = .13f))) }
                    }
                    Spacer(Modifier.weight(1f))
                    CardBottom(holder, until, doc.createdAt, open, ink)
                }
                CardTpl.PASSPORT -> {
                    Spacer(Modifier.weight(1f))
                    CardBottom(holder, until, doc.createdAt, open, ink)
                    val (l1, l2) = mrzLines(holder, doc.issuer, doc.tail())
                    Column(Modifier.padding(top = 9.dp).alpha(.55f)) {
                        Text(l1, style = fdText(10f, color = ink, spacing = .14f, lineHeight = 14.5f), maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
                        Text(l2, style = fdText(10f, color = ink, spacing = .14f, lineHeight = 14.5f), maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
                    }
                }
                else -> {
                    Spacer(Modifier.weight(1f))
                    Text(doc.tail(), style = fdText(19f, color = ink, spacing = .06f))
                    Box(Modifier.padding(top = 8.dp)) { CardBottom(holder, until, doc.createdAt, open, ink) }
                }
            }
        }
        // `.wopen`: shown on the lifted card, bottom-right, over every template.
        androidx.compose.animation.AnimatedVisibility(open, Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 12.dp),
            enter = fadeIn(tween(250, 200)), exit = fadeOut(tween(120))) {
            Box(Modifier.clip(RoundedCornerShape(99.dp)).background(if (paper) hue.copy(alpha = .16f) else Color.White.copy(alpha = .22f)).padding(horizontal = 9.dp, vertical = 3.dp)) {
                Text("Tap again to open", style = fdText(10.5f, color = ink))
            }
        }
    }
}

/** `.wbot`: Holder on the left, Valid until / Added on the right (hidden under the "Tap again" pill while lifted). */
@Composable
private fun CardBottom(holder: String, until: String?, created: Long, open: Boolean, ink: Color) {
    val fade by animateFloatAsState(if (open) 0f else 1f, tween(150), label = "rightLabel")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
        CardLabel("HOLDER", holder.uppercase(), ink, Modifier.weight(1f, fill = false))
        CardLabel(if (until != null) "VALID UNTIL" else "ADDED", (until ?: fmtDate(created)).uppercase(), ink, Modifier.alpha(fade), end = true)
    }
}

@Composable
private fun CardLabel(label: String, value: String, ink: Color, modifier: Modifier = Modifier, end: Boolean = false) {
    Column(modifier, horizontalAlignment = if (end) Alignment.End else Alignment.Start) {
        Text(label, style = fdText(9f, FontWeight.Medium, ink.copy(alpha = .75f), spacing = .08f))
        Text(value, style = fdText(12f, FontWeight.SemiBold, ink, spacing = .03f), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** `.wphoto`: the holder's picture (photo, character, house for Common, or a person outline). */
@Composable
private fun CardPhoto(photoFile: String?, character: String?, shared: Boolean, modifier: Modifier) {
    val shape = RoundedCornerShape(10.dp)
    val photo = rememberVaultImage(photoFile, 500)
    val res = characterRes(character)
    Box(modifier.clip(shape).background(Color.White.copy(alpha = .16f)).border(1.dp, Color.White.copy(alpha = .28f), shape), contentAlignment = Alignment.Center) {
        when {
            photo != null -> Image(photo, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            res != null && !shared -> Image(painterResource(res), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else -> FdIcon(if (shared) "house" else "user", 30.dp, 1.5f, Color.White)
        }
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
 */
@Composable
fun FdWalletStack(
    docs: List<DocumentEntity>, holderFallback: String, expandedId: String?,
    onExpand: (id: String?, topPx: Float) -> Unit, onOpen: (DocumentEntity) -> Unit, modifier: Modifier = Modifier,
    owner: com.familydocs.app.data.MemberEntity? = null,
    onOpenBounds: (androidx.compose.ui.geometry.Rect) -> Unit = {},
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
                        .onGloballyPositioned { if (isOpen) onOpenBounds(it.boundsInRoot()) }
                        .tap { if (isOpen) onOpen(d) else onExpand(d.id, with(density) { tops[i].toPx() }) },
                    photoFile = owner?.photoFile, character = owner?.avatar, shared = owner?.shared == true,
                )
            }
        }
    }
}

