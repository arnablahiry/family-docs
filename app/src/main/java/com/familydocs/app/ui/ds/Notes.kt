package com.familydocs.app.ui.ds

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp

/**
 * WhatsApp-style formatting for notes: *bold*, _italic_, ~strikethrough~ and ```monospace``` (or `code`),
 * nestable (_*bold italic*_). Within one line, like WhatsApp: a marker opens before a non-space and closes after one.
 */
object NoteFormat {
    private const val MARKS = "*_~`"
    private const val EDGE = ".,;:!?()[]{}\"'-–—/"
    /** One formatted run: markers at [start, start+len) and [end, end+len); the text between them is styled. */
    data class Span(val start: Int, val end: Int, val mark: Char, val len: Int)

    fun spans(s: String): List<Span> = mutableListOf<Span>().also { scan(s, 0, s.length, it) }

    private fun boundary(c: Char?) = c == null || c.isWhitespace() || c in MARKS || c in EDGE

    private fun scan(s: String, from: Int, to: Int, out: MutableList<Span>) {
        var i = from
        while (i < to) {
            val m = s[i]
            val len = if (m == '`' && s.startsWith("```", i)) 3 else 1
            val first = i + len
            if (m in MARKS && boundary(s.getOrNull(i - 1)) && first < to && !s[first].isWhitespace() && s[first] != m) {
                var j = first + 1
                var found = -1
                while (j + len <= to && s[j] != '\n') {
                    if (s[j] == m && (len == 1 || s.startsWith("```", j)) && !s[j - 1].isWhitespace() && boundary(s.getOrNull(j + len))) { found = j; break }
                    j++
                }
                if (found > 0) {
                    out += Span(i, found, m, len)
                    scan(s, first, found, out)
                    i = found + len
                    continue
                }
            }
            i++
        }
    }

    private fun style(m: Char) = when (m) {
        '*' -> SpanStyle(fontWeight = FontWeight.Bold)
        '_' -> SpanStyle(fontStyle = FontStyle.Italic)
        '~' -> SpanStyle(textDecoration = TextDecoration.LineThrough)
        else -> SpanStyle(fontFamily = FontFamily.Monospace)
    }

    /** While you type: every character stays, the markers just fade, the text between them is styled. */
    fun editing(s: String, markColor: Color): AnnotatedString = AnnotatedString.Builder(s).apply {
        spans(s).forEach { sp ->
            addStyle(style(sp.mark), sp.start + sp.len, sp.end)
            addStyle(SpanStyle(color = markColor), sp.start, sp.start + sp.len)
            addStyle(SpanStyle(color = markColor), sp.end, sp.end + sp.len)
        }
    }.toAnnotatedString()

    /** Reading: the markers disappear. Also returns where each character went, for the cursor. */
    fun reading(s: String): TransformedText {
        val sp = spans(s)
        val hidden = BooleanArray(s.length)
        sp.forEach { x -> for (k in 0 until x.len) { hidden[x.start + k] = true; hidden[x.end + k] = true } }
        val o2t = IntArray(s.length + 1); val t2o = ArrayList<Int>(s.length + 1)
        val b = StringBuilder()
        for (i in s.indices) { o2t[i] = b.length; if (!hidden[i]) { t2o += i; b.append(s[i]) } }
        o2t[s.length] = b.length; t2o += s.length
        val out = AnnotatedString.Builder(b.toString())
        sp.forEach { x -> out.addStyle(style(x.mark), o2t[x.start + x.len], o2t[x.end]) }
        return TransformedText(out.toAnnotatedString(), object : OffsetMapping {
            override fun originalToTransformed(offset: Int) = o2t[offset.coerceIn(0, s.length)]
            override fun transformedToOriginal(offset: Int) = t2o[offset.coerceIn(0, t2o.lastIndex)]
        })
    }
}

/**
 * Notes box with formatting: styled as you type (markers faded), shown clean when you're not editing, and a small
 * toolbar while editing that wraps the selected text (or puts the markers either side of the cursor).
 */
@Composable
fun FdNotesField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier, placeholder: String = "") {
    val c = Fd.c
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val zones = LocalNoteZones.current
    val zoneKey = remember { Any() }
    var tv by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    LaunchedEffect(value) { if (value != tv.text) tv = TextFieldValue(value, TextRange(value.length)) }
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val border by animateColorAsState(if (focused) c.primary else c.line, tween(150), label = "notesBorder")
    val mark = c.muted.copy(alpha = .55f)
    val transform = remember(focused, mark) {
        if (focused) VisualTransformation { t -> TransformedText(NoteFormat.editing(t.text, mark), OffsetMapping.Identity) }
        else VisualTransformation { t -> NoteFormat.reading(t.text) }
    }
    // Typing ends (formatting shows, keys hide) with Back, closing the keyboard, or a tap anywhere else; Back then
    // doesn't also leave the page.
    androidx.activity.compose.BackHandler(enabled = focused) { focusManager.clearFocus() }
    @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
    val ime = androidx.compose.foundation.layout.WindowInsets.isImeVisible
    var hadKeyboard by remember { mutableStateOf(false) }
    LaunchedEffect(focused, ime) {
        when {
            !focused -> hadKeyboard = false
            ime -> hadKeyboard = true
            hadKeyboard -> { hadKeyboard = false; focusManager.clearFocus() }
        }
    }
    androidx.compose.runtime.DisposableEffect(zones) { onDispose { zones?.rects?.remove(zoneKey) } }
    LaunchedEffect(focused) { if (!focused) zones?.rects?.remove(zoneKey) }
        fun update(n: TextFieldValue) { tv = n; if (n.text != value) onValueChange(n.text) }
    fun wrap(m: String) {
        val a = tv.selection.min; val b = tv.selection.max
        val t = tv.text
        update(if (a == b) TextFieldValue(t.substring(0, a) + m + m + t.substring(a), TextRange(a + m.length))
               else TextFieldValue(t.substring(0, a) + m + t.substring(a, b) + m + t.substring(b), TextRange(a, b + m.length * 2)))
    }
    fun bullet() {
        val t = tv.text; val at = tv.selection.min
        val start = t.lastIndexOf('\n', (at - 1).coerceAtLeast(0)).let { if (at == 0 || it < 0) 0 else it + 1 }
        if (t.startsWith("• ", start)) update(TextFieldValue(t.removeRange(start, start + 2), TextRange((at - 2).coerceAtLeast(start))))
        else update(TextFieldValue(t.substring(0, start) + "• " + t.substring(start), TextRange(at + 2)))
    }
    Column(modifier.fillMaxWidth().onGloballyPositioned { if (focused) zones?.rects?.set(zoneKey, it.boundsInRoot()) else zones?.rects?.remove(zoneKey) }) {
        BasicTextField(
            value = tv, onValueChange = ::update, interactionSource = source, minLines = 3,
            textStyle = fdText(15f, color = c.fg), cursorBrush = SolidColor(c.primary), visualTransformation = transform,
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                Box(Modifier.fillMaxWidth().heightIn(min = 50.dp).clip(RoundedCornerShape(14.dp)).background(c.surface)
                    .border(1.5.dp, border, RoundedCornerShape(14.dp)).padding(12.dp)) {
                    if (tv.text.isEmpty()) Text(placeholder, style = fdText(15f, color = c.muted))
                    inner()
                }
            },
        )
        AnimatedVisibility(focused, enter = expandVertically(tween(180)) + fadeIn(tween(180)), exit = shrinkVertically(tween(150)) + fadeOut(tween(120))) {
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                FormatKey("B", SpanStyle(fontWeight = FontWeight.Bold), "Bold") { wrap("*") }
                FormatKey("I", SpanStyle(fontStyle = FontStyle.Italic), "Italic") { wrap("_") }
                FormatKey("S", SpanStyle(textDecoration = TextDecoration.LineThrough), "Strikethrough") { wrap("~") }
                FormatKey("{}", SpanStyle(fontFamily = FontFamily.Monospace), "Monospace") { wrap("```") }
                FormatKey("•", SpanStyle(fontWeight = FontWeight.Bold), "Bullet") { bullet() }
                Text("*bold*  _italic_  ~strike~", style = fdText(11f, color = c.muted), modifier = Modifier.weight(1f).padding(start = 4.dp), maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun FormatKey(label: String, style: SpanStyle, description: String, onClick: () -> Unit) {
    val c = Fd.c
    Box(Modifier.size(width = 40.dp, height = 34.dp).clip(RoundedCornerShape(10.dp)).background(c.surface2).border(1.dp, c.line, RoundedCornerShape(10.dp))
        .tap(onClick = onClick).semantics { contentDescription = description; role = Role.Button }, contentAlignment = Alignment.Center) {
        Text(AnnotatedString(label, style), style = fdText(15f, color = c.fg))
    }
}

/** Where focused notes fields are on screen (with their format keys), for [FdEndTypingOnTapOutside]. */
class NoteZones { val rects = androidx.compose.runtime.mutableStateMapOf<Any, androidx.compose.ui.geometry.Rect>() }
val LocalNoteZones = androidx.compose.runtime.staticCompositionLocalOf<NoteZones?> { null }

/**
 * Around the whole app: while a notes field is being typed in, a tap anywhere outside it (and its format keys) ends
 * typing. The tap still does whatever it does there (buttons work as usual); drags and scrolls don't count.
 */
@Composable
fun FdEndTypingOnTapOutside(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val zones = remember { NoteZones() }
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    var origin by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    Box(modifier.onGloballyPositioned { origin = it.positionInRoot() }.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = androidx.compose.ui.input.pointer.PointerEventPass.Initial)
            if (zones.rects.isEmpty()) return@awaitEachGesture
            var moved = false
            while (true) {
                val e = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                val ch = e.changes.firstOrNull { it.id == down.id } ?: break
                if ((ch.position - down.position).getDistance() > viewConfiguration.touchSlop) moved = true
                if (!ch.pressed) break
            }
            val at = down.position + origin
            if (!moved && zones.rects.values.none { it.contains(at) }) focusManager.clearFocus()
        }
    }) {
        androidx.compose.runtime.CompositionLocalProvider(LocalNoteZones provides zones) { content() }
    }
}
