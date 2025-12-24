package com.ashutoshsun.homescreenwidgetpack

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import android.widget.RemoteViews
import android.util.Log
import android.view.View
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toBitmap
import androidx.preference.PreferenceManager
import androidx.palette.graphics.Palette

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
            val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
            val maxHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
            val isSmallLayout = maxHeight < 150

            val layoutId = if (isSmallLayout) {
                R.layout.music_widget_layout_2x1
            } else {
                R.layout.music_widget_layout
            }
            val views = RemoteViews(context.packageName, layoutId)

            val playPauseButtonSize = if (isSmallLayout) 48 else 64

            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            val blurEnabled = prefs.getBoolean("blur_effect", true)
            val gradientEnabled = prefs.getBoolean("gradient_overlay", true)
            val blurStrength = prefs.getInt("blur_strength", 10).toFloat()
            val gradientDarkness = prefs.getInt("gradient_darkness", 30) / 100f
            val useAppIcon = prefs.getBoolean("use_app_icon", false)

            // Update track information
            if (metadata != null && metadata.albumArt != null) {
                // Music is playing with album art
                views.setTextViewText(R.id.track_title, metadata.title ?: "Unknown Track")
                views.setTextViewText(R.id.artist_name, metadata.artist ?: "Unknown Artist")

                val albumArt = if (blurEnabled) {
                    applyBlur(context, metadata.albumArt, blurStrength)
                } else {
                    metadata.albumArt
                }
                views.setImageViewBitmap(R.id.album_art, albumArt)
                views.setViewVisibility(R.id.album_art, View.VISIBLE)

                // Calculate brightness for color extraction
                val brightness = calculateBrightness(metadata.albumArt)
                val isDark = brightness < 128

                // Extract dominant vibrant color from album art
                val dominantColor = extractDominantColor(metadata.albumArt, isDark)

                // Set app icon based on preference
                if (useAppIcon && metadata.launcherIcon != null) {
                    views.setImageViewBitmap(R.id.music_icon, metadata.launcherIcon)
                    views.setInt(R.id.music_icon, "setColorFilter", Color.TRANSPARENT) // Remove any tint
                } else {
                    if (metadata.notificationIcon != null) {
                        views.setImageViewBitmap(R.id.music_icon, metadata.notificationIcon)
                    } else {
                        views.setImageViewResource(R.id.music_icon, R.drawable.ic_music_note)
                    }
                    views.setInt(R.id.music_icon, "setColorFilter", dominantColor) // Tint with dominant color
                }

                // Text is always white
                views.setTextColor(R.id.track_title, Color.WHITE)
                views.setTextColor(R.id.artist_name, Color.WHITE)

                if (gradientEnabled) {
                    val darkenedColor = darkenColor(dominantColor, gradientDarkness)
                    val gradient = GradientDrawable(
                        GradientDrawable.Orientation.TOP_BOTTOM,
                        intArrayOf(darkenedColor, Color.TRANSPARENT)
                    )
                    views.setImageViewBitmap(R.id.gradient_overlay, gradient.toBitmap(albumArt.width, albumArt.height))
                    views.setViewVisibility(R.id.gradient_overlay, View.VISIBLE)
                    views.setInt(R.id.overlay_scrim, "setBackgroundColor", Color.TRANSPARENT)
                } else {
                    views.setViewVisibility(R.id.gradient_overlay, View.GONE)
                    val scrimColor = Color.argb(102, 0, 0, 0) // 40% dark scrim
                    views.setInt(R.id.overlay_scrim, "setBackgroundColor", scrimColor)
                }

                // Create colored circle background for play/pause button
                val circleBackground = createColoredCircle(playPauseButtonSize, dominantColor)
                views.setImageViewBitmap(R.id.play_pause_button_background, circleBackground)

                // Set dark icon
                views.setInt(R.id.play_pause_button, "setColorFilter", Color.parseColor("#1A1A1A")) // Dark icon

                if (!isSmallLayout) {
                    views.setInt(R.id.previous_button, "setColorFilter", dominantColor)
                    views.setInt(R.id.next_button, "setColorFilter", dominantColor)
                }

                // Hide dynamic color background when music is playing
                views.setViewVisibility(R.id.widget_background, View.GONE)

                // Update play/pause button
                val playPauseIcon = if (metadata.isPlaying) {
                    R.drawable.ic_pause
                } else {
                    R.drawable.ic_play
                }
                views.setImageViewResource(R.id.play_pause_button, playPauseIcon)

                // Create circular progress ring
                val progress = if (metadata.duration > 0) {
                    (metadata.position.toFloat() / metadata.duration.toFloat())
                } else {
                    0f
                }
                val progressRing = createProgressRing(playPauseButtonSize, dominantColor, progress)
                views.setImageViewBitmap(R.id.progress_ring, progressRing)
                views.setViewVisibility(R.id.progress_ring, View.VISIBLE)
            } else {
                // No media playing - show dynamic accent background
                views.setTextViewText(R.id.track_title, "No track playing")
                views.setTextViewText(R.id.artist_name, "")

                // Hide album art, show dynamic color background
                views.setViewVisibility(R.id.album_art, View.GONE)
                views.setViewVisibility(R.id.gradient_overlay, View.GONE)
                views.setViewVisibility(R.id.widget_background, View.VISIBLE)

                // Get Material You colors
                val darkPrimary = getDarkPrimaryColor(context)
                val accentColor = getMaterialYouAccentColor(context)

                // Set dark primary color for background
                views.setInt(R.id.widget_background, "setBackgroundColor", darkPrimary)

                // Remove scrim overlay
                views.setInt(R.id.overlay_scrim, "setBackgroundColor", Color.TRANSPARENT)

                // Text is always white
                views.setTextColor(R.id.track_title, Color.WHITE)
                views.setTextColor(R.id.artist_name, Color.WHITE)

                // Create accent-colored circle for play/pause button
                val circleBackground = createColoredCircle(playPauseButtonSize, accentColor)
                views.setImageViewBitmap(R.id.play_pause_button_background, circleBackground)

                // Set dark primary icon color for play/pause
                views.setInt(R.id.play_pause_button, "setColorFilter", darkPrimary)
                views.setImageViewResource(R.id.play_pause_button, R.drawable.ic_play)

                // Tint music and nav icons with accent color
                views.setImageViewResource(R.id.music_icon, R.drawable.ic_music_note)
                views.setInt(R.id.music_icon, "setColorFilter", accentColor)

                if (!isSmallLayout) {
                    views.setInt(R.id.previous_button, "setColorFilter", accentColor)
                    views.setInt(R.id.next_button, "setColorFilter", accentColor)
                }

                // No progress ring when no music playing
                views.setImageViewBitmap(R.id.progress_ring, null)
            }

            // Set up click listeners
            setupClickListeners(context, views, !isSmallLayout, metadata)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        /**
         * Darkens a color by a given factor.
         * @param color The color to darken.
         * @param factor The factor to darken by (0.0 to 1.0).
         * @return The darkened color.
         */
        private fun darkenColor(color: Int, factor: Float): Int {
            return ColorUtils.blendARGB(color, Color.BLACK, factor)
        }

        /**
         * Calculate the average brightness of an image
         * Returns a value between 0 (dark) and 255 (bright)
         */
        private fun calculateBrightness(bitmap: Bitmap): Int {
            // Sample the bitmap for performance
            val scaledBitmap = Bitmap.createScaledBitmap(bitmap, 50, 50, false)
            var totalBrightness = 0L
            val pixelCount = scaledBitmap.width * scaledBitmap.height

            for (x in 0 until scaledBitmap.width) {
                for (y in 0 until scaledBitmap.height) {
                    val pixel = scaledBitmap.getPixel(x, y)
                    val r = Color.red(pixel)
                    val g = Color.green(pixel)
                    val b = Color.blue(pixel)
                    // Calculate perceived brightness
                    val brightness = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
                    totalBrightness += brightness
                }
            }

            scaledBitmap.recycle()
            return (totalBrightness / pixelCount).toInt()
        }

        /**
         * Apply strong blur to album art for beautiful background effect
         * @param context Application context
         * @param bitmap Original album art bitmap
         * @param radius Blur radius (1-25, higher = more blur)
         * @return Blurred bitmap
         */
        @Suppress("DEPRECATION")
        private fun applyBlur(context: Context, bitmap: Bitmap, radius: Float): Bitmap {
            try {
                // Create output bitmap
                val outputBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)

                // Use RenderScript for efficient blur
                val renderScript = RenderScript.create(context)
                val input = Allocation.createFromBitmap(renderScript, bitmap)
                val output = Allocation.createFromBitmap(renderScript, outputBitmap)

                val script = ScriptIntrinsicBlur.create(renderScript, Element.U8_4(renderScript))
                script.setRadius(radius.coerceIn(1f, 25f))
                script.setInput(input)
                script.forEach(output)

                output.copyTo(outputBitmap)

                // Cleanup
                input.destroy()
                output.destroy()
                script.destroy()
                renderScript.destroy()

                return outputBitmap
            } catch (e: Exception) {
                Log.e(TAG, "Error applying blur, using original bitmap", e)
                return bitmap
            }
        }

        /**
         * Create a colored circle bitmap for the play/pause button background
         * @param sizeDp Size in dp
         * @param color The color for the circle
         * @return Bitmap with colored circle
         */
        private fun createColoredCircle(sizeDp: Int, color: Int): Bitmap {
            val bitmap = Bitmap.createBitmap(sizeDp, sizeDp, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint = Paint().apply {
                this.color = color
                isAntiAlias = true
            }
            val radius = sizeDp / 2f
            canvas.drawCircle(radius, radius, radius, paint)
            return bitmap
        }

        /**
         * Create a circular progress ring bitmap with background track and progress arc
         * @param sizeDp Size in dp
         * @param color The color for the progress arc
         * @param progress Progress value from 0.0 to 1.0
         * @return Bitmap with circular progress ring
         */
        private fun createProgressRing(sizeDp: Int, color: Int, progress: Float): Bitmap {
            val bitmap = Bitmap.createBitmap(sizeDp, sizeDp, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            val strokeWidth = 2.5f // Much thinner, subtle ring
            val padding = strokeWidth / 2f + 3f // Extra padding to move ring inside
            val rect = RectF(
                padding,
                padding,
                sizeDp - padding,
                sizeDp - padding
            )

            // Draw background track (full circle) - muted translucent gray
            val backgroundPaint = Paint().apply {
                this.color = Color.argb(100, 255, 255, 255) // Translucent white/gray (40% opacity)
                style = Paint.Style.STROKE
                this.strokeWidth = strokeWidth
                isAntiAlias = true
            }
            canvas.drawCircle(sizeDp / 2f, sizeDp / 2f, (sizeDp - padding * 2) / 2f, backgroundPaint)

            // Draw progress arc on top - solid black
            val progressPaint = Paint().apply {
                this.color = Color.BLACK // Solid black
                style = Paint.Style.STROKE
                this.strokeWidth = strokeWidth
                isAntiAlias = true
                strokeCap = Paint.Cap.ROUND
            }

            // Draw from top (-90 degrees) clockwise
            val sweepAngle = progress * 360f
            canvas.drawArc(rect, -90f, sweepAngle, false, progressPaint)

            return bitmap
        }

        /**
         * Extract the lightest accent color from album art
         * @param bitmap Album art bitmap
         * @param isDark Whether the background is dark (unused, kept for compatibility)
         * @return Lightest accent color from the album art
         */
        private fun extractDominantColor(bitmap: Bitmap, isDark: Boolean): Int {
            try {
                // Create a scaled down version for faster palette generation
                val scaledBitmap = Bitmap.createScaledBitmap(bitmap, 100, 100, false)

                // Generate palette from the album art
                val palette = Palette.from(scaledBitmap).generate()
                scaledBitmap.recycle()

                // Always extract the LIGHTEST accent color
                val accentColor = palette.lightVibrantSwatch?.rgb
                    ?: palette.lightMutedSwatch?.rgb
                    ?: palette.vibrantSwatch?.rgb
                    ?: palette.mutedSwatch?.rgb
                    ?: Color.WHITE // Fallback to white

                // Return color directly without post-processing
                return accentColor
            } catch (e: Exception) {
                Log.e(TAG, "Error extracting dominant color", e)
                // Return white fallback
                return Color.WHITE
            }
        }

        /**
         * Get system accent color (Android 12+/API 31+)
         */
        private fun getMaterialYouAccentColor(context: Context): Int {
            // Android 13+ is guaranteed, use dynamic color directly
            return context.resources.getColor(
                android.R.color.system_accent1_200,
                context.theme
            )
        }

        /**
         * Get system dark primary color (Android 12+/API 31+)
         */
        private fun getDarkPrimaryColor(context: Context): Int {
            return context.resources.getColor(
                android.R.color.system_neutral1_900,
                context.theme
            )
        }

        /**
         * Calculate brightness of a single color
         */
        private fun calculateColorBrightness(color: Int): Int {
            val r = Color.red(color)
            val g = Color.green(color)
            val b = Color.blue(color)
            return (0.299 * r + 0.587 * g + 0.114 * b).toInt()
        }

        private fun setupClickListeners(context: Context, views: RemoteViews, includePrevNext: Boolean, metadata: MediaMetadata?) {
            // Click listener for the whole widget
            if (metadata?.clickIntent != null) {
                views.setOnClickPendingIntent(R.id.widget_root, metadata.clickIntent)
            } else {
                // Do nothing if there's no intent
                views.setOnClickPendingIntent(R.id.widget_root, null)
            }

            // Play/Pause button
            val playPauseIntent = Intent(context, MusicWidgetProvider::class.java).apply {
                action = ACTION_PLAY_PAUSE
            }
            val playPausePendingIntent = PendingIntent.getBroadcast(
                context, 0, playPauseIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.play_pause_button, playPausePendingIntent)

            if (includePrevNext) {
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
    }

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle?) {
        Log.d(TAG, "onAppWidgetOptionsChanged called")
        val metadata = MediaNotificationListener.getCurrentMetadata()
        updateAppWidget(context, appWidgetManager, appWidgetId, metadata)
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
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
    val duration: Long,
    val notificationIcon: Bitmap? = null,
    val launcherIcon: Bitmap? = null,
    val clickIntent: PendingIntent? = null
)
