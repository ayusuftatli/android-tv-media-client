# Ororo TV — Planned Improvements

## 1. Resume Playback from Last Position

**Gap**: Watch progress is saved every 5 seconds but is never used to auto-resume.
This means every time you re-open a movie or episode, it starts from the beginning.

**Files to change**:
- `app/src/main/java/tv/ororo/app/ui/player/PlayerScreen.kt`
- `app/src/main/java/tv/ororo/app/ui/player/PlayerViewModel.kt`
- `app/src/main/java/tv/ororo/app/data/repository/WatchProgressRepository.kt`

**Approach**: On player launch, read the saved position from `WatchProgressRepository`
and seek ExoPlayer to that offset before starting playback.

---

## 2. Continue Watching Row on Home Screen

**Gap**: The home screen is a static 4-button menu (Search, Movies, TV Shows, Logout).
There is no way to quickly get back to something you were watching.

**Files to change**:
- `app/src/main/java/tv/ororo/app/ui/home/HomeScreen.kt`
- `app/src/main/java/tv/ororo/app/ui/home/HomeViewModel.kt`
- `app/src/main/java/tv/ororo/app/data/repository/WatchProgressRepository.kt`

**Approach**: Query the local watch progress store, join against movie/show metadata,
and display a horizontal "Continue Watching" row above the main menu buttons.
Only show items that are in-progress (not yet marked as completed).

---

## 3. Search Debouncing

**Gap**: `SearchViewModel` fires a network call on every single keystroke.
On a TV soft keyboard this creates excessive API calls and a laggy experience.

**Files to change**:
- `app/src/main/java/tv/ororo/app/ui/search/SearchViewModel.kt`

**Approach**: Add a 300ms `debounce` operator on the query `StateFlow`/`Flow`
before triggering the search API call.

---

## 10. Cache Expiration

**Gap**: The in-memory movie/show cache in `OroroRepository` never expires —
it only clears on logout. After a long session the data can become stale
without the user knowing.

**Files to change**:
- `app/src/main/java/tv/ororo/app/data/repository/OroroRepository.kt`

**Approach**: Store a timestamp alongside each cached list.
On the next fetch, if the timestamp is older than ~15 minutes, treat the cache
as invalid and make a fresh API call.
