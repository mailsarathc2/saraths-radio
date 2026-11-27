package com.example.scheduledradio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat

class RadioPlaybackService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var currentStreamUrl: String = ""
    private lateinit var prefs: RadioPreferences

    companion object {
        const val CMD_PLAY = "CMD_PLAY"
        const val CMD_STOP = "CMD_STOP"
        const val CMD_UPDATE_AUDIO = "CMD_UPDATE_AUDIO"
        const val EXTRA_URL = "EXTRA_URL"
        private const val CHANNEL_ID = "radio_playback_channel"
        private const val NOTIFICATION_ID = 999
    }

    override fun onCreate() {
        super.onCreate()
        prefs = RadioPreferences(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            CMD_PLAY -> {
                val url = intent.getStringExtra(EXTRA_URL) ?: return START_NOT_STICKY
                currentStreamUrl = url
                startForeground(NOTIFICATION_ID, buildNotification("Playing Sarath's Radio..."))
                startStreaming(url)
            }

            CMD_UPDATE_AUDIO -> {
                applySavedVolume()
            }

            CMD_STOP -> {
                stopPlaybackAndService()
            }
        }
        return START_STICKY
    }

    private fun startStreaming(url: String) {
        releasePlayer()
        try {
            mediaPlayer = MediaPlayer().apply {
                setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setDataSource(url)
                setOnPreparedListener { mp ->
                    applySavedVolume()
                    mp.start()
                }
                setOnErrorListener { _, _, _ ->
                    if (currentStreamUrl.isNotBlank()) {
                        startStreaming(currentStreamUrl)
                    }
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun applySavedVolume() {
        val vol = if (prefs.isMuted) 0f else prefs.volumeLevel
        try {
            mediaPlayer?.setVolume(vol, vol)
        } catch (_: Exception) {
        }
    }

    private fun releasePlayer() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {
        }
        mediaPlayer = null
    }

    private fun stopPlaybackAndService() {
        currentStreamUrl = ""
        releasePlayer()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(statusText: String): Notification {
        val stopIntent = Intent(this, RadioPlaybackService::class.java).apply {
            action = CMD_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openAppIntent = Intent(this, MainActivity::class.java)
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Sarath's Radio")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(openAppPendingIntent)
            .addAction(android.R.drawable.ic_media_pause, "Stop Radio", stopPendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Radio Playback",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        releasePlayer()
        super.onDestroy()
    }
}
