package com.ashutoshsun.homescreenwidgetpack

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.RemoteViews
import android.util.Log

class MusicWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val TAG = "MusicWidgetProvider"
        const val ACTION_PLAY_PAUSE = "com.ashutoshsun.homescreenwidgetpack.PLAY_PAUSE"
        const val ACTION_NEXT = "com.ashutoshsun.homescreenwidgetpack.NEXT"
        const val ACTION_PREVIOUS = "com.ashutoshsun.homescreenwidgetpack.PREVIOUS"
        const val ACTION_UPDATE_WIDGET = "com.ashutoshsun.homescreenwidgetpack.UPDATE_WIDGET"

        fun updateWidget(context: Context, metadata: MediaMetadata?) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, MusicWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            
            appWidgetIds.forEach { appWidgetId ->
                updateAppWidget(context, appWidgetManager, appWidgetId, metadata)
            }
        }

        private fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            metadata: MediaMetadata?
        ) {
            val views = RemoteViews(context.packageName, R.layout.music_widget_layout)

            // Update track information
            if (metadata != null) {
                views.setTextViewText(R.id.track_title, metadata.title ?: "Unknown Track")
                views.setTextViewText(R.id.artist_name, metadata.artist ?: "Unknown Artist")
                
                // Update album art
                if (metadata.albumArt != null) {
                    views.setImageViewBitmap(R.id.album_art, metadata.albumArt)
                } else {
                    views.setImageViewResource(R.id.album_art, R.drawable.ic_launcher_background)
                }

                // Update play/pause button
                val playPauseIcon = if (metadata.isPlaying) {
                    R.drawable.ic_pause
                } else {
                    R.drawable.ic_play
                }
                views.setImageViewResource(R.id.play_pause_button, playPauseIcon)

                // Update progress bar
                val progress = if (metadata.duration > 0) {
                    ((metadata.position.toFloat() / metadata.duration.toFloat()) * 100).toInt()
                } else {
                    0
                }
                views.setProgressBar(R.id.progress_bar, 100, progress, false)
            } else {
                // No media playing
                views.setTextViewText(R.id.track_title, "No track playing")
                views.setTextViewText(R.id.artist_name, "")
                views.setImageViewResource(R.id.album_art, R.drawable.ic_launcher_background)
                views.setImageViewResource(R.id.play_pause_button, R.drawable.ic_play)
                views.setProgressBar(R.id.progress_bar, 100, 0, false)
            }

            // Set up click listeners
            setupClickListeners(context, views)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun setupClickListeners(context: Context, views: RemoteViews) {
            // Play/Pause button
            val playPauseIntent = Intent(context, MusicWidgetProvider::class.java).apply {
                action = ACTION_PLAY_PAUSE
            }
            val playPausePendingIntent = PendingIntent.getBroadcast(
                context, 0, playPauseIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.play_pause_button, playPausePendingIntent)

            // Next button
            val nextIntent = Intent(context, MusicWidgetProvider::class.java).apply {
                action = ACTION_NEXT
            }
            val nextPendingIntent = PendingIntent.getBroadcast(
                context, 1, nextIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.next_button, nextPendingIntent)

            // Previous button
            val previousIntent = Intent(context, MusicWidgetProvider::class.java).apply {
                action = ACTION_PREVIOUS
            }
            val previousPendingIntent = PendingIntent.getBroadcast(
                context, 2, previousIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.previous_button, previousPendingIntent)
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        Log.d(TAG, "onUpdate called")
        // Get current metadata from service
        val metadata = MediaNotificationListener.getCurrentMetadata()
        appWidgetIds.forEach { appWidgetId ->
            updateAppWidget(context, appWidgetManager, appWidgetId, metadata)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        
        Log.d(TAG, "onReceive: ${intent.action}")
        
        when (intent.action) {
            ACTION_PLAY_PAUSE -> {
                MediaNotificationListener.sendMediaAction(MediaNotificationListener.ACTION_PLAY_PAUSE)
            }
            ACTION_NEXT -> {
                MediaNotificationListener.sendMediaAction(MediaNotificationListener.ACTION_NEXT)
            }
            ACTION_PREVIOUS -> {
                MediaNotificationListener.sendMediaAction(MediaNotificationListener.ACTION_PREVIOUS)
            }
            ACTION_UPDATE_WIDGET -> {
                val metadata = MediaNotificationListener.getCurrentMetadata()
                updateWidget(context, metadata)
            }
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        Log.d(TAG, "First widget added")
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        Log.d(TAG, "Last widget removed")
    }
}

data class MediaMetadata(
    val title: String?,
    val artist: String?,
    val albumArt: Bitmap?,
    val isPlaying: Boolean,
    val position: Long,
    val duration: Long
)
