package com.ashutoshsun.homescreenwidgetpack

import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import android.view.KeyEvent

class MediaNotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "MediaNotifListener"
        private var instance: MediaNotificationListener? = null
        private var currentMetadata: com.ashutoshsun.homescreenwidgetpack.MediaMetadata? = null
        private var currentController: MediaController? = null

        const val ACTION_PLAY_PAUSE = "play_pause"
        const val ACTION_NEXT = "next"
        const val ACTION_PREVIOUS = "previous"

        fun getCurrentMetadata(): com.ashutoshsun.homescreenwidgetpack.MediaMetadata? {
            return currentMetadata
        }

        fun sendMediaAction(action: String) {
            instance?.performMediaAction(action)
        }
    }

    private val activeControllers = mutableMapOf<String, MediaController>()
    private val progressHandler = Handler(Looper.getMainLooper())
    private var updateProgressRunnable: Runnable? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.d(TAG, "Service created")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        activeControllers.values.forEach { it.unregisterCallback(mediaCallback) }
        activeControllers.clear()
        stopUpdatingProgress()
        Log.d(TAG, "Service destroyed")
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "Notification listener connected")
        
        // Check existing notifications for media sessions
        try {
            activeNotifications?.forEach { sbn ->
                checkForMediaSession(sbn)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking existing notifications", e)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        sbn?.let { checkForMediaSession(it) }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        sbn?.let {
            val key = it.key
            activeControllers[key]?.let { controller ->
                controller.unregisterCallback(mediaCallback)
                activeControllers.remove(key)
                Log.d(TAG, "Removed media controller for: ${it.packageName}")
                
                // If this was our current controller, clear it
                if (currentController == controller) {
                    currentController = null
                    currentMetadata = null
                    stopUpdatingProgress()
                    updateWidget()
                }
            }
        }
    }

    private fun checkForMediaSession(sbn: StatusBarNotification) {
        try {
            val notification = sbn.notification ?: return
            val mediaSession = notification.extras?.get("android.mediaSession") as? MediaSession.Token
            
            if (mediaSession != null) {
                val controller = MediaController(this, mediaSession)
                val key = sbn.key
                
                // Register callback if not already registered
                if (!activeControllers.containsKey(key)) {
                    controller.registerCallback(mediaCallback)
                    activeControllers[key] = controller
                    Log.d(TAG, "Found media session from: ${sbn.packageName}")
                }
                
                // Update current controller and metadata with notification small icon
                currentController = controller
                currentPackageName = sbn.packageName
                currentNotification = sbn
                updateMetadataFromController(controller, sbn)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for media session", e)
        }
    }

    private var currentPackageName: String? = null
    private var currentNotification: StatusBarNotification? = null
    
    private val mediaCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            super.onPlaybackStateChanged(state)
            Log.d(TAG, "Playback state changed: ${state?.state}")
            currentController?.let { currentNotification?.let { sbn -> updateMetadataFromController(it, sbn) } }
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            super.onMetadataChanged(metadata)
            Log.d(TAG, "Metadata changed")
            currentController?.let { currentNotification?.let { sbn -> updateMetadataFromController(it, sbn) } }
        }
    }

    private fun updateMetadataFromController(controller: MediaController, sbn: StatusBarNotification) {
        try {
            val metadata = controller.metadata
            val playbackState = controller.playbackState
            
            if (metadata != null) {
                val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
                val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
                val albumArt = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                    ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
                val duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION)
                
                val isPlaying = playbackState?.state == PlaybackState.STATE_PLAYING
                val position = playbackState?.position ?: 0L
                
                // Get notification small icon (the monochrome icon from status bar)
                val appIcon = getNotificationIcon(sbn)

                currentMetadata = com.ashutoshsun.homescreenwidgetpack.MediaMetadata(
                    title = title,
                    artist = artist,
                    albumArt = albumArt,
                    isPlaying = isPlaying,
                    position = position,
                    duration = duration,
                    appIcon = appIcon
                )

                Log.d(TAG, "Updated metadata: $title - $artist (Playing: $isPlaying)")
                updateWidget()
                if (isPlaying) {
                    startUpdatingProgress(controller)
                } else {
                    stopUpdatingProgress()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating metadata", e)
        }
    }
    
    private fun getNotificationIcon(sbn: StatusBarNotification): Bitmap? {
        return try {
            // Get the small icon from the notification (status bar icon)
            val icon = sbn.notification.smallIcon?.loadDrawable(this)
            
            if (icon != null) {
                val bitmap = Bitmap.createBitmap(
                    icon.intrinsicWidth.coerceAtLeast(24),
                    icon.intrinsicHeight.coerceAtLeast(24),
                    Bitmap.Config.ARGB_8888
                )
                val canvas = android.graphics.Canvas(bitmap)
                icon.setBounds(0, 0, canvas.width, canvas.height)
                icon.draw(canvas)
                bitmap
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting notification icon", e)
            null
        }
    }

    private fun startUpdatingProgress(controller: MediaController) {
        if (updateProgressRunnable != null) return // Already running

        updateProgressRunnable = Runnable {
            val playbackState = controller.playbackState
            if (playbackState != null && playbackState.state == PlaybackState.STATE_PLAYING) {
                val elapsed = SystemClock.elapsedRealtime() - playbackState.lastPositionUpdateTime
                val currentPosition = playbackState.position + (elapsed * playbackState.playbackSpeed).toLong()
                currentMetadata = currentMetadata?.copy(position = currentPosition)
                updateWidget()
                progressHandler.postDelayed(updateProgressRunnable!!, 1000)
            } else {
                stopUpdatingProgress()
            }
        }
        progressHandler.post(updateProgressRunnable!!)
    }

    private fun stopUpdatingProgress() {
        updateProgressRunnable?.let { progressHandler.removeCallbacks(it) }
        updateProgressRunnable = null
    }

    private fun updateWidget() {
        try {
            MusicWidgetProvider.updateWidget(this, currentMetadata)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating widget", e)
        }
    }

    private fun performMediaAction(action: String) {
        try {
            currentController?.let { controller ->
                when (action) {
                    ACTION_PLAY_PAUSE -> {
                        val playbackState = controller.playbackState
                        if (playbackState?.state == PlaybackState.STATE_PLAYING) {
                            controller.transportControls.pause()
                            Log.d(TAG, "Pausing playback")
                        } else {
                            controller.transportControls.play()
                            Log.d(TAG, "Starting playback")
                        }
                    }
                    ACTION_NEXT -> {
                        controller.transportControls.skipToNext()
                        Log.d(TAG, "Skipping to next")
                    }
                    ACTION_PREVIOUS -> {
                        controller.transportControls.skipToPrevious()
                        Log.d(TAG, "Skipping to previous")
                    }
                }
            } ?: Log.w(TAG, "No active media controller")
        } catch (e: Exception) {
            Log.e(TAG, "Error performing media action", e)
        }
    }
}
