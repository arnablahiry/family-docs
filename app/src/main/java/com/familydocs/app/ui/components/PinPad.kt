package com.familydocs.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Passcode dots. Change [shakeKey] to trigger a shake (wrong code). */
@Composable
fun PinDots(length: Int, filled: Int, shakeKey: Int) {
    val shake = remember { Animatable(0f) }
    LaunchedEffect(shakeKey) {
        if (shakeKey > 0) shake.animateTo(0f, keyframes { durationMillis = 400; -16f at 60; 16f at 140; -12f at 220; 8f at 300; 0f at 400 })
    }
    Row(
        Modifier.offset { IntOffset(shake.value.dp.roundToPx(), 0) }.semantics { contentDescription = "$filled of $length digits entered" },
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        repeat(length) { i ->
            val on = i < filled
            val c by animateColorAsState(if (on) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent, label = "dot")
            Box(Modifier.size(13.dp).clip(CircleShape).background(c).border(1.5.dp, if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, CircleShape))
        }
    }
}

@Composable
fun PinPad(onDigit: (Char) -> Unit, onDelete: () -> Unit, onBiometric: (() -> Unit)? = null) {
    val haptic = LocalHapticFeedback.current
    val letters = listOf("", "ABC", "DEF", "GHI", "JKL", "MNO", "PQRS", "TUV", "WXYZ")
    val press: (Char) -> Unit = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onDigit(it) }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        for (row in 0 until 3) {
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                for (col in 1..3) {
                    val d = row * 3 + col
                    DigitKey(d, letters[d - 1]) { press('0' + d) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            if (onBiometric != null) Key(onBiometric, "Use fingerprint", flat = true) { Icon(Icons.Outlined.Fingerprint, null, Modifier.size(28.dp)) }
            else Spacer(Modifier.size(76.dp))
            DigitKey(0, "") { press('0') }
            Key(onDelete, "Delete", flat = true) { Icon(Icons.AutoMirrored.Outlined.Backspace, null, Modifier.size(24.dp)) }
        }
    }
}

/** Number on top, letters on their own fixed-height line so every key's numeral sits at the same height. */
@Composable
private fun DigitKey(d: Int, letters: String, onClick: () -> Unit) {
    Key(onClick, "$d") {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$d", fontSize = 30.sp, fontWeight = FontWeight.Light, lineHeight = 32.sp)
            Text(letters, fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp, lineHeight = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun Key(onClick: () -> Unit, label: String, flat: Boolean = false, content: @Composable () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, label = "keyScale")
    val base = MaterialTheme.colorScheme.background
    val ink = MaterialTheme.colorScheme.onBackground
    val bg = when {
        flat && pressed -> ink.copy(alpha = .08f).compositeOver(base)
        flat -> Color.Transparent
        pressed -> ink.copy(alpha = .20f).compositeOver(base)
        else -> ink.copy(alpha = .08f).compositeOver(base)
    }
    Box(
        Modifier.size(76.dp).scale(scale).clip(CircleShape).background(bg)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides if (flat) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onBackground) { content() }
    }
}
