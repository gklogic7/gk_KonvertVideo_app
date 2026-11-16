package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.KonvertApplication
import com.example.MainActivity
import com.example.R

class KvPlayerAudioService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannel()
        val app = application as KonvertApplication
        val controller = app.kvPlayerController

        when (intent?.action) {
            ACTION_TOGGLE_PLAY -> controller.togglePlayPause()
            ACTION_NEXT -> controller.skipNext()
            ACTION_PREV -> controller.skipPrevious()
            ACTION_STOP -> {
                controller.stopAndDismissBgAudio()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }

        val state = controller.uiState.value
        val trackTitle = state.currentTrack?.title ?: getString(R.string.nav_kvplayer)
        val notification = buildNotification(
            title = trackTitle,
            isPlaying = state.isPlaying,
            speed = state.playbackSpeed
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        val app = application as KonvertApplication
        if (!app.kvPlayerController.uiState.value.bgAudioEnabled) {
            app.kvPlayerController.pause()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.kvplayer_notif_channel),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = getString(R.string.kvplayer_notif_desc)
                }
                nm.createNotificationChannel(channel)
            }
        }
    }

    private fun buildNotification(
        title: String,
        isPlaying: Boolean,
        speed: Float
    ): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                putExtra(EXTRA_OPEN_KVPLAYER, true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val prevIntent = servicePendingIntent(1, ACTION_PREV)
        val toggleIntent = servicePendingIntent(2, ACTION_TOGGLE_PLAY)
        val nextIntent = servicePendingIntent(3, ACTION_NEXT)
        val stopIntent = servicePendingIntent(4, ACTION_STOP)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(
                if (isPlaying) "Kvplayer Bg Audio • ${speed}x" else "Kvplayer Paused"
            )
            .setContentIntent(openIntent)
            .setOngoing(isPlaying)
            .setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_media_previous, "Prev", prevIntent)
            .addAction(
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (isPlaying) "Pause" else "Play",
                toggleIntent
            )
            .addAction(android.R.drawable.ic_media_next, "Next", nextIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopIntent)
            .build()
    }

    private fun servicePendingIntent(requestCode: Int, action: String): PendingIntent {
        val intent = Intent(this, KvPlayerAudioService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val CHANNEL_ID = "kvplayer_bg_audio"
        const val NOTIFICATION_ID = 4208
        const val ACTION_SYNC = "com.example.kvplayer.SYNC"
        const val ACTION_TOGGLE_PLAY = "com.example.kvplayer.TOGGLE_PLAY"
        const val ACTION_NEXT = "com.example.kvplayer.NEXT"
        const val ACTION_PREV = "com.example.kvplayer.PREV"
        const val ACTION_STOP = "com.example.kvplayer.STOP"
        const val EXTRA_OPEN_KVPLAYER = "open_kvplayer"
    }
}
