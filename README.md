# Android TV Media Client

Android TV application built with Kotlin and Jetpack Compose for TV, designed for browsing and playing media from authorized sources.

## Features
- TV-first UI with D-pad navigation
- Save movies and TV shows for quick access
- Weekly TMDB top-100 movies and TV series filtered to titles available on Ororo
- Media playback with AndroidX Media3 (ExoPlayer)
- HLS playback support
- Dependency injection with Hilt
- Network stack with Retrofit + OkHttp
- Local preferences with DataStore

## TMDB setup

The Trending Movies and Trending TV screens use TMDB's API Read Access Token. Create an API
credential in your TMDB account, then add the token to the untracked
`local.properties` file:

```properties
TMDB_READ_ACCESS_TOKEN=your_api_read_access_token
```

For automated builds, set the `TMDB_READ_ACCESS_TOKEN` environment variable
instead. The first visit to each section resolves its weekly top 100 to Ororo's
IMDb IDs. Movie and TV results are cached separately for 24 hours and are cleared
by the app's **Clear cache** action.

TMDB attribution and its approved logo are available under **Settings → About &
data attribution**. The logo asset is the unmodified TMDB primary logo published
on TMDB's official logos and attribution page.

## Tech Stack
- Kotlin (JVM 17)
- Android SDK (`minSdk 21`, `targetSdk 34`)
- Jetpack Compose + Compose for TV
- Media3 ExoPlayer
- Hilt
- Retrofit / OkHttp

## Build APK
1. Open the project in Android Studio.
2. Build debug APK:
   - `Build > Build Bundle(s) / APK(s) > Build APK(s)`
3. Or from terminal:
   - `./gradlew :app:assembleDebug`
4. APK output:
   - `app/build/outputs/apk/debug/app-debug.apk`

## Install on Android TV

### Option 1: Install with ADB (recommended)
1. On your TV, enable Developer options and USB debugging.
2. Make sure your computer and TV are on the same network.
3. Get your TV IP address.
4. Connect and install:
   - `adb connect <TV_IP>:5555`
   - `adb install -r app/build/outputs/apk/debug/app-debug.apk`

### Option 2: Copy APK to TV and install manually
1. Copy `app-debug.apk` to a USB drive (or cloud storage).
2. Move the APK to your TV.
3. Open it with a file manager on the TV.
4. Allow installs from unknown sources for that file manager.
5. Install the APK.

## Legal
Use this project only with content you are legally allowed to access. Do not use it to access or distribute copyrighted content without permission.

## Disclaimer
This repository does not host or provide media content.
