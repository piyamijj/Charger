package com.auracharge

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.util.Locale

private const val CHANNEL = "charging_monitor"
private const val ALERT_CHANNEL = "charging_alert"
private const val ALERT = 11
private const val SERVICE = 12

private fun localized(context: Context): Context {
    val language = context.getSharedPreferences("aura", Context.MODE_PRIVATE).getString("language", "tr") ?: "tr"
    val locale = Locale.forLanguageTag(language)
    return context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(locale) })
}
private fun channel(context: Context) {
    val title = localized(context).getString(R.string.channel)
    context.getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL, title, NotificationManager.IMPORTANCE_LOW))
    context.getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(ALERT_CHANNEL, title, NotificationManager.IMPORTANCE_HIGH))
}
private fun openIntent(context: Context): PendingIntent {
    val intent = Intent(context, MainActivity::class.java).putExtra("charging_screen", true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    return PendingIntent.getActivity(context, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
}
private fun notification(context: Context, service: Boolean): Notification {
    val strings = localized(context)
    val builder = NotificationCompat.Builder(context, if (service) CHANNEL else ALERT_CHANNEL).setSmallIcon(R.drawable.ic_charge).setContentTitle(strings.getString(R.string.app_name)).setContentText(strings.getString(if (service) R.string.service_notification else R.string.notification)).setContentIntent(openIntent(context)).setOngoing(service).setAutoCancel(!service).setCategory(NotificationCompat.CATEGORY_SERVICE)
    if (!service) builder.setFullScreenIntent(openIntent(context), true)
    return builder.build()
}
class PowerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_POWER_DISCONNECTED) { NotificationManagerCompat.from(context).cancel(ALERT); return }
        val prefs = context.getSharedPreferences("aura", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("monitor", false)) return
        if (prefs.getBoolean("lock_only", false) && !context.getSystemService(KeyguardManager::class.java).isKeyguardLocked) return
        channel(context)
        try { NotificationManagerCompat.from(context).notify(ALERT, notification(context, false)) } catch (_: SecurityException) { }
    }
}
class ChargingService : Service() {
    private val receiver = PowerReceiver()
    override fun onCreate() {
        super.onCreate()
        channel(this)
        startForeground(SERVICE, notification(this, true))
        registerReceiver(receiver, IntentFilter().apply { addAction(Intent.ACTION_POWER_CONNECTED); addAction(Intent.ACTION_POWER_DISCONNECTED) })
    }
    override fun onDestroy() { unregisterReceiver(receiver); super.onDestroy() }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY
    override fun onBind(intent: Intent?): IBinder? = null
}
