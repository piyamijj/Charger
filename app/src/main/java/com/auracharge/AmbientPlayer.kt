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
    fun start(track: String, volume: Float, silentOnly: Boolean, custom: String) {
        stop()
        playingVolume = volume
        if (silentOnly && manager.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        if (manager.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return
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
                if (custom.isBlank()) { stop(); return }
                next.setDataSource(context, Uri.parse(custom))
            } else {
                val resource = when (track) { "2" -> R.raw.ambient2; "3" -> R.raw.ambient3; else -> R.raw.ambient1 }
                context.resources.openRawResourceFd(resource).use { next.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
            }
            next.isLooping = true
            next.setVolume(0f, 0f)
            next.prepare()
            next.start()
            val token = ++generation
            for (step in 1..20) handler.postDelayed({ if (generation == token) runCatching { next.setVolume(volume * step / 20, volume * step / 20) } }, step * 60L)
        } catch (_: Exception) { stop(); Toast.makeText(context, R.string.audio_error, Toast.LENGTH_SHORT).show() }
    }
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
