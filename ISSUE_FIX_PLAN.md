# Ororo TV Issue Fix Plan

## Goal
Stabilize the current implementation so it builds, handles auth/session failures correctly, and matches the behavior defined in `PLAN.md`.

## Phase 1: Build Blocker (Must Fix First)

### 1.1 Fix resource linking failure
- Problem: `:app:processDebugResources` fails because `adaptive-icon` is used while `minSdk = 21`.
- File: `app/src/main/res/mipmap-hdpi/ic_launcher.xml`
- Action:
  - Move adaptive icon resources to API 26+ resource folders (`mipmap-anydpi-v26`).
  - Provide backward-compatible launcher resources for API 21-25.
- Success criteria:
  - `./gradlew :app:assembleDebug` passes.

## Phase 2: Auth and Session Correctness

### 2.1 Enforce global 401 handling
- Problem: 401 currently shows local errors, but does not always clear session and redirect to login.
- Files:
  - `app/src/main/java/tv/ororo/app/ui/movies/MovieDetailViewModel.kt`
  - `app/src/main/java/tv/ororo/app/ui/shows/ShowDetailViewModel.kt`
  - `app/src/main/java/tv/ororo/app/ui/player/PlayerViewModel.kt`
- Action:
  - Introduce a shared auth-error path (clear credentials + clear cache + navigate to login).
  - Ensure all API entry points use this behavior consistently.
- Success criteria:
  - Any 401 from any screen logs user out and routes to Login.

### 2.2 Validate persisted session on app start
- Problem: startup checks only for stored credentials, not whether they are still valid.
- File: `app/src/main/java/tv/ororo/app/MainActivity.kt`
- Action:
  - Validate session with a lightweight API check before entering Home.
  - Fall back to Login if validation fails.
- Success criteria:
  - Expired/invalid persisted credentials do not land user in broken Home flow.

### 2.3 Improve HTTP error classification
- Problem: code relies on string matching (`contains("401")`, `contains("402")`).
- Files:
  - `app/src/main/java/tv/ororo/app/ui/login/LoginViewModel.kt`
  - `app/src/main/java/tv/ororo/app/ui/movies/MovieDetailViewModel.kt`
  - `app/src/main/java/tv/ororo/app/ui/shows/ShowDetailViewModel.kt`
  - `app/src/main/java/tv/ororo/app/ui/player/PlayerViewModel.kt`
- Action:
  - Classify errors via typed Retrofit/HTTP exceptions and actual status codes.
- Success criteria:
  - 401 and 402 are detected reliably and always mapped to correct UI behavior.

## Phase 3: UX and Feature-Plan Gaps

### 3.1 Add missing movie sort: `added`
- Problem: plan requires `title/added/year/rating`, implementation has no `added` sort.
- Files:
  - `app/src/main/java/tv/ororo/app/ui/components/SortFilterBar.kt`
  - `app/src/main/java/tv/ororo/app/ui/movies/MovieBrowseViewModel.kt`
- Action:
  - Extend sort options with `ADDED`.
  - Sort by `updatedAt` descending (or mapped date field) in movie list.
- Success criteria:
  - Movies screen supports all planned sort modes.

### 3.2 Add explicit search error state
- Problem: search data load failures are swallowed and appear as empty results.
- File: `app/src/main/java/tv/ororo/app/ui/search/SearchViewModel.kt`
- Action:
  - Add `error` to `SearchUiState` and surface retry/error UI in `SearchScreen`.
- Success criteria:
  - Search distinguishes “no results” from “load failed”.

## Phase 4: Repo Hygiene and Safety Nets

### 4.1 Ignore module build outputs
- Problem: `.gitignore` does not ignore `app/build`.
- File: `.gitignore`
- Action:
  - Add `app/build/`.
- Success criteria:
  - No generated module build artifacts appear in git status.

### 4.2 Add minimal regression tests
- Problem: no unit/instrumentation tests for auth/session critical flows.
- Action:
  - Add tests for:
    - login success/failure status mapping
    - 401 -> logout behavior
    - startup session validation routing
- Success criteria:
  - `./gradlew :app:testDebugUnitTest` runs with baseline coverage on critical logic.

## Verification Checklist
1. `./gradlew :app:assembleDebug` succeeds.
2. Login with invalid credentials shows proper error and stays on Login.
3. Login with valid credentials navigates to Home and persists session.
4. For any endpoint, forced 401 clears session and redirects to Login.
5. 402 shows subscription-required messaging.
6. Movies sort includes `Added` and behaves correctly.
7. Search shows explicit load errors (not silent empty state).
8. `git status` is clean of build outputs.
