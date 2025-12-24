## Home Screen Widget Pack

Android 13+ widget pack intended to host **multiple home screen widgets**. The initial release ships with a **dynamic, Material You–styled music widget** that mirrors your currently playing media and controls playback from the home screen.

## Highlights

- **First widget: Now Playing Music** that reads active media notifications via a `NotificationListenerService`
- **Dynamic theming** using Material You colors and album art (blurred backgrounds, gradients, and accent tints)
- **Full playback controls**: play/pause, next, and previous directly from the widget
- **Adaptive layouts** for different widget sizes (2x1 compact and larger layouts)
- **Live progress ring** around the play/pause button showing track position
- **Configurable visuals** via in-app settings (blur effect, gradient overlay, etc.)

## Architecture

- **App**: Classic Android app with `MainActivity` as the launcher and `SettingsActivity` for configuration
- **Widgets**: `MusicWidgetProvider` `AppWidgetProvider` handling layout selection, RemoteViews updates, and click actions
- **Media Bridge**: `MediaNotificationListener` `NotificationListenerService` that:
  - Listens to media-style notifications
  - Tracks the active `MediaController` and playback state
  - Extracts title, artist, duration, album art, and app icon
  - Pushes `MediaMetadata` into the widget for rendering
- **Theming**:
  - `WidgetPackApplication` applies Material dynamic colors globally
  - Uses system accent colors when idle and album-art-derived Palette colors when playing
- **Settings**: `SettingsFragment` backed by `PreferenceFragmentCompat` to toggle visual features

## Tech Stack

- **Language**: Kotlin
- **UI**: App Widgets, RemoteViews, Material Components, Dynamic Color (Material You)
- **Media**: `NotificationListenerService`, `MediaController`, `MediaSession`, `PlaybackState`
- **Graphics**: `Palette` for color extraction, blurred album art backgrounds, custom progress ring drawing
- **Preferences**: Jetpack `Preference` library for settings

## Local Development

1. **Import project** into Android Studio (I used Otter 2 Feature Drop).
2. **Sync Gradle**; ensure you have the Android SDK for at least Android 13 (API 33) installed.
3. **Run** the app on a physical device or emulator that supports notification listener services and widgets.
4. **Grant notification access** when prompted (or via system Settings → Notifications → Notification access).
5. **Add the widget** to your home screen:
   - Long-press home screen → Widgets → find **Home Screen Widget Pack** → drag the music widget.
6. **Play music** in any media app (Spotify, YouTube Music, etc.) and verify that:
   - Artwork, title, and artist are shown
   - Play/pause/next/previous buttons work
   - Progress ring animates while playing

## Permissions & Notes

- **Notification access** (`BIND_NOTIFICATION_LISTENER_SERVICE`) is required so the widget can:
  - Read currently playing media information
  - Control playback via the active media session
- The widget only reacts to apps that expose proper media-style notifications with a `MediaSession`.
- For the best experience, test on Android 13+ where dynamic colors and system accent APIs are fully available.
- You may have to temporarily pause Play Protect and allow restricted settings for this app on first install. You can change them back after installation.