package com.auracharge

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.repeatOnLifecycle
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun ChargingScreen(model: ChargeViewModel, battery: BatteryState, screenOn: Boolean, onClose: () -> Unit) {
    val context = LocalContext.current
    val activity = context as ComponentActivity
    val power = remember { context.getSystemService(PowerManager::class.java) }
    val player = remember { AmbientPlayer(context) }
    val lowEnd = remember { Build.VERSION.SDK_INT < 29 || context.getSystemService(ActivityManager::class.java).isLowRamDevice }
    val reduced = remember { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
    var elapsed by remember { mutableLongStateOf(if (reduced) 3000L else 0L) }
    val entries = remember { model.entries().let { if (model.get("shuffle", false)) it.shuffled() else it } }
    val initialIndex = remember { if (entries.isEmpty() || model.get("shuffle", false)) 0 else (model.get("position", "0").toIntOrNull() ?: 0) % entries.size }
    var entryIndex by remember { mutableIntStateOf(initialIndex) }
    val firstCategory = entries.getOrNull(initialIndex)?.category ?: "custom"
    val trackCounts = remember { mutableMapOf(firstCategory to 1) }
    var activeTrack by remember { mutableStateOf(resolveTrack("category", firstCategory, 0)) }
    var lastEntryIndex by remember { mutableIntStateOf(initialIndex) }
    val interval = (22000 / model.get("speed", 1f)).toLong()
    fun play() {
        if (screenOn && power.isInteractive && model.get("music", true)) {
            val selected = model.get("track", "category")
            val track = if (selected == "category") activeTrack else selected
            player.start(track, model.get("volume", .35f), model.get("silent", false), model.get("audio_uri", ""))
        }
    }
    LaunchedEffect(screenOn, reduced) {
        if (screenOn) {
            activity.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                var last = SystemClock.elapsedRealtime()
                var wasInteractive = power.isInteractive
                while (true) {
                    delay(if (!wasInteractive) 500L else if (lowEnd) 50L else 16L)
                    val now = SystemClock.elapsedRealtime()
                    val interactive = power.isInteractive
                    if (!interactive && wasInteractive) player.stop()
                    if (interactive && !wasInteractive) play()
                    if (interactive && wasInteractive) elapsed += now - last
                    wasInteractive = interactive
                    last = now
                    if (elapsed >= 3000 && entries.isNotEmpty()) entryIndex = ((initialIndex + (elapsed - 3000) / interval) % entries.size).toInt()
                }
            }
        }
    }
    LaunchedEffect(screenOn) {
        if (screenOn && !reduced && elapsed < 2700) {
            delay(2700)
            if (power.isInteractive && activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) && model.get("haptics", true)) context.getSystemService(Vibrator::class.java)?.vibrate(VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }
    DisposableEffect(screenOn) {
        var startedByObserver = false
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) player.stop()
            if (event == Lifecycle.Event.ON_START) { startedByObserver = true; play() }
        }
        activity.lifecycle.addObserver(observer)
        if (!startedByObserver && activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) play()
        onDispose { activity.lifecycle.removeObserver(observer); player.stop(fade = screenOn); if (entries.isNotEmpty() && !model.get("shuffle", false)) model.put("position", ((entryIndex + 1) % entries.size).toString()) }
    }
    LaunchedEffect(entryIndex) {
        if (entryIndex != lastEntryIndex) {
            lastEntryIndex = entryIndex
            val category = entries.getOrNull(entryIndex)?.category ?: "custom"
            val count = trackCounts[category] ?: 0
            activeTrack = resolveTrack("category", category, count)
            trackCounts[category] = count + 1
            if (model.get("track", "category") == "category" && activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) play()
        }
    }
    if (!screenOn) { Box(Modifier.fillMaxSize().background(Color.Black)); return }
    val dock by animateFloatAsState(if (elapsed >= 3000 || reduced) 1f else 0f, spring(stiffness = 150f), label = "dock")
    val levelColor = when { battery.level < 25 -> Color(0xffff8864); battery.level < 70 -> Color(0xffffc46b); else -> Color(0xff7bd9ba) }
    val status = stringResource(battery.statusResource)
    var drag by remember { mutableFloatStateOf(0f) }
    val hint = stringResource(R.string.close_hint)
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black).semantics { contentDescription = hint }.pointerInput(Unit) { detectTapGestures(onLongPress = { onClose() }) }.pointerInput(Unit) { detectDragGestures(onDragEnd = { if (drag > 100) onClose(); drag = 0f }) { _, amount -> drag += amount.y } }) {
        TextButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd)) { Text(stringResource(R.string.close), color = Color.Gray) }
        TextButton(onClick = { elapsed = if (elapsed < 3000) 3000 + interval else elapsed + interval - (elapsed - 3000) % interval }, modifier = Modifier.align(Alignment.TopStart), enabled = entries.isNotEmpty()) { Text(stringResource(R.string.next), color = Color.LightGray) }
        val density = LocalDensity.current
        val heightPx = with(density) { maxHeight.toPx() }
        val widthPx = with(density) { maxWidth.toPx() }
        val centerY = heightPx * (.46f + .39f * dock)
        val dockShift = if (dock > .9f) ((elapsed / 60000 % 3).toInt() - 1) else 0
        val radius = with(density) { (92f - 67f * dock).dp.toPx() }
        val style = model.get("style", "ring")
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(widthPx / 2 + dockShift, centerY)
            if (dock < .95f && !reduced) {
                val count = if (lowEnd) 12 else 26
                for (index in 0 until count) {
                    val angle = 6.283185 * index / count
                    val distance = radius + (1f - (elapsed / 3000f).coerceIn(0f, 1f)) * widthPx * .5f
                    val start = Offset(center.x + cos(angle).toFloat() * distance, center.y + sin(angle).toFloat() * distance)
                    val end = Offset(center.x + cos(angle).toFloat() * radius, center.y + sin(angle).toFloat() * radius)
                    val tint = when (style) { "aurora" -> Color(0xff70b7a6); "lightning" -> Color(0xffe7c77b); else -> levelColor }
                    drawLine(tint.copy(alpha = .3f * (1f - dock)), start, end, if (style == "aurora") 3f else 1f)
                }
            }
            drawCircle(levelColor.copy(alpha = .14f), radius, center, style = Stroke(width = 3.dp.toPx()))
            drawArc(levelColor, -90f, 360f * (if (dock < .5f) (elapsed / 3000f).coerceIn(0f, 1f) else battery.level / 100f), false, Offset(center.x - radius, center.y - radius), Size(radius * 2, radius * 2), style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
            if (dock < .5f) {
                val angle = elapsed / 400f
                drawCircle(levelColor, 3.dp.toPx(), Offset(center.x + cos(angle).toFloat() * radius, center.y + sin(angle).toFloat() * radius))
                if (elapsed >= 2600) {
                    val unit = 1.dp.toPx()
                    val bolt = Path().apply {
                        moveTo(center.x + 2 * unit, center.y - radius - 9 * unit)
                        lineTo(center.x - 5 * unit, center.y - radius + unit)
                        lineTo(center.x, center.y - radius + unit)
                        lineTo(center.x - 2 * unit, center.y - radius + 9 * unit)
                        lineTo(center.x + 6 * unit, center.y - radius - 2 * unit)
                        lineTo(center.x + unit, center.y - radius - 2 * unit)
                        close()
                    }
                    drawPath(bolt, levelColor)
                }
            }
        }
        val shownLevel = if (elapsed < 3000 && !reduced) (battery.level * (elapsed / 3000f)).toInt() else battery.level
        Column(Modifier.offset { IntOffset(dockShift, (centerY - with(density) { (if (dock < .5f) 33.dp else 17.dp).toPx() }).toInt()) }.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$shownLevel%", color = Color.White, fontSize = (46 - dock * 27).sp)
            if (dock < .5f) Text(status, color = levelColor, fontSize = 14.sp)
        }
        if (dock > .8f) {
            val remaining = if (battery.remainingMs > 0) stringResource(R.string.hours_minutes, battery.remainingMs / 3_600_000, (battery.remainingMs / 60_000) % 60) else stringResource(R.string.unknown)
            val watts = kotlin.math.abs(battery.currentMa.toLong() * battery.voltageMv) / 1_000_000.0
            Column(Modifier.align(Alignment.BottomCenter).offset { IntOffset(dockShift, 0) }.padding(bottom = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(status, color = levelColor, fontSize = 13.sp)
                Text(stringResource(R.string.remaining, remaining), color = Color.LightGray, fontSize = 11.sp)
                Text("${battery.currentMa} mA · ${"%.1f".format(watts)} W · ${"%.1f".format(battery.temperature)} °C", color = Color.Gray, fontSize = 11.sp)
            }
        }
        if (elapsed >= 3000 && dock > .6f) {
            val progress = if (reduced) .5f else ((elapsed - 3000) % interval).toFloat() / interval
            val position = (.74f - .68f * progress) * heightPx
            val alpha = (if (reduced) 1f else minOf(1f, progress * 6f, (1f - progress) * 6f).coerceIn(0f, 1f)) * if (model.get("dim", false)) .55f else 1f
            val entry = entries.getOrNull(entryIndex)
            val length = (entry?.fa?.length ?: 0) + (entry?.tr?.length ?: 0)
            val maximumSize = if (length > 500) 16f else if (length > 300) 20f else 34f
            val size = textScale(progress, 14f, maximumSize) * model.get("size", 1f)
            val tint = when (model.get("color", "cream")) { "mint" -> Color(0xffb4e4d2); "gold" -> Color(0xffe7c77b); else -> Color(0xfff0e8d7) }
            val family = if (model.get("font", "sans") == "serif") FontFamily.Serif else FontFamily(Font(R.font.vazirmatn))
            Column(Modifier.offset { IntOffset(0, position.toInt()) }.fillMaxWidth().padding(horizontal = 24.dp).alpha(alpha), horizontalAlignment = Alignment.CenterHorizontally) {
                if (entry == null) Text(stringResource(R.string.no_text), color = tint, textAlign = TextAlign.Center)
                else {
                    val display = model.get("display", "both")
                    if (display != "tr") Text(entry.fa, style = TextStyle(textDirection = TextDirection.Content), textAlign = TextAlign.Center, fontFamily = family, fontSize = size.sp, lineHeight = (size * 1.5f).sp, color = tint)
                    if (display != "fa" && (entry.category != "custom" || display == "tr")) Text(entry.tr, textAlign = TextAlign.Center, fontFamily = family, fontSize = (size * .65f).sp, lineHeight = (size * .95f).sp, color = tint.copy(alpha = .8f))
                    if (entry.author.isNotEmpty()) Text("${entry.author} · ${entry.source}", textAlign = TextAlign.Center, color = tint.copy(alpha = .65f), fontSize = 11.sp)
                }
            }
        }
    }
}
