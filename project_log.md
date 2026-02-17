# Project Log — YouTube ReVanced VOT Integration

## 2026-02-17 — US-001: Clone YouTube-ReVanced and verify base build

- Cloned https://github.com/mentalblank/YouTube-Revanced into dev/revanced-vot/
- Created feature branch `feature/vot-translation`
- Analyzed build system:
  - `build.sh` — main build script (bash), parses TOML config, downloads ReVanced CLI/patches, patches APK
  - `utils.sh` — helper functions (TOML parsing, APK downloading, patching)
  - `config.toml` — build configuration (app list, patches, versions)
  - GitHub Actions workflow: `.github/workflows/build.yml` on `ubuntu-latest` with Java 17
- Build requirements: Linux x86_64, Java 17, jq, zip, GitHub token (for APK download)
- Build is CI-only (requires `tq-x86_64` binary and linux environment)
- Project structure verified: build scripts valid and parseable

## 2026-02-17 - US-003: Create VOT module package structure

### What was done
- Created isolated VOT module at `vot-module/src/main/java/app/revanced/integrations/youtube/vot/`
- Package: `app.revanced.integrations.youtube.vot` (follows ReVanced integrations conventions)
- Sub-packages with placeholder classes:
  - `api/` — YandexTranslationClient, YandexSignature (API communication + HMAC signing)
  - `player/` — TranslationAudioManager (Shadow Player), AudioDucking
  - `ui/` — TranslationButton (player toggle)
  - `settings/` — VotSettings (language, duck volume, auto-translate)
  - `proto/` — TranslationProto (Protobuf serialization)
- Root: VotModule.java (entry point, initialization)
- All classes compile independently with javac
- 29 tests pass (package structure, declarations, compilation, class loading)

## 2026-02-17 - US-004: Implement Protobuf models for Yandex Translation API

### Changes
- Fully implemented `TranslationProto.java` with manual protobuf wire-format serialization
- **TranslationRequest**: all fields from VOT protocol (url, language, responseLanguage, duration, firstRequest, deviceId, forceSourceLang, bypassCache, useLivelyVoice, videoTitle, etc.)
- **TranslationResponse**: all fields (url, status, duration, translationId, language, message, isLivelyVoice, remainingTime, shouldRetry, etc.)
- **VideoTranslationStatus**: enum constants (FAILED=0, FINISHED=1, WAITING=2, LONG_WAITING=3, PART_CONTENT=5, AUDIO_REQUESTED=6, SESSION_REQUIRED=7)
- **ProtoReader**: wire-format decoder supporting varint, double, string, bytes, skip
- Convenience methods: `buildTranslationRequest()`, `parseTranslationResponse()`
- `equals()` and `toString()` on both Request and Response
- Status helper methods: `isFinished()`, `isWaiting()`

### Tests
- Created `tests/test_protobuf.sh` with 42 total assertions:
  - Structure tests (class existence, field presence, method signatures)
  - Compilation test
  - Full round-trip serialization test (27 sub-tests):
    - Request encode→decode with all field types (string, double, bool)
    - Response encode→decode with all field types
    - Empty message round-trip
    - Status helper methods
    - Convenience builder methods
    - equals() verification

### Field Numbers (from @vot.js protocol)
- Request: url=3, deviceId=4, firstRequest=5, duration=6, language=8, responseLanguage=14, etc.
- Response: url=1, duration=2, status=4, remainingTime=5, translationId=7, language=8, etc.

## 2026-02-17 - US-005: Implement YandexSignature (HMAC signing)

### What was done
- Implemented `YandexSignature` class in `vot-module/src/main/java/.../vot/api/`
- HMAC-SHA256 signing using key from reference (`bt8xH3VOlb4mqf0nqAibnDOoiPlXsisf`)
- Methods: `sign(byte[])`, `sign(String)`, `getVtransSignature()`, `getTokenSignature()`, `generateUUID()`, `bytesToHex()`
- Token signature format: `{hmac}:{uuid}:{path}:{componentVersion}` matching reference
- Component version: `25.6.0.2259` from @vot.js config

### Tests
- `YandexSignatureTest.java` (JUnit-style) + `YandexSignatureTestRunner.java` (standalone runner)
- 10 test cases with known test vectors generated from reference JS implementation
- All tests passing

### Reference
- Key and algorithm from: `@vot.js/shared/dist/secure.js` → `signHMAC("SHA-256", config.hmac, data)`
- Config from: `@vot.js/shared/dist/data/config.js` → `hmac: "bt8xH3VOlb4mqf0nqAibnDOoiPlXsisf"`

## US-006: Implement YandexTranslationClient (API communication)
**Date:** 2026-02-17

### Changes
- Implemented full `YandexTranslationClient` in `vot-module/src/main/java/.../api/YandexTranslationClient.java`
- HTTP client using `HttpURLConnection` with proper headers (Content-Type, Vtrans-Signature, Sec-Vtrans-Token, User-Agent, Origin, Referer)
- `requestTranslation()` sends signed protobuf request, parses protobuf response
- Polling with exponential backoff (1s initial, 1.5x multiplier, 5s max, 30 attempts max)
- `TranslationResult` class with status helpers (isSuccess, isPending, isFailed)
- `TranslationException` with HTTP status and translation status codes
- `HttpConnectionFactory` interface for dependency injection / testing
- `Sleeper` interface to avoid real delays in tests

### Tests
- `YandexTranslationClientTestRunner.java` — 41 tests, all passing
- Covers: success, polling (pending→success), failed translation, HTTP errors (429), network errors, failure during polling, status helpers, protobuf body verification, constants validation
- Uses mock HTTP connections (no real network calls)

### Reference
- Headers and API URL from: `@vot.js/shared/dist/data/config.js`
- Polling pattern from: SmartTube reference implementation pattern

## US-007: Implement Shadow Player (TranslationAudioManager) — 2026-02-17

### What was done
- Fully implemented `TranslationAudioManager` in `vot-module/src/main/java/.../player/TranslationAudioManager.java`
- State machine: IDLE → LOADING → READY → PLAYING ⇄ PAUSED, with ERROR state
- Methods: `loadAudio(url)`, `play()`, `pause()`, `stop()`, `seekTo(ms)`, `release()`
- State listener interface for callbacks on state transitions and errors
- Duck volume and translation volume management with clamping
- Guard rails: IllegalStateException for invalid state transitions, post-release usage
- Designed for ExoPlayer swap-in (comments mark where real ExoPlayer calls go)

### Tests
- `TranslationAudioManagerTest.java` — 42 assertions, all passing
- Covers: initial state, load, play, pause, resume, stop, seek, release
- Edge cases: play before load, pause when not playing, seek when idle, null/empty URL, double release, reload while playing, volume clamping, state listener

### Files changed
- `vot-module/src/main/java/.../player/TranslationAudioManager.java` — full rewrite
- `vot-module/src/test/java/.../player/TranslationAudioManagerTest.java` — new

## US-008: Implement audio synchronization logic (2026-02-17)

### What was implemented
- Added `syncWithMainPlayer(positionMs, isPlaying)` method to TranslationAudioManager
  - Syncs play/pause state: shadow pauses when main pauses, resumes when main resumes
  - Corrects position drift: seeks shadow player when drift exceeds 500ms threshold
  - Returns boolean indicating whether a seek correction was performed
- Created `AudioSyncController` class for periodic sync checks
  - Configurable sync interval (default 1000ms)
  - `MainPlayerProvider` interface to abstract main player position/state
  - `Scheduler` interface for testable periodic execution
  - Tracks sync count and seek correction count
- Added SYNC_THRESHOLD_MS constant (500ms) to TranslationAudioManager

### Tests (36 new tests in AudioSyncTest.java)
- Pause/resume sync: shadow follows main player state
- Seek correction: drift > 500ms triggers seek, ≤ 500ms does not
- Boundary: exact 500ms = no seek, 501ms = seek
- Forward and backward seek handling
- Combined pause + seek scenario
- AudioSyncController: periodic sync counting, seek counting, start/stop, no-scheduler error
- Controller pause/resume propagation

### Files changed
- `vot-module/src/main/java/.../player/TranslationAudioManager.java` — added syncWithMainPlayer method
- `vot-module/src/main/java/.../player/AudioSyncController.java` — new
- `vot-module/src/test/java/.../player/AudioSyncTest.java` — new (36 tests)

## 2026-02-17 — US-009: Implement audio ducking (original volume reduction)

- Created `AudioDuckingManager` class in `vot-module/src/main/java/.../player/`
- Features: startDucking(), stopDucking(), setDuckVolume(float), smooth linear fade
- Default duck volume: 0.15, fade duration: 300ms over configurable steps
- VolumeApplier interface for applying volume to main player
- FadeScheduler interface for testable fade animation
- Without scheduler: instant snap to target (fallback)
- 44 unit tests covering: default values, start/stop, configurable volume, clamping, fade steps, volume applier callbacks, immediate volume set, scheduler integration, multiple cycles
- All tests pass

## 2026-02-17 - US-010: Implement VOT Translation Coordinator
- Created VotTranslationCoordinator in root vot package
- Orchestrates full flow: request → poll → load audio → sync → duck
- State machine: IDLE → REQUESTING → LOADING → PLAYING, with ERROR transitions
- startTranslation(videoId, targetLanguage) runs API on background thread (Executor)
- stopTranslation() stops audio, ducking, sync, restores IDLE
- onVideoChanged() automatically stops current translation
- Thread-safe with synchronized state transitions
- Executor and MainThreadPoster interfaces for testability
- 25 unit tests with mocked dependencies: state transitions, error handling, video change, double start, null/empty validation
- All tests pass

## 2026-02-17 - US-011: Implement VOT settings/preferences
- Rewrote VotSettings class with SharedPreferences pattern (keys, pref name, singleton)
- Added isEnabled/setEnabled, getTargetLanguage/setTargetLanguage, getDuckVolume/setDuckVolume
- Default values: enabled=false, targetLanguage='ru', duckVolume=0.15
- Input validation: language null/empty rejected, duck volume clamped 0-1
- Created VotSettingsTest with 24 tests (all passing)
