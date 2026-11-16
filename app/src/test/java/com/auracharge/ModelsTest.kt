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
            assertEquals(Entry(fields[3].replace("\\n", "\n"), fields[4], fields[1], fields[2], fields[0]), entry)
        }
        assertTrue(entries.all { it.fa.isNotBlank() && it.tr.isNotBlank() && it.author.isNotBlank() && it.source.isNotBlank() })
        assertEquals(setOf("poetry", "story", "wisdom", "spiritual"), entries.map { it.category }.toSet())
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
