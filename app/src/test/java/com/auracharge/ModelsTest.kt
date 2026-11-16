package com.auracharge

import android.os.BatteryManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ModelsTest {
    @Test fun packagedLibraryLoadsAllEntries() {
        val entries = parseLibrary(File("src/main/assets/library.json").readText(Charsets.UTF_8))
        val source = File("../scripts/library.tsv").readLines(Charsets.UTF_8).map { it.split('|') }
        assertTrue("Library contains ${entries.size} entries", entries.size >= 150)
        assertEquals(source.size, entries.size)
        source.zip(entries).forEach { (fields, entry) ->
            assertEquals(5, fields.size)
            assertEquals(Entry(fields[3].replace("\\n", "\n"), fields[4].replace("\\n", "\n"), fields[1], fields[2], fields[0]), entry)
        }
        assertTrue(entries.all { it.fa.isNotBlank() && it.tr.isNotBlank() && it.author.isNotBlank() && it.source.isNotBlank() })
        assertEquals(setOf("poetry", "story", "wisdom", "spiritual", "religious", "educational"), entries.map { it.category }.toSet())
        assertTrue(entries.count { it.category == "educational" } >= 40)
        assertTrue(entries.filter { it.category == "educational" }.all { it.tr.contains("\nÖğüt:") })
    }
    @Test fun categoryMusicResolvesAndAlternates() {
        val expected = mapOf("poetry" to "poetry", "story" to "story", "religious" to "religious", "educational" to "religious")
        expected.forEach { (category, prefix) ->
            assertEquals("${prefix}_1", resolveTrack("category", category, 0))
            assertEquals("${prefix}_2", resolveTrack("category", category, 1))
            assertEquals("${prefix}_1", resolveTrack("category", category, 2))
            assertTrue(File("src/main/res/raw/${prefix}_1.mp3").isFile)
            assertTrue(File("src/main/res/raw/${prefix}_2.mp3").isFile)
        }
        assertEquals("custom", resolveTrack("custom", "story", 0))
        assertEquals("2", resolveTrack("2", "story", 0))
    }
    @Test fun requestedCategoryMinimums() {
        val entries = parseLibrary(File("src/main/assets/library.json").readText(Charsets.UTF_8))
        listOf("poetry", "story", "religious", "educational").forEach { category ->
            val count = entries.count { it.category == category }
            assertTrue("$category has $count entries; at least 40 required", count >= 40)
        }
    }
    @Test fun religiousCategoryContainsHadith() {
        val entries = parseLibrary(File("src/main/assets/library.json").readText(Charsets.UTF_8))
        val count = entries.count { it.category == "religious" && listOf("Buhari", "Buhârî", "Bukhari", "Muslim", "Müslim", "Tirmidhi", "Tirmizî").any { source -> it.source.contains(source, ignoreCase = true) } }
        assertTrue("Religious category has $count sourced hadith; at least 28 required", count >= 28)
        assertTrue("Hadith must outnumber other religious selections", count > entries.count { it.category == "religious" } - count)
    }
    @Test fun statusPriorities() {
        assertEquals(R.string.full, BatteryState(status = BatteryManager.BATTERY_STATUS_FULL, plugged = 4, currentMa = 2000).statusResource)
        assertEquals(R.string.paused, BatteryState(status = BatteryManager.BATTERY_STATUS_NOT_CHARGING, plugged = 1).statusResource)
        assertEquals(R.string.wireless, BatteryState(status = BatteryManager.BATTERY_STATUS_CHARGING, plugged = BatteryManager.BATTERY_PLUGGED_WIRELESS, currentMa = 2000).statusResource)
        assertEquals(R.string.fast_charging, BatteryState(status = BatteryManager.BATTERY_STATUS_CHARGING, plugged = 1, currentMa = 1600).statusResource)
        assertEquals(R.string.charging, BatteryState(status = BatteryManager.BATTERY_STATUS_CHARGING, plugged = 1, currentMa = 500).statusResource)
    }
    @Test fun scalingClampsAndInterpolates() {
        assertEquals(14f, textScale(-1f, 14f, 34f), .001f)
        assertEquals(24f, textScale(.5f, 14f, 34f), .001f)
        assertEquals(34f, textScale(2f, 14f, 34f), .001f)
    }
    @Test fun longCustomTextIsSplitWithoutLosingWords() {
        val text = "one two three four five six seven eight nine"
        assertEquals(text, textChunks(text, 12).joinToString(" "))
    }
}
