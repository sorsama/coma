package com.zyphite.coma.ui

import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.isUnspecified
import androidx.compose.ui.unit.sp
import com.zyphite.coma.R
import kotlinx.coroutines.delay
import kotlin.math.floor

/** The palette: near-black ground, grey ink, and amber only where it is the user's turn. */
object C {
    val Bg = Color(0xFF0A0A0B)
    val Backdrop = Color(0xFF050506)
    val Surface = Color(0xFF111113)
    val Surface2 = Color(0xFF141417)
    val Raised = Color(0xFF1A1A1E)
    val Chip = Color(0xFF1C1C20)
    val Thumb = Color(0xFF2A2A30)
    val Hair = Color(0xFF17171A)
    val Line = Color(0xFF1C1C20)
    val Border = Color(0xFF232327)
    val Border2 = Color(0xFF26262B)
    val Border3 = Color(0xFF2E2E34)
    val BorderStrong = Color(0xFF3A3A40)
    val Mute = Color(0xFF55555C)
    val Dim = Color(0xFF8B8B94)
    val Sub = Color(0xFFA0A0A8)
    val Text = Color(0xFFEDEDEF)
    val Amber = Color(0xFFFFB224)
    val AmberBorder = Color(0xFF5C4310)
    val AmberBg = Color(0xFF14110A)
    val AmberTrack = Color(0xFF3A2C0C)
}

val W5 = FontWeight.Medium
val W6 = FontWeight.SemiBold

@OptIn(ExperimentalTextApi::class)
private fun variable(res: Int, weight: Int) =
    Font(res, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

val Geist = FontFamily(variable(R.font.geist, 400), variable(R.font.geist, 500), variable(R.font.geist, 600))
val GeistMono = FontFamily(variable(R.font.geist_mono, 400), variable(R.font.geist_mono, 500))

/** Enter 400ms, spring 340ms and the sheet curve, as named in the design's signal language. */
val EnterEase = CubicBezierEasing(.2f, .8f, .2f, 1f)
val SpringEase = CubicBezierEasing(.34f, 1.56f, .64f, 1f)
val SheetEase = CubicBezierEasing(.2f, .9f, .25f, 1f)
val HopEase = CubicBezierEasing(.6f, 0f, .3f, 1f)

/** Seconds since the app started drawing. Read it in draw or layer blocks only, so a tick redraws without recomposing. */
val LocalClock = staticCompositionLocalOf<FloatState> { mutableFloatStateOf(0f) }

/** True when the system's animations are switched off; every looping motion stands still. */
val LocalReduceMotion = staticCompositionLocalOf { false }

fun frac(x: Float): Float = x - floor(x)
fun mix(a: Float, b: Float, t: Float): Float = a + (b - a) * t

@Composable
fun ComaTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val reduce = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val clock = remember { mutableFloatStateOf(0f) }
    if (!reduce) {
        LaunchedEffect(Unit) {
            val start = withFrameNanos { it }
            while (true) withFrameNanos { clock.floatValue = (it - start) / 1e9f }
        }
    }
    CompositionLocalProvider(LocalClock provides clock, LocalReduceMotion provides reduce, content = content)
}

fun ts(
    size: TextUnit = 15.sp,
    color: Color = C.Text,
    weight: FontWeight = FontWeight.Normal,
    mono: Boolean = false,
    align: TextAlign = TextAlign.Start,
    spacing: TextUnit = TextUnit.Unspecified,
    lineHeight: TextUnit = TextUnit.Unspecified,
    decoration: TextDecoration? = null,
) = TextStyle(
    color = color,
    fontSize = size,
    fontWeight = weight,
    fontFamily = if (mono) GeistMono else Geist,
    textAlign = align,
    letterSpacing = spacing,
    lineHeight = if (lineHeight.isUnspecified) (size.value * 1.35f).sp else lineHeight,
    textDecoration = decoration,
)

@Composable
fun T(
    text: String,
    modifier: Modifier = Modifier,
    size: TextUnit = 15.sp,
    color: Color = C.Text,
    weight: FontWeight = FontWeight.Normal,
    mono: Boolean = false,
    maxLines: Int = Int.MAX_VALUE,
    align: TextAlign = TextAlign.Start,
    spacing: TextUnit = TextUnit.Unspecified,
    lineHeight: TextUnit = TextUnit.Unspecified,
    decoration: TextDecoration? = null,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = ts(size, color, weight, mono, align, spacing, lineHeight, decoration),
        maxLines = maxLines,
        overflow = if (maxLines == Int.MAX_VALUE) TextOverflow.Clip else TextOverflow.Ellipsis,
    )
}

/** A tap target that shrinks a little while pressed. */
fun Modifier.press(scale: Float = .95f, role: Role? = Role.Button, onClick: () -> Unit): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) scale else 1f, tween(120, easing = EnterEase), label = "press")
    graphicsLayer { scaleX = s; scaleY = s }.clickable(source, null, role = role, onClick = onClick)
}

/** A full-width row that darkens while pressed. */
fun Modifier.rowPress(pressed: Color = C.Surface2, role: Role? = Role.Button, onClick: () -> Unit): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val down by source.collectIsPressedAsState()
    val bg by animateColorAsState(if (down) pressed else Color.Transparent, tween(150), label = "row")
    background(bg).clickable(source, null, role = role, onClick = onClick)
}

fun Modifier.topLine(color: Color = C.Hair): Modifier = drawBehind {
    val y = .5.dp.toPx()
    drawLine(color, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
}

fun Modifier.bottomLine(color: Color = C.Hair): Modifier = drawBehind {
    val y = size.height - .5.dp.toPx()
    drawLine(color, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
}

fun Modifier.card(bg: Color = C.Surface, border: Color = C.Border, radius: Dp = 12.dp): Modifier {
    val shape = RoundedCornerShape(radius)
    return clip(shape).background(bg).border(1.dp, border, shape)
}

fun Modifier.dashedBorder(color: Color, radius: Dp): Modifier = drawBehind {
    drawRoundRect(
        color,
        cornerRadius = CornerRadius(radius.toPx()),
        style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
    )
}

/** Fades and slides content in once, when it first appears. */
fun Modifier.enter(delayMs: Int = 0, dy: Dp = 8.dp, dx: Dp = 0.dp, ms: Int = 400): Modifier = composed {
    val reduce = LocalReduceMotion.current
    val a = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!reduce) {
            delay(delayMs.toLong())
            a.animateTo(1f, tween(ms, easing = EnterEase))
        }
    }
    graphicsLayer {
        alpha = a.value
        translationY = (1f - a.value) * dy.toPx()
        translationX = (1f - a.value) * dx.toPx()
    }
}

/** Grows a bar from its left edge once, when it first appears. */
fun Modifier.growX(delayMs: Int = 0, ms: Int = 500): Modifier = composed {
    val reduce = LocalReduceMotion.current
    val a = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!reduce) {
            delay(delayMs.toLong())
            a.animateTo(1f, tween(ms, easing = EnterEase))
        }
    }
    graphicsLayer { scaleX = a.value; transformOrigin = TransformOrigin(0f, .5f) }
}

@Composable
fun Ico(icon: ImageVector, dp: Dp, tint: Color = C.Text, modifier: Modifier = Modifier, desc: String? = null) {
    Image(icon, desc, modifier.size(dp), colorFilter = ColorFilter.tint(tint))
}

@Composable
fun IconBtn(
    icon: ImageVector,
    desc: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    box: Dp = 44.dp,
    dp: Dp = 22.dp,
    tint: Color = C.Text,
) {
    Box(modifier.size(box).press(onClick = onClick), Alignment.Center) { Ico(icon, dp, tint, desc = desc) }
}

@Composable
fun ScreenHeader(
    title: String,
    signal: Signal,
    signalLabel: String,
    endPad: Dp = 20.dp,
    inset: Dp = 0.dp,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding()
            .padding(start = 20.dp, top = 10.dp, end = endPad, bottom = 8.dp).heightIn(min = 44.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Wing(signal, 18.dp, Modifier.padding(start = inset), label = signalLabel)
        T(title, Modifier.weight(1f).padding(start = inset), size = 22.sp, weight = W6, spacing = (-0.02).em, maxLines = 1)
        trailing()
    }
}

@Composable
fun BackHeader(title: String, backLabel: String, onBack: () -> Unit, trailing: String? = null) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(start = 8.dp, top = 10.dp, end = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        IconBtn(Icons.ChevronLeft, backLabel, onBack)
        T(title, Modifier.weight(1f), size = 22.sp, weight = W6, spacing = (-0.02).em, maxLines = 1)
        if (trailing != null) T(trailing, size = 12.sp, color = C.Sub, mono = true)
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    T(text, modifier, size = 13.sp, weight = W5, color = C.Dim)
}

/** Segmented control whose thumb overshoots a little, so a tap feels like it landed. */
@Composable
fun Segmented(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, bg: Color = C.Surface) {
    val shape = RoundedCornerShape(14.dp)
    BoxWithConstraints(
        modifier.fillMaxWidth().height(52.dp).clip(shape).background(bg).border(1.dp, C.Border, shape).padding(4.dp),
    ) {
        val cell = maxWidth / labels.size
        val x by animateDpAsState(cell * selected, tween(360, easing = SpringEase), label = "thumb")
        Box(
            Modifier.offset { IntOffset(x.roundToPx(), 0) }.width(cell).fillMaxHeight()
                .clip(RoundedCornerShape(10.dp)).background(C.Thumb),
        )
        Row(Modifier.fillMaxSize()) {
            labels.forEachIndexed { index, label ->
                val color by animateColorAsState(if (index == selected) C.Text else C.Sub, tween(200), label = "seg")
                Box(
                    Modifier.weight(1f).fillMaxHeight()
                        .clickable(remember { MutableInteractionSource() }, null, role = Role.Tab) { onSelect(index) },
                    Alignment.Center,
                ) { T(label, size = 13.sp, weight = W5, color = color, maxLines = 1) }
            }
        }
    }
}

@Composable
fun Toggle(on: Boolean, modifier: Modifier = Modifier) {
    val x by animateDpAsState(if (on) 18.dp else 0.dp, tween(300, easing = SpringEase), label = "knob")
    val track by animateColorAsState(if (on) C.Text else C.Thumb, tween(200), label = "track")
    val knob by animateColorAsState(if (on) C.Bg else C.Sub, tween(200), label = "knobColor")
    Box(modifier.size(46.dp, 28.dp).clip(RoundedCornerShape(14.dp)).background(track).padding(3.dp)) {
        Box(Modifier.offset { IntOffset(x.roundToPx(), 0) }.size(22.dp).clip(CircleShape).background(knob))
    }
}

@Composable
fun Radio(on: Boolean) {
    val s by animateFloatAsState(if (on) 1f else 0f, tween(300, easing = SpringEase), label = "dot")
    Box(Modifier.size(22.dp).border(1.5.dp, if (on) C.Text else C.Mute, CircleShape), Alignment.Center) {
        Box(Modifier.size(10.dp).graphicsLayer { scaleX = s; scaleY = s }.clip(CircleShape).background(C.Text))
    }
}

@Composable
fun Chip(label: String, on: Boolean, onClick: () -> Unit, pill: Dp = 32.dp, pad: Dp = 12.dp, off: Color = C.Border2) {
    val bg by animateColorAsState(if (on) C.Text else Color.Transparent, tween(180), label = "chipBg")
    val fg by animateColorAsState(if (on) C.Bg else C.Sub, tween(180), label = "chipFg")
    val shape = RoundedCornerShape(pill / 2)
    Box(Modifier.height(44.dp).press(onClick = onClick), Alignment.Center) {
        Box(
            Modifier.height(pill).clip(shape).background(bg).border(1.dp, if (on) C.Text else off, shape)
                .padding(horizontal = pad),
            Alignment.Center,
        ) { T(label, size = 13.sp, weight = W5, color = fg, maxLines = 1) }
    }
}

/** A settings-style row: label, optional value, chevron. */
@Composable
fun ChevronRow(label: String, value: String? = null, minHeight: Dp = 52.dp, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier.fillMaxWidth().topLine().rowPress(onClick = onClick).heightIn(min = minHeight).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        T(label, Modifier.weight(1f), weight = W5)
        if (value != null) T(value, size = 12.sp, color = C.Sub, mono = true)
        Ico(Icons.ChevronRight, 18.dp, C.Dim)
    }
}

/** The moving dashed edge under any card with live work. */
@Composable
fun MarchLine(modifier: Modifier = Modifier) {
    val clock = LocalClock.current
    Canvas(modifier.fillMaxWidth().height(1.dp)) {
        val dash = 4.dp.toPx()
        val phase = (1f - frac(clock.floatValue * 2f)) * dash * 2
        val y = size.height / 2
        drawLine(
            C.Text, Offset(0f, y), Offset(size.width, y), size.height,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash), phase),
        )
    }
}

/** A message route: a line with a dot travelling along it while it is live. */
@Composable
fun Lane(
    modifier: Modifier = Modifier,
    live: Boolean = true,
    dashed: Boolean = true,
    color: Color = C.Mute,
    period: Float = 1.8f,
    delay: Float = 0f,
) {
    val clock = LocalClock.current
    val reduce = LocalReduceMotion.current
    Canvas(modifier.height(7.dp)) {
        val y = size.height / 2
        val dash = 4.dp.toPx()
        drawLine(
            color, Offset(0f, y), Offset(size.width, y), 1.dp.toPx(),
            pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(dash, dash)) else null,
        )
        val t = clock.floatValue - delay
        if (live && !reduce && t >= 0f) {
            val p = frac(t / period)
            val r = 3.5.dp.toPx()
            val edge = when {
                p < .15f -> p / .15f
                p > .85f -> (1f - p) / .15f
                else -> 1f
            }
            drawCircle(C.Text, r, Offset(r + (size.width - 2 * r) * HopEase.transform(p), y), alpha = edge)
        }
    }
}

@Composable
fun Field(
    value: String,
    onValue: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    style: TextStyle = ts(),
    placeholderColor: Color = C.Dim,
    keyboard: KeyboardOptions = KeyboardOptions.Default,
    desc: String? = null,
    onDone: (() -> Unit)? = null,
) {
    BasicTextField(
        value = value,
        onValueChange = onValue,
        modifier = modifier.semantics { if (desc != null) contentDescription = desc },
        textStyle = style,
        singleLine = true,
        cursorBrush = SolidColor(C.Text),
        keyboardOptions = keyboard,
        keyboardActions = KeyboardActions(onAny = { onDone?.invoke() }),
        decorationBox = { inner ->
            Box(Modifier.fillMaxWidth(), Alignment.CenterStart) {
                if (value.isEmpty()) {
                    BasicText(placeholder, Modifier.fillMaxWidth(), style = style.copy(color = placeholderColor), maxLines = 1)
                }
                inner()
            }
        },
    )
}

/** A sheet that rises from the bottom edge over a scrim. */
@Composable
fun Sheet(visible: Boolean, onClose: () -> Unit, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
    BackHandler(visible, onClose)
    AnimatedVisibility(visible, enter = fadeIn(tween(250)), exit = fadeOut(tween(200))) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier.fillMaxSize().background(C.Backdrop.copy(alpha = .72f))
                    .clickable(remember { MutableInteractionSource() }, null, onClickLabel = "Close", onClick = onClose),
            )
            Column(
                modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .animateEnterExit(
                        enter = slideInVertically(tween(450, easing = SheetEase)) { it / 6 },
                        exit = slideOutVertically(tween(200)) { it / 6 },
                    )
                    .clip(shape).background(C.Surface).border(1.dp, C.Border, shape)
                    .pointerInput(Unit) { detectTapGestures { } }
                    .navigationBarsPadding(),
                content = content,
            )
        }
    }
}
