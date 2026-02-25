# Ororo.tv Google TV App - Implementation Plan

## Context
Build a native Google TV app for the Ororo.tv streaming service, based on the API discovered in the Kodi plugin (`plugin.video.ororotv-4.0.1`). The app should be functional, lag-free, and easy to navigate with a D-pad. UI beauty is not a priority.

## Tech Stack
- **Language:** Kotlin
- **UI:** Jetpack Compose for TV (`androidx.tv.material3`)
- **Architecture:** MVVM + Repository pattern
- **DI:** Hilt
- **Networking:** Retrofit + OkHttp + kotlinx.serialization
- **Video:** Media3 ExoPlayer
- **Images:** Coil
- **Auth storage:** Preferences DataStore (session persistence without `EncryptedSharedPreferences`)

## API Reference
- **Base URL:** `https://front.ororo-mirror.tv/api/v2/`
- **Auth:** HTTP Basic (Base64 `user:password`)
- **Endpoints:** `GET /movies`, `GET /movies/{id}`, `GET /shows`, `GET /shows/{id}`, `GET /episodes/{id}`
- **Errors:** 401 = bad credentials, 402 = free limit reached

## Core Features
1. Login screen (email + password)
2. Browse movies (sort by title/added/year/rating, filter by genre)
3. Browse TV shows (sort by title/rating, filter by genre)
4. Show detail with seasons + episode list
5. Video playback with ExoPlayer + subtitle support
6. Search (client-side filtering of movies + shows)

## Project Structure
```
app/src/main/java/tv/ororo/app/
├── OroroApp.kt                    # Hilt Application
├── MainActivity.kt                # Single Activity entry point
├── data/
│   ├── api/
│   │   ├── OroroApiService.kt     # Retrofit interface (5 endpoints)
│   │   └── AuthInterceptor.kt     # OkHttp interceptor for Basic Auth
│   ├── model/
│   │   ├── dto/                   # API DTOs (MoviesResponseDto, MovieDto, ShowDto, EpisodeDto)
│   │   └── mapper/                # DTO -> domain model mapping
│   ├── domain/
│   │   └── model/                 # Movie, Show, Episode, Subtitle
│   └── repository/
│       ├── AuthRepository.kt      # Credential storage + validation
│       ├── MovieRepository.kt     # Movies list + detail
│       └── ShowRepository.kt      # Shows + episodes
├── di/
│   └── AppModule.kt               # Hilt module (Retrofit, OkHttp, repos)
└── ui/
    ├── navigation/
    │   ├── Screen.kt              # Sealed class of routes
    │   └── AppNavigation.kt       # NavHost
    ├── login/                     # LoginScreen + LoginViewModel
    ├── home/                      # HomeScreen (Movies/Shows/Search tabs)
    ├── movies/                    # MovieBrowseScreen, MovieDetailScreen + VMs
    ├── shows/                     # ShowBrowseScreen, ShowDetailScreen + VMs
    ├── search/                    # SearchScreen + SearchViewModel
    ├── player/                    # PlayerScreen + PlayerViewModel (ExoPlayer)
    └── components/                # ContentCard, SortFilterBar, LoadingIndicator
```

## Implementation Order

### Phase 1: Project Setup + Networking
- Create Android TV project, configure `build.gradle.kts` with all dependencies
- `AndroidManifest.xml` with `LEANBACK_LAUNCHER`, internet permission, touchscreen not required, `android.software.leanback` feature, and TV banner/icon assets
- Hilt setup (`OroroApp.kt`, `AppModule.kt`)
- Define DTO + domain models and mappers, Retrofit interface, `AuthInterceptor`, repositories

#### Success Criteria
- App installs and launches on Google TV emulator from the TV launcher row.
- A test call to `GET /movies` with valid credentials returns parsed DTOs and mapped domain models.
- Build passes with no unresolved DI bindings for network/repository layers.

### Phase 2: Login + Navigation
- `Screen.kt` sealed class, `AppNavigation.kt` NavHost
- `LoginScreen` + `LoginViewModel` (validates by calling API, stores session in Preferences DataStore)
- `MainActivity` routes to Login or Home based on saved credentials

#### Success Criteria
- Invalid credentials show an error state and keep user on Login.
- Valid credentials persist in DataStore and app routes to Home.
- App restart restores session and skips Login when credentials are still valid.

### Phase 3: Browse Screens
- `ContentCard` composable (poster via Coil + title)
- `SortFilterBar` (sort dropdown + genre chips)
- `MovieBrowseScreen` + VM (loads all movies, sorts/filters client-side, `TvLazyVerticalGrid`)
- `ShowBrowseScreen` + VM (same pattern)

#### Success Criteria
- Movies and shows load and render in TV-friendly grids with focusable cards.
- Sort and genre filters update visible results correctly for both content types.
- D-pad navigation moves predictably across rows/columns with no focus loss.

### Phase 4: Detail Screens
- `MovieDetailScreen` + VM (backdrop, info, Play button)
- `ShowDetailScreen` + VM (season tabs, episode list per season)

#### Success Criteria
- Selecting a movie opens detail with metadata and a working Play action.
- Selecting a show opens detail with season switching and episode list updates.
- Back navigation from detail returns to previous browse position.

### Phase 5: Search
- `SearchScreen` + VM (text field, client-side match across movies + shows)

#### Success Criteria
- Entering a query returns matching movies and shows in one results view.
- Empty query and no-result states are handled without crashes or blank focus.
- Selecting a result navigates to the correct detail screen.

### Phase 6: Video Playback
- `PlayerScreen` + VM: fetch streaming URL, build `MediaItem` with `SubtitleConfiguration` for each language
- Media3 `PlayerView` wrapped in `AndroidView` (D-pad controls built-in)
- Handle player lifecycle (release on dispose)

#### Success Criteria
- Movie and episode playback both start successfully from their Play actions.
- Subtitle tracks from API are available in player track selection and can be switched.
- Exiting player releases resources and re-entering playback does not leak/crash.

### Phase 7: Error Handling + Polish
- Loading/error states per screen (`UiState<T>` sealed class)
- 401 → redirect to login, 402 → show limit dialog
- Verify D-pad navigation works on all screens

#### Success Criteria
- All primary screens show deterministic loading, success, and error UI states.
- 401 consistently clears session and redirects to Login; 402 shows a clear limit message.
- End-to-end D-pad traversal works across login, browse, detail, search, and player entry.

## Build Configuration
- `minSdk = 21`, `targetSdk = 34`, `compileSdk = 34`
- `applicationId = "tv.ororo.app"`
- Plugins: android application, kotlin, kotlin serialization, hilt, ksp

## Key Technical Decisions
- **Client-side search/sort**: API returns all content in one call; filter in memory (same as Kodi plugin)
- **No Room DB**: No offline support needed; in-memory caching suffices
- **Separate DTO and domain models**: keep network response shape isolated from UI/domain; map via dedicated mappers
- **Subtitles**: Use `MediaItem.SubtitleConfiguration` with SRT URLs from API

## Verification
1. Build project in Android Studio, deploy to Google TV emulator (API 34 TV profile)
2. Login with Ororo.tv credentials → should navigate to Home
3. Browse movies → posters load, sort/filter works, D-pad navigates grid
4. Click movie → detail screen, click Play → video plays with subtitle tracks available
5. Browse shows → click show → seasons/episodes displayed → click episode → plays
6. Search → type query → results appear → click result → navigates to detail
7. Test D-pad on every screen (no focus traps, back button works)
