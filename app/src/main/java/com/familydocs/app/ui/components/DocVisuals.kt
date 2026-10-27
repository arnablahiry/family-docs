package com.familydocs.app.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.House
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.HomeWork
import androidx.compose.material.icons.outlined.HowToVote
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Vaccines
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.familydocs.app.data.DisplayMode
import com.familydocs.app.data.DocumentEntity
import com.familydocs.app.domain.DocIcon
import com.familydocs.app.domain.DocTypes
import com.familydocs.app.domain.maskValue
import com.familydocs.app.ui.theme.LocalExtra
import com.familydocs.app.ui.theme.MonoFont
import java.text.DateFormat
import java.util.Date

fun DocIcon.vector(): ImageVector = when (this) {
    DocIcon.HOUSE -> Icons.Outlined.House
    DocIcon.BOLT -> Icons.Outlined.Bolt
    DocIcon.FINGERPRINT -> Icons.Outlined.Fingerprint
    DocIcon.CARD -> Icons.Outlined.CreditCard
    DocIcon.PASSPORT -> Icons.Outlined.Public
    DocIcon.PLANE -> Icons.Outlined.Flight
    DocIcon.RESIDENCE -> Icons.Outlined.HomeWork
    DocIcon.CAR -> Icons.Outlined.DirectionsCar
    DocIcon.BALLOT -> Icons.Outlined.HowToVote
    DocIcon.SHIELD -> Icons.Outlined.HealthAndSafety
    DocIcon.SYRINGE -> Icons.Outlined.Vaccines
    DocIcon.BANK -> Icons.Outlined.AccountBalance
    DocIcon.CAP -> Icons.Outlined.School
    DocIcon.CERTIFICATE -> Icons.Outlined.WorkspacePremium
    DocIcon.MEDICAL -> Icons.Outlined.MedicalServices
    DocIcon.RECEIPT -> Icons.Outlined.Receipt
    DocIcon.ID -> Icons.Outlined.Badge
    DocIcon.FILE -> Icons.Outlined.Description
    DocIcon.KEY -> Icons.Outlined.Key
}

/** Icon tint that stays readable in dark mode. */
@Composable
fun hueTint(hue: Color): Color = if (LocalExtra.current.dark) lerp(hue, MaterialTheme.colorScheme.onSurface, 0.38f) else hue

/** Rounded-square placeholder icon picked from the document name, or the chosen picture. */
@Composable
fun DocIconTile(name: String, modifier: Modifier = Modifier, size: Dp = 54.dp, picture: ImageBitmap? = null, iconOverride: DocIcon? = null, hueOverride: Long? = null) {
    val type = DocTypes.forName(name)
    val hue = Color(hueOverride ?: type.hue)
    val bg = hue.copy(alpha = if (LocalExtra.current.dark) 0.26f else 0.15f).compositeOver(MaterialTheme.colorScheme.surface)
    Box(modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(bg), contentAlignment = Alignment.Center) {
        if (picture != null) {
            Image(picture, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon((iconOverride ?: type.icon).vector(), null, tint = hueTint(hue), modifier = Modifier.size(size * 0.48f))
            if (!type.matched && type.initials.isNotEmpty() && iconOverride == null) {
                Text(
                    type.initials, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = MonoFont,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(5.dp).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(4.dp)).padding(horizontal = 3.dp),
                )
            }
        }
    }
}

@Composable
fun DocumentIcon(doc: DocumentEntity, size: Dp = 54.dp) {
    val pic = if (doc.display != DisplayMode.ICON) rememberVaultImage(doc.pictureFile, 400) else null
    DocIconTile(doc.name, size = size, picture = pic)
}

/** Short masked preview of the main number, e.g. "•••• 8237". */
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

// ---------------- Wallet cards ----------------

private const val CARD_RATIO = 1.586f

@Composable
fun WalletCard(doc: DocumentEntity, holderFallback: String, expanded: Boolean, modifier: Modifier = Modifier) {
    val type = DocTypes.forName(doc.name)
    val hue = Color(type.hue)
    val pic = if (doc.display != DisplayMode.ICON) rememberVaultImage(doc.pictureFile, 900) else null
    val dark = lerp(hue, Color(0xFF0B1020), 0.45f)
    Box(
        modifier
            .fillMaxWidth()
            .shadow(if (expanded) 18.dp else 6.dp, RoundedCornerShape(18.dp), ambientColor = dark, spotColor = dark)
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.linearGradient(listOf(lerp(hue, Color.White, 0.12f), hue, dark))),
    ) {
        if (pic != null) {
            Image(pic, null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(0f to dark.copy(alpha = .92f), 0.25f to Color(0x40050A19), 1f to Color(0xC8050A19))))
        }
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(type.icon.vector(), null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(9.dp))
                Text(doc.name, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(doc.tail(), color = Color.White.copy(alpha = .85f), fontFamily = MonoFont, fontSize = 11.sp)
                if (doc.issuer.isNotEmpty()) { Spacer(Modifier.width(8.dp)); Flag(doc.issuer, 24.dp, outline = Color.White.copy(alpha = .55f)) }
            }
            Spacer(Modifier.weight(1f))
            Text(doc.tail(), color = Color.White, fontFamily = MonoFont, fontSize = 19.sp, letterSpacing = 1.sp)
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                CardLabel("HOLDER", doc.holder(holderFallback).uppercase())
                AnimatedVisibility(expanded, enter = fadeIn(), exit = fadeOut()) {
                    Text("Tap again to open", color = Color.White, fontSize = 11.sp, modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = .22f)).padding(horizontal = 10.dp, vertical = 3.dp))
                }
                if (!expanded) {
                    val until = doc.validUntil()
                    CardLabel(if (until != null) "VALID UNTIL" else "ADDED", until ?: DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(doc.createdAt)), end = true)
                }
            }
        }
    }
}

@Composable
private fun CardLabel(label: String, value: String, end: Boolean = false) {
    Column(horizontalAlignment = if (end) Alignment.End else Alignment.Start) {
        Text(label, color = Color.White.copy(alpha = .75f), fontSize = 9.sp, letterSpacing = 1.sp)
        Text(value, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Stacked wallet. Cards overlap showing a 58 dp strip; tapping a card lifts it out whole and the cards below
 * spring away. Tapping the lifted card (or the bottom card, which is always whole) opens it.
 */
@Composable
fun WalletStack(
    docs: List<DocumentEntity>,
    holderFallback: String,
    expandedId: String?,
    onExpand: (id: String, topPx: Float) -> Unit,
    onOpen: (DocumentEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val cardH = maxWidth / CARD_RATIO
        val strip = 58.dp
        val gap = 16.dp
        val expandedIndex = docs.indexOfFirst { it.id == expandedId }
        val tops = ArrayList<Dp>(docs.size)
        docs.indices.forEach { i ->
            tops += if (i == 0) 0.dp else tops[i - 1] + if (expandedIndex == i - 1) cardH + gap else strip
        }
        val total = if (docs.isEmpty()) 0.dp else tops.last() + cardH
        val animatedTotal by animateDpAsState(total, spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow), label = "stackHeight")
        Box(Modifier.fillMaxWidth().height(animatedTotal)) {
            docs.forEachIndexed { i, d ->
                val top by animateDpAsState(tops[i], spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow), label = "cardTop")
                val lifted = i == expandedIndex
                val density = androidx.compose.ui.platform.LocalDensity.current
                WalletCard(
                    d, holderFallback, expanded = lifted,
                    modifier = Modifier
                        .zIndex(i.toFloat())
                        .offset(y = top)
                        .height(cardH)
                        .clickable {
                            if (lifted || i == docs.lastIndex) onOpen(d) else onExpand(d.id, with(density) { tops[i].toPx() })
                        },
                )
            }
        }
    }
}

/** Bordered surface used for list rows and tiles. */
fun Modifier.outlinedCard(color: Color, border: Color, radius: Dp = 18.dp) =
    this.clip(RoundedCornerShape(radius)).background(color).border(1.dp, border, RoundedCornerShape(radius))
