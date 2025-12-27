## Home Screen Widget Pack

Android 13+ widget pack.

## Setup

1. Import into Android Studio and sync Gradle
2. Run on Android 13+ device/emulator
3. Grant notification access when prompted (might also need Restricted Settings access as well as Play Protect bypass)
4. Add widget: Long-press home screen -> Widgets -> Home Screen Widget Pack
5. Play music in any media app to test Now Playing widget

## Requirements

- Android 13+ (API 33+)
- Notification access permission (for reading media notifications for Now Playing widget)
- Media apps that expose MediaSession notifications (for Now Playing widget)

## Tech Stack

Kotlin, NotificationListenerService, MediaController, Palette
