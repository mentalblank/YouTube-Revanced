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
