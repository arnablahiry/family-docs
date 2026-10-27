package com.familydocs.app.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.familydocs.app.domain.Countries

/**
 * Country flag for any ISO code, using the phone's emoji flags (every country, always up to date).
 * [width] is roughly the flag's width. Nothing is drawn for "".
 */
@Composable
fun Flag(code: String, width: Dp = 22.dp, @Suppress("UNUSED_PARAMETER") outline: Color = Color.Unspecified) {
    val emoji = Countries.flagEmoji(code)
    if (emoji.isEmpty()) return
    val size = with(LocalDensity.current) { (width * 0.8f).toSp() }
    Text(emoji, fontSize = size, lineHeight = size, fontFamily = FontFamily.Default, modifier = Modifier.semantics { contentDescription = Countries.name(code) })
}
