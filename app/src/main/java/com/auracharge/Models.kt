package com.auracharge

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import kotlin.math.abs

data class Entry(val fa: String, val tr: String, val author: String, val source: String, val category: String)

fun parseLibrary(json: String): List<Entry> {
    val data = JSONArray(json)
    return (0 until data.length()).map { index -> data.getJSONObject(index).let { Entry(it.getString("fa"), it.getString("tr"), it.getString("author"), it.getString("source"), it.getString("category")) } }
}

data class BatteryState(val level: Int = 0, val status: Int = BatteryManager.BATTERY_STATUS_UNKNOWN, val plugged: Int = 0, val currentMa: Int = 0, val voltageMv: Int = 0, val temperature: Float = 0f, val remainingMs: Long = -1) {
    val connected get() = plugged != 0
    val statusResource: Int get() = when {
        status == BatteryManager.BATTERY_STATUS_FULL -> R.string.full
        status != BatteryManager.BATTERY_STATUS_CHARGING -> R.string.paused
        plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS -> R.string.wireless
        abs(currentMa) >= 1500 -> R.string.fast_charging
        else -> R.string.charging
    }
    companion object {
        fun read(context: Context, intent: Intent): BatteryState {
            val manager = context.getSystemService(BatteryManager::class.java)
            val rawCurrent = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            val current = if (rawCurrent == Int.MIN_VALUE) 0 else rawCurrent / 1000
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) * 100 / scale
            val remaining = if (android.os.Build.VERSION.SDK_INT >= 28) manager.computeChargeTimeRemaining() else -1L
            return BatteryState(level.coerceIn(0, 100), intent.getIntExtra(BatteryManager.EXTRA_STATUS, 1), intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0), current, intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0), intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f, remaining)
        }
    }
}

fun textScale(progress: Float, minimum: Float, maximum: Float): Float = minimum + (maximum - minimum) * progress.coerceIn(0f, 1f)

fun entryDurationMs(entry: Entry?, speed: Float): Long {
    val base = if (entry?.category == "story") {
        val words = entry.tr.trim().split(Regex("\\s+")).size
        maxOf(22_000L, words * 60_000L / 130L + 8_000L)
    } else 22_000L
    return (base / speed.coerceIn(.5f, 2f)).toLong()
}

fun textChunks(text: String, limit: Int = 160): List<String> {
    val chunks = mutableListOf<String>()
    var start = 0
    while (start < text.length) {
        var end = (start + limit).coerceAtMost(text.length)
        if (end < text.length) {
            val whitespace = text.lastIndexOfAny(charArrayOf(' ', '\n', '\t'), end - 1)
            if (whitespace > start + limit / 2) end = whitespace
            if (end > start && Character.isHighSurrogate(text[end - 1])) end--
        }
        if (end <= start) end = (start + 1).coerceAtMost(text.length)
        text.substring(start, end).trim().takeIf { it.isNotEmpty() }?.let { chunks.add(it) }
        start = end
        while (start < text.length && text[start].isWhitespace()) start++
    }
    return chunks
}

class ChargeViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = application.getSharedPreferences("aura", Context.MODE_PRIVATE)
    private val revisions = MutableStateFlow(0)
    val revision = revisions.asStateFlow()
    val texts = mutableStateListOf<String>()
    val library: List<Entry> = runCatching {
        parseLibrary(application.assets.open("library.json").bufferedReader(Charsets.UTF_8).use { it.readText() })
    }.onFailure { android.util.Log.e("AuraCharge", "Cannot load library", it) }.getOrDefault(emptyList())
    init {
        if (!preferences.getBoolean("categories_v102_migrated", false)) {
            val saved = preferences.getString("categories", null)
            val edit = preferences.edit().putBoolean("categories_v102_migrated", true)
            if (saved != null) {
                val selected = saved.split(',').filter { it.isNotBlank() }.toSet()
                if ("spiritual" in selected) edit.putString("categories", (selected + "religious" + "educational").sorted().joinToString(","))
            }
            edit.apply()
        }
        val data = runCatching { JSONArray(preferences.getString("texts", "[]")) }.getOrDefault(JSONArray())
        for (index in 0 until data.length()) texts.add(data.optString(index))
    }
    fun get(key: String, default: String) = preferences.getString(key, default) ?: default
    fun get(key: String, default: Boolean) = preferences.getBoolean(key, default)
    fun get(key: String, default: Float) = preferences.getFloat(key, default)
    fun put(key: String, value: String) { preferences.edit().putString(key, value).apply(); revisions.value++ }
    fun put(key: String, value: Boolean) { preferences.edit().putBoolean(key, value).apply(); revisions.value++ }
    fun put(key: String, value: Float) { preferences.edit().putFloat(key, value).apply(); revisions.value++ }
    fun saveTexts() { preferences.edit().putString("texts", JSONArray(texts.toList()).toString()).apply(); revisions.value++ }
    fun entries(): List<Entry> {
        val selected = selectedCategories()
        val builtIn = library.filter { it.category in selected }
        val own = if ("custom" in selected) texts.flatMap { textChunks(it).map { part -> Entry(part, part, "", "", "custom") } } else emptyList()
        return builtIn + own
    }
    fun selectedCategories(): Set<String> {
        val source = get("source", "library")
        val category = get("category", "all")
        val builtIn = if (source == "mine") emptySet() else if (category == "all") setOf("poetry", "story", "wisdom", "spiritual", "religious", "educational") else setOf(category)
        val legacy = builtIn + if (source != "library") setOf("custom") else emptySet()
        return get("categories", legacy.joinToString(",")).split(',').filter { it.isNotBlank() }.toSet()
    }
}
