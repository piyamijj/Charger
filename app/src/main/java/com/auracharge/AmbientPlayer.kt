package com.auracharge

import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.content.ContextCompat

fun categoryTracks(category: String): List<String> = when (category) {
    "poetry" -> listOf("poetry_1", "poetry_2")
    "story", "wisdom" -> listOf("story_1", "story_2")
    "spiritual", "religious", "educational" -> listOf("religious_1", "religious_2")
    else -> listOf("poetry_1", "poetry_2")
}

fun resolveTrack(track: String, category: String, index: Int): String =
    if (track == "category") categoryTracks(category)[Math.floorMod(index, 2)] else track

class AmbientPlayer(private val context: Context) {
    private val manager = context.getSystemService(AudioManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var generation = 0
    private var playingVolume = 0f
    private var hasFocus = false
    private var watchingRinger = false
    private val ringerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (manager.ringerMode != AudioManager.RINGER_MODE_NORMAL) stop()
        }
    }
    private val listener = AudioManager.OnAudioFocusChangeListener { change -> if (change < 0) stop() }
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()).setOnAudioFocusChangeListener(listener).build()
    fun start(track: String, volume: Float, silentOnly: Boolean, custom: String): Boolean {
        stop()
        playingVolume = volume.coerceIn(0f, 1f)
        if (silentOnly && manager.ringerMode != AudioManager.RINGER_MODE_NORMAL) { Toast.makeText(context, R.string.silent_audio, Toast.LENGTH_SHORT).show(); return false }
        if (manager.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) { Toast.makeText(context, R.string.audio_focus_error, Toast.LENGTH_SHORT).show(); return false }
        hasFocus = true
        try {
            if (silentOnly) {
                ContextCompat.registerReceiver(context, ringerReceiver, IntentFilter(AudioManager.RINGER_MODE_CHANGED_ACTION), ContextCompat.RECEIVER_NOT_EXPORTED)
                watchingRinger = true
            }
            val next = MediaPlayer()
            player = next
            next.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            if (track == "custom") {
                if (custom.isBlank()) { stop(); Toast.makeText(context, R.string.audio_error, Toast.LENGTH_SHORT).show(); return false }
                next.setDataSource(context, Uri.parse(custom))
            } else {
                val resource = when (track) {
                    "2" -> R.raw.ambient2; "3" -> R.raw.ambient3; "4" -> R.raw.ambient4; "5" -> R.raw.ambient5; "6" -> R.raw.ambient6
                    "poetry_1" -> R.raw.poetry_1; "poetry_2" -> R.raw.poetry_2
                    "story_1" -> R.raw.story_1; "story_2" -> R.raw.story_2
                    "religious_1" -> R.raw.religious_1; "religious_2" -> R.raw.religious_2
                    else -> R.raw.ambient1
                }
                context.resources.openRawResourceFd(resource).use { next.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
            }
            next.isLooping = true
            next.setVolume(0f, 0f)
            next.prepare()
            next.start()
            val token = ++generation
            for (step in 1..20) handler.postDelayed({ if (generation == token) runCatching { next.setVolume(playingVolume * step / 20, playingVolume * step / 20) } }, step * 60L)
            return true
        } catch (_: Exception) { stop(); Toast.makeText(context, R.string.audio_error, Toast.LENGTH_SHORT).show(); return false }
    }
    fun setVolume(volume: Float) { playingVolume = volume.coerceIn(0f, 1f); generation++; player?.setVolume(playingVolume, playingVolume) }
    fun stop(fade: Boolean = false) {
        if (watchingRinger) { context.unregisterReceiver(ringerReceiver); watchingRinger = false }
        val old = player
        player = null
        generation++
        if (old != null && fade && old.isPlaying) {
            val volume = playingVolume
            for (step in 1..10) handler.postDelayed({ runCatching { old.setVolume(volume * (10 - step) / 10f, volume * (10 - step) / 10f); if (step == 10) old.release() } }, step * 40L)
        } else if (old != null) runCatching { old.release() }
        if (hasFocus) { manager.abandonAudioFocusRequest(focus); hasFocus = false }
    }
}
