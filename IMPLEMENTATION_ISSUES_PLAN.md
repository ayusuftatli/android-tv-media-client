# Ororo TV App - Issue Implementation Plan

## Scope
This plan covers four issues:
1. Player back-button behavior with visible controls
2. Search-screen Enter key behavior on the virtual keyboard
3. Subtitle behavior parity with Kodi reference
4. Watched-state tracking for completed movies and episodes

## Constraints and Decisions
- Keep current app architecture (`MVVM + Repository + DataStore`).
- Do not add Room for this scope; use Preferences DataStore for watched/subtitle settings.
- Watched completion rule: mark as watched when playback reaches `>= 95%` or playback ends.
- Distinguish content keys by type to avoid collisions:
  - `movie:<id>`
  - `episode:<id>`

## Phase 1: Fix Back Button in Player --- fixed. 

### Problem
In playback, pressing Back while controls are visible exits the screen instead of hiding controls first.

### Implementation
- File: `app/src/main/java/tv/ororo/app/ui/player/PlayerScreen.kt`
- Add explicit Back handling:
  - If `PlayerView` controller is visible: call `hideController()` and consume event.
  - If controller is not visible: call existing `onBack()`.
- Handle both:
  - Compose back (`BackHandler`)
  - Remote key events (`KEYCODE_BACK`) in existing key listeners
- Keep focus on `PlayerView` after hiding/showing controls.

### Acceptance Criteria
- First Back press while controls are visible hides controls.
- Back navigation occurs only when controls are already hidden.
- Existing left/right seek and play/pause behavior still works.

## Phase 2: Search Enter Key Should Dismiss Keyboard - still notworking

### Problem
Pressing Enter/Search on the on-screen keyboard does nothing in Search view.

### Implementation
- File: `app/src/main/java/tv/ororo/app/ui/search/SearchScreen.kt`
- Add keyboard/focus control:
  - `LocalSoftwareKeyboardController`
  - `LocalFocusManager`
- Update `KeyboardActions(onSearch = ...)` to:
  - Hide virtual keyboard
  - Clear focus from text field
- Add fallback key handling for `KEYCODE_ENTER` / `KEYCODE_NUMPAD_ENTER` on TV IMEs that bypass `onSearch`.

### Acceptance Criteria
- Pressing Enter/Search closes the virtual keyboard.
- Query and results stay visible (no accidental navigation/reset).

## Phase 3: Subtitle Behavior (Kodi-Informed) works but needs changings.

### Kodi Reference (already in repo)
- `plugin.video.ororotv-4.0.1/default.py:188-193`: selects preferred subtitle language at playback start.
- `plugin.video.ororotv-4.0.1/default.py:733-764`: exposes available subtitle tracks and downloads selected subtitle.

### Current Gap in Android App
- Subtitle tracks are attached, but preferred-language selection and subtitle enable/disable behavior are incomplete.
- Subtitle MIME type is hardcoded to SRT.

### Implementation
- Files:
  - `app/src/main/java/tv/ororo/app/ui/player/PlayerViewModel.kt`
  - `app/src/main/java/tv/ororo/app/ui/player/PlayerScreen.kt`
  - `app/src/main/java/tv/ororo/app/data/repository/SessionRepository.kt` (or new `SubtitlePreferencesRepository`)
- Add subtitle preferences:
  - `subtitlesEnabled` (default `true`)
  - `preferredSubtitleLang` (default device language)
- On player load:
  - Preselect subtitle matching preferred language when available.
  - If disabled, do not auto-select text track.
- Improve subtitle config generation:
  - Infer MIME by URL extension (`.srt`, `.vtt`; fallback to unknown/text)
  - Keep all available tracks exposed to Media3 controller
- Add minimal user control in player:
  - Choose subtitle language or disable subtitles
  - Persist selection as new preferred language

### Acceptance Criteria
- Preferred subtitle language auto-selects when available.
- User can switch subtitle language and disable subtitles.
- Playback does not fail when subtitles are missing or track format differs.

## Phase 4: Watched Tracker for Movies and Episodes - done

### Problem
App has no watched-state persistence, so completed content is not identified in UI.

### Implementation
- New repository:
  - `app/src/main/java/tv/ororo/app/data/repository/WatchProgressRepository.kt`
  - Backed by DataStore (JSON payload or keyed preferences)
- New model:
  - `WatchState(contentKey, positionMs, durationMs, completed, updatedAt)`
- Player integration:
  - Files:
    - `app/src/main/java/tv/ororo/app/ui/player/PlayerViewModel.kt`
    - `app/src/main/java/tv/ororo/app/ui/player/PlayerScreen.kt`
  - Persist progress periodically (e.g., every 5-10s) and on dispose/end.
  - Mark completed if ended or `position/duration >= 0.95`.
- UI integration:
  - `app/src/main/java/tv/ororo/app/ui/components/ContentCard.kt` (watched badge/check overlay)
  - `app/src/main/java/tv/ororo/app/ui/movies/MovieBrowseViewModel.kt`
  - `app/src/main/java/tv/ororo/app/ui/shows/ShowDetailViewModel.kt`
  - `app/src/main/java/tv/ororo/app/ui/search/SearchViewModel.kt`
  - Show watched indicators for:
    - Movie cards
    - Episode rows
    - Search results

### Acceptance Criteria
- Completing a movie or episode marks it as watched.
- Returning to browse/detail/search screens shows watched state.
- Partially watched content below threshold is not marked watched.

## Phase 5: Verification and Regression Checks

### Functional QA
1. Start playback, show controls, press Back -> controls hide, playback continues.
2. Press Back again with controls hidden -> exit player.
3. In Search, press Enter/Search on virtual keyboard -> keyboard hides.
4. Start playback with multiple subtitle languages -> preferred language auto-selected.
5. Change subtitle language -> new selection persists for next playback.
6. Finish movie/episode -> watched indicator appears in relevant lists/details.

### Test Coverage (minimum)
- Unit tests:
  - watched completion threshold calculation
  - content key mapping (`movie:<id>` vs `episode:<id>`)
  - subtitle preference selection fallback
- UI/instrumentation tests:
  - player back behavior contract
  - search Enter action contract

## Delivery Order
1. Phase 1 (Player Back fix)
2. Phase 2 (Search Enter fix)
3. Phase 3 (Subtitle parity)
4. Phase 4 (Watched tracker)
5. Phase 5 (tests + regression pass)
