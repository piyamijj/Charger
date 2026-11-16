package com.auracharge

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val model: ChargeViewModel by viewModels()
    private var battery by mutableStateOf(BatteryState())
    private var chargeScreen by mutableStateOf(false)
    private var preview by mutableStateOf(false)
    private var screenOn by mutableStateOf(true)
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_BATTERY_CHANGED -> {
                    battery = BatteryState.read(context, intent)
                    if (!battery.connected && !preview) chargeScreen = false
                }
                Intent.ACTION_SCREEN_OFF -> screenOn = false
                Intent.ACTION_SCREEN_ON -> screenOn = true
            }
        }
    }
    override fun attachBaseContext(base: Context) {
        val language = base.getSharedPreferences("aura", Context.MODE_PRIVATE).getString("language", "tr") ?: "tr"
        val locale = Locale.forLanguageTag(language)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration).apply { setLocale(locale); setLayoutDirection(locale) }
        super.attachBaseContext(base.createConfigurationContext(config))
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sticky = registerReceiver(receiver, IntentFilter().apply { addAction(Intent.ACTION_BATTERY_CHANGED); addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON) })
        sticky?.let { battery = BatteryState.read(this, it) }
        chargeScreen = battery.connected || (intent.getBooleanExtra("charging_screen", false) && battery.connected)
        setLockScreenMode(chargeScreen)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                LaunchedEffect(chargeScreen) {
                    setLockScreenMode(chargeScreen)
                    val insets = WindowCompat.getInsetsController(window, window.decorView)
                    if (chargeScreen) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        insets.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                        insets.hide(WindowInsetsCompat.Type.systemBars())
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        insets.show(WindowInsetsCompat.Type.systemBars())
                    }
                }
                if (chargeScreen) ChargingScreen(model, battery, screenOn) { chargeScreen = false; preview = false }
                else SettingsScreen(model, onPreview = { preview = true; chargeScreen = true }, onMonitor = { enabled ->
                    if (enabled) {
                        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        runCatching { ContextCompat.startForegroundService(this, Intent(this, ChargingService::class.java)) }
                    } else stopService(Intent(this, ChargingService::class.java))
                }, onLanguage = { recreate() })
            }
        }
    }
    @Suppress("DEPRECATION")
    private fun setLockScreenMode(enabled: Boolean) {
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(enabled)
            setTurnScreenOn(enabled)
        } else {
            val flags = WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            if (enabled) window.addFlags(flags) else window.clearFlags(flags)
        }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); if (intent.getBooleanExtra("charging_screen", false) && battery.connected) { setLockScreenMode(true); preview = false; chargeScreen = true } }
    override fun onStart() { super.onStart(); screenOn = getSystemService(android.os.PowerManager::class.java).isInteractive }
    override fun onStop() { screenOn = false; super.onStop() }
    override fun onDestroy() { unregisterReceiver(receiver); super.onDestroy() }
}

@Composable
private fun Choices(label: String, current: String, options: List<Pair<String, String>>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Box {
            OutlinedButton(onClick = { expanded = true }) { Text(options.firstOrNull { it.first == current }?.second ?: current) }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { (value, title) -> DropdownMenuItem(text = { Text(title) }, onClick = { expanded = false; onSelect(value) }) }
            }
        }
    }
}

@Composable
fun SettingsScreen(model: ChargeViewModel, onPreview: () -> Unit, onMonitor: (Boolean) -> Unit, onLanguage: () -> Unit) {
    val revision by model.revision.collectAsState()
    val selected = remember(revision) { model.selectedCategories() }
    val context = LocalContext.current
    val audioPreview = remember { AmbientPlayer(context) }
    var testingAudio by remember { mutableStateOf(false) }
    var showLibrary by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { audioPreview.stop() } }
    DisposableEffect(context) {
        val activity = context as ComponentActivity
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) { audioPreview.stop(); testingAudio = false }
        }
        activity.lifecycle.addObserver(observer)
        onDispose { activity.lifecycle.removeObserver(observer) }
    }
    var editing by remember { mutableIntStateOf(-2) }
    var draft by remember { mutableStateOf("") }
    val txtPicker = androidx.activity.compose.rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = runCatching { context.contentResolver.openInputStream(uri)?.use { stream ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = stream.read(buffer)
                    if (count < 0) break
                    require(output.size() + count <= 1_000_000)
                    output.write(buffer, 0, count)
                }
                output.toString("UTF-8")
            } }.getOrNull()
            if (!text.isNullOrBlank()) { model.texts.add(text); model.saveTexts() } else android.widget.Toast.makeText(context, R.string.import_error, android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    val audioPicker = androidx.activity.compose.rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                .onSuccess { model.put("audio_uri", uri.toString()); model.put("track", "custom") }
                .onFailure { android.widget.Toast.makeText(context, R.string.audio_error, android.widget.Toast.LENGTH_SHORT).show() }
        }
    }
    if (editing != -2) AlertDialog(onDismissRequest = { editing = -2 }, title = { Text(stringResource(R.string.text_entry)) }, text = { OutlinedTextField(value = draft, onValueChange = { draft = it }, label = { Text(stringResource(R.string.text_hint)) }, modifier = Modifier.fillMaxWidth(), minLines = 4) }, confirmButton = { TextButton(onClick = { if (draft.isNotBlank()) { if (editing == -1) model.texts.add(draft) else model.texts[editing] = draft; model.saveTexts() }; editing = -2 }) { Text(stringResource(R.string.save)) } }, dismissButton = { TextButton(onClick = { editing = -2 }) { Text(stringResource(R.string.cancel)) } })
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.settings), style = MaterialTheme.typography.titleMedium)
        Button(onClick = onPreview) { Text(stringResource(R.string.preview)) }
        Choices(stringResource(R.string.language), model.get("language", "tr"), listOf("tr" to "Türkçe", "en" to "English", "fa" to "فارسی")) { model.put("language", it); onLanguage() }
        Toggle(stringResource(R.string.monitor), model.get("monitor", false)) { model.put("monitor", it); onMonitor(it) }
        Text(stringResource(R.string.monitor_help), style = MaterialTheme.typography.bodySmall)
        Toggle(stringResource(R.string.lock_only), model.get("lock_only", false)) { model.put("lock_only", it) }
        Text(stringResource(R.string.category), style = MaterialTheme.typography.titleMedium)
        listOf("poetry" to stringResource(R.string.poetry), "story" to stringResource(R.string.story), "wisdom" to stringResource(R.string.wisdom), "spiritual" to stringResource(R.string.spiritual), "custom" to stringResource(R.string.mine)).forEach { (category, label) ->
            val count = if (category == "custom") model.texts.size else model.library.count { it.category == category }
            Toggle("$label ($count)", category in selected) { checked ->
                model.put("categories", (if (checked) selected + category else selected - category).sorted().joinToString(","))
                model.put("position", "0")
            }
        }
        val availableEntries = remember(revision) { model.entries() }
        OutlinedButton(onClick = { showLibrary = !showLibrary }) { Text(stringResource(if (showLibrary) R.string.hide_entries else R.string.show_entries, availableEntries.size)) }
        if (showLibrary) availableEntries.forEachIndexed { index, entry ->
            TextButton(onClick = { model.put("shuffle", false); model.put("position", index.toString()); onPreview() }) {
                Text("${index + 1}. ${entry.fa.take(80)}\n${entry.tr.take(100)} · ${entry.author}", maxLines = 3)
            }
        }
        Choices(stringResource(R.string.display), model.get("display", "both"), listOf("both" to stringResource(R.string.both), "fa" to stringResource(R.string.persian), "tr" to stringResource(R.string.turkish))) { model.put("display", it) }
        Toggle(stringResource(R.string.shuffle), model.get("shuffle", false)) { model.put("shuffle", it) }
        SliderSetting(stringResource(R.string.speed), model.get("speed", 1f), .5f..2f) { model.put("speed", it) }
        SliderSetting(stringResource(R.string.size), model.get("size", 1f), .7f..1.3f) { model.put("size", it) }
        Choices(stringResource(R.string.font), model.get("font", "sans"), listOf("serif" to stringResource(R.string.serif), "sans" to "${stringResource(R.string.sans)} (Vazirmatn)")) { model.put("font", it) }
        Choices(stringResource(R.string.color), model.get("color", "cream"), listOf("cream" to stringResource(R.string.cream), "mint" to stringResource(R.string.mint), "gold" to stringResource(R.string.gold))) { model.put("color", it) }
        Toggle(stringResource(R.string.dim), model.get("dim", false)) { model.put("dim", it) }
        Choices(stringResource(R.string.style), model.get("style", "ring"), listOf("ring" to stringResource(R.string.ring), "aurora" to stringResource(R.string.aurora), "lightning" to stringResource(R.string.lightning))) { model.put("style", it) }
        Toggle(stringResource(R.string.haptics), model.get("haptics", true)) { model.put("haptics", it) }
        HorizontalDivider()
        Toggle(stringResource(R.string.music), model.get("music", true)) { model.put("music", it) }
        Choices(stringResource(R.string.track), model.get("track", "1"), listOf("1" to "I", "2" to "II", "3" to "III", "4" to "IV", "5" to "V", "6" to "VI", "custom" to stringResource(R.string.custom_audio))) { audioPreview.stop(); testingAudio = false; model.put("track", it) }
        Button(onClick = { audioPicker.launch(arrayOf("audio/*")) }) { Text(stringResource(R.string.choose_audio)) }
        SliderSetting(stringResource(R.string.volume), model.get("volume", .35f), 0f..1f) { model.put("volume", it); if (testingAudio) audioPreview.setVolume(it) }
        Toggle(stringResource(R.string.silent), model.get("silent", false)) { model.put("silent", it); audioPreview.stop(); testingAudio = false }
        Button(onClick = { if (testingAudio) { audioPreview.stop(); testingAudio = false } else testingAudio = audioPreview.start(model.get("track", "1"), model.get("volume", .35f), model.get("silent", false), model.get("audio_uri", "")) }) { Text(stringResource(if (testingAudio) R.string.stop_music else R.string.test_music)) }
        Text(stringResource(R.string.media_volume_help), style = MaterialTheme.typography.bodySmall)
        HorizontalDivider()
        Text(stringResource(R.string.my_texts), style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { draft = ""; editing = -1 }) { Text(stringResource(R.string.add)) }
            OutlinedButton(onClick = { txtPicker.launch(arrayOf("text/plain")) }) { Text(stringResource(R.string.import_txt)) }
        }
        model.texts.forEachIndexed { index, text ->
            Column {
                Text(text.take(100), maxLines = 2)
                Row {
                    TextButton(onClick = { draft = text; editing = index }) { Text(stringResource(R.string.edit)) }
                    TextButton(onClick = { model.texts.removeAt(index); model.saveTexts() }) { Text(stringResource(R.string.delete)) }
                    val up = stringResource(R.string.up)
                    val down = stringResource(R.string.down)
                    TextButton(modifier = Modifier.semantics { contentDescription = up }, enabled = index > 0, onClick = { val item = model.texts.removeAt(index); model.texts.add(index - 1, item); model.saveTexts() }) { Text("↑") }
                    TextButton(modifier = Modifier.semantics { contentDescription = down }, enabled = index < model.texts.lastIndex, onClick = { val item = model.texts.removeAt(index); model.texts.add(index + 1, item); model.saveTexts() }) { Text("↓") }
                }
            }
        }
    }
}
@Composable private fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label, Modifier.weight(1f)); Switch(checked = checked, onCheckedChange = onChange) } }
@Composable private fun SliderSetting(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) { Column { Text(label); Slider(value = value, onValueChange = onChange, valueRange = range) } }
