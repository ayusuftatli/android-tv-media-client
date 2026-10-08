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
instead. Trending Movies and Trending TV resolve TMDB's weekly top 100 to titles
available on Ororo. On a first visit, matches appear progressively in ranking order.
The catalog, ranking pages, and IMDb lookups overlap, with up to eight TMDB requests
in flight across both screens and a shared ceiling of 38 request starts per second.
A `429` response pauses new requests from both screens for the server's retry period.

Rankings remain fresh for 24 hours; Ororo availability is rechecked after 15 minutes.
Each completed list is cached in memory and on disk, including the title data needed
to render cards immediately after an app restart. Expired lists stay visible during
background refresh and update automatically when it completes. Refresh failures keep
existing results visible with a cached-results notice. First-load partial results
are never persisted as a fresh snapshot. Posters use the existing Coil image cache.

Successful TMDB-to-IMDb mappings survive ranking refreshes, so only new or unresolved
titles need another lookup. Older ranking-only caches are upgraded on the next
successful load. Title snapshots are scoped to an opaque login identifier: logout
invalidates them, and a subsequent login cannot reuse them. Public ID mappings remain
reusable. **Clear cache** removes snapshots, rankings, and mappings and cancels pending
trending loads so they cannot recreate cleared data. Overlapping callers share a load;
cancelling one caller leaves the load running while another still needs it.

The deterministic loading benchmark in `TrendingLoadingTest` uses 200 ms for every
API response and 100 matching titles: first results arrive at approximately 504 ms,
and completion takes 3,102 ms versus 6,078 ms for the previous sequential-page,
four-concurrent-lookup pipeline. Both issue 105 TMDB requests. These timings exclude
real network variability, device rendering, and poster downloads.

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

For an optimized release build, run `./gradlew :app:assembleRelease`. Release builds
use R8 code optimization and resource shrinking. The output at
`app/build/outputs/apk/release/app-release-unsigned.apk` must be signed before installation.

Run regression tests and Android lint with:
`./gradlew :app:testDebugUnitTest :app:lintDebug`.

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
