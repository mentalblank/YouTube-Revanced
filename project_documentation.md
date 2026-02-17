# YouTube ReVanced VOT — Project Documentation

## Overview

This project integrates Yandex Voice-Over Translation (VOT) into YouTube ReVanced, enabling synchronized voice-over translation of YouTube videos.

## Base Project

- **Repository**: https://github.com/mentalblank/YouTube-Revanced
- **Purpose**: Automated ReVanced APK build system
- **License**: GPL-3.0

## Build System

### Requirements
- **OS**: Linux x86_64 (Ubuntu latest) — CI-only build
- **Java**: OpenJDK 17 (Zulu distribution)
- **Tools**: jq, zip, bash
- **Environment**: `GITHUB_TOKEN` for APK source downloads

### Build Command
```bash
bash build.sh config.toml
```

### How It Works
1. `build.sh` parses `config.toml` for app definitions (YouTube, YouTube Music, etc.)
2. Downloads ReVanced CLI, patches, and integrations from GitHub releases
3. Downloads stock APK from APKMirror
4. Applies ReVanced patches using the CLI
5. Signs the APK with `ks.keystore`
6. Outputs patched APK to `build/` directory

### Key Files
| File | Purpose |
|------|---------|
| `build.sh` | Main build orchestrator |
| `utils.sh` | Helper functions (TOML parsing, downloads, patching) |
| `config.toml` | Build configuration (apps, patches, versions) |
| `.github/workflows/build.yml` | CI/CD workflow |
| `ks.keystore` | APK signing keystore |
| `bin/toml/tq-x86_64` | TOML query tool (linux binary) |

### CI/CD
- GitHub Actions workflow: `.github/workflows/build.yml`
- Triggers: manual dispatch or workflow_call
- Runs on: `ubuntu-latest`
- Creates GitHub releases with built APKs

---

## VOT Reference Implementation Analysis

> Based on: [voice-over-translation](https://github.com/ilyhalight/voice-over-translation) (browser extension)
> and its dependency [@vot.js](https://www.npmjs.com/package/@vot.js/core) (Yandex VOT client library)

### Architecture Overview

The VOT system consists of these main components:

1. **VOT Client** (`@vot.js/core`) — Yandex API communication (Protobuf + HMAC signing)
2. **Translation Handler** (`src/core/translationHandler.ts`) — Orchestrates translation requests, polling, retries
3. **Audio Ducking** (`src/videoHandler/modules/ducking.ts`) — Smart volume reduction of original audio
4. **Translation Module** (`src/videoHandler/modules/translation.ts`) — Ties everything together
5. **Audio Player** — Plays translated audio in sync with video (browser `<audio>` element)

---

### Protobuf Request/Response Format

**Library**: `@vot.js/shared/protos/yandex.js` (uses `@bufbuild/protobuf/wire`)
**Encoder/Decoder**: `@vot.js/core/dist/protobuf.js` (`YandexVOTProtobuf` class)

#### VideoTranslationRequest

Protobuf-encoded request sent to Yandex API:

| Field | Type | Proto Field # | Description |
|-------|------|---------------|-------------|
| `url` | string | 3 | Video URL (e.g., `https://youtu.be/VIDEO_ID`) |
| `deviceId` | string? | 4 | Optional device identifier |
| `firstRequest` | bool | 5 | Whether this is the first request for this video |
| `duration` | double | 6 | Video duration in seconds (default: 343) |
| `unknown0` | int32 | 7 | Always 1 |
| `language` | string | 8 | Source language code (e.g., "en") |
| `forceSourceLang` | bool | 9 | Force use specified source language |
| `unknown1` | int32 | 10 | Always 0 |
| `translationHelp` | repeated | 11 | Array of `{target, targetUrl}` hints |
| `wasStream` | bool | 13 | Whether this was previously a stream |
| `responseLanguage` | string | 14 | Target language code (e.g., "ru") |
| `unknown2` | int32 | 15 | Always 1 |
| `unknown3` | int32 | 16 | Always 2 |
| `bypassCache` | bool | 17 | Skip translation cache |
| `useLivelyVoice` | bool | 18 | Use "lively" (more natural) voice |
| `videoTitle` | string | 19 | Video title for context |

#### VideoTranslationResponse

| Field | Description |
|-------|-------------|
| `status` | Translation status enum (see below) |
| `url` | Audio URL (when translation is ready) |
| `remainingTime` | Estimated seconds until translation completes |
| `translationId` | ID to track this translation |
| `message` | Human-readable status message |

#### Translation Status Enum (`VideoTranslationStatus`)

| Value | Name | Meaning |
|-------|------|---------|
| 0 | `WAITING` | Translation in queue |
| 1 | `FINISHED` | Translation complete, URL available |
| 2 | `LONG_WAITING` | Translation taking longer than usual |
| 3 | `FAILED` | Translation failed |
| 4 | `PART_CONTENT` | Partial content available |
| 5 | `AUDIO_REQUESTED` | Server needs client-side audio extraction |
| 6 | `SESSION_REQUIRED` | Auth required |

#### Session Request/Response

Sessions are created per-module (e.g., "video-translation"):

**Request**: `{uuid: string, module: string}` — Protobuf encoded
**Response**: `{secretKey: string, expires: number}` — Session key + TTL

#### Other Protobuf Messages

- `SubtitlesRequest`: `{url, language}`
- `StreamTranslationRequest`: `{url, language, responseLanguage, unknown0: 1, unknown1: 0}`
- `StreamPingRequest`: `{pingId}`
- `VideoTranslationCacheRequest`: `{url, duration, language, responseLanguage}`
- `VideoTranslationAudioRequest`: `{url, translationId, audioInfo | partialAudioInfo}` — for uploading client-extracted audio

---

### HMAC Signing (Vtrans-Signature)

**Source**: `@vot.js/shared/dist/secure.js`

#### Session Creation Signature

When creating a session, the request body is signed:

```
Vtrans-Signature: HMAC-SHA-256(body, hmacKey)
```

- **HMAC Key**: `"bt8xH3VOlb4mqf0nqAibnDOoiPlXsisf"` (hardcoded in `@vot.js/shared/dist/data/config.js`)
- **Algorithm**: HMAC-SHA-256
- **Input**: Raw Protobuf request body bytes
- **Output**: Hex-encoded signature string

#### Authenticated Request Headers (SecYa)

For all API requests after session creation, three headers are used:

```
Vtrans-Signature: HMAC-SHA-256(requestBody, hmacKey)
Sec-Vtrans-Sk: <session.secretKey>
Sec-Vtrans-Token: <tokenSign>:<uuid>:<path>:<componentVersion>
```

Where:
- `tokenSign` = `HMAC-SHA-256(utf8("<uuid>:<path>:<componentVersion>"), hmacKey)` (hex-encoded)
- `componentVersion` = `"25.6.0.2259"` (from config)
- `path` = API endpoint path (e.g., `/video-translation/translate`)

#### Signing Algorithm (`getSignature`)

```javascript
async function getSignature(body) {
  const key = await crypto.subtle.importKey(
    "raw", utf8Encode(hmacKey),
    { name: "HMAC", hash: { name: "SHA-256" } },
    false, ["sign"]
  );
  const signature = await crypto.subtle.sign("HMAC", key, body);
  return hexEncode(new Uint8Array(signature));
}
```

#### API Endpoints

| Path | Method | Purpose |
|------|--------|---------|
| `/session/create` | POST | Create authenticated session |
| `/video-translation/translate` | POST | Request video translation |
| `/video-translation/audio` | PUT | Upload client-extracted audio |
| `/video-translation/fail-audio-js` | PUT | Report audio extraction failure |
| `/video-translation/cache` | POST | Check translation cache |
| `/video-subtitles/get-subtitles` | POST | Get video subtitles |
| `/stream-translation/translate-stream` | POST | Stream translation |
| `/stream-translation/ping-stream` | POST | Keep stream alive |

**Base Host**: `api.browser.yandex.ru` (HTTPS)

---

### Shadow Player Architecture (Audio Player)

In the browser extension implementation, the "Shadow Player" is simply a second `<audio>` HTML element that plays the translated audio track alongside the original video.

#### Key Components

**Source files**:
- `src/videoHandler/modules/translation.ts` — `updateTranslation()`, `createPlayer()`, `setupAudioSettings()`
- `src/videoHandler/modules/init.ts` — Player initialization

#### How It Works

1. **Translation Requested**: User clicks VOT button → `translateFunc()` called
2. **Audio URL Received**: Yandex API returns an audio URL (MP3/OGG on S3)
3. **Player Created**: `this.createPlayer()` creates an audio player instance
4. **Source Set**: `this.audioPlayer.player.src = audioUrl`
5. **Player Initialized**: `this.audioPlayer.init()` — sets up event listeners, sync logic
6. **Audio Settings Applied**: `setupAudioSettings()` — sets volume, starts ducking

#### Audio Player Properties

The audio player wraps an HTMLMediaElement (`<audio>`) with:
- `player.src` — Audio source URL
- `player.volume` — Translation audio volume (0-1, configurable via `defaultVolume` setting)
- `player.currentSrc` — Currently loaded URL
- `player.audio` / `player.audioElement` — The underlying media element
- `player.getAudioRms()` — RMS level of current audio (for smart ducking)
- `player.analyser` — Web Audio API AnalyserNode (for RMS computation fallback)

#### Audio URL Validation

Before playing, audio URLs are validated via HEAD/Range request:
```javascript
// Try HEAD first (for S3-compatible hosts)
const response = await GM_fetch(audioUrl, { method: "HEAD" });
// If invalid, re-request translation to get a fresh URL
```

#### Audio URL Proxying

Audio URLs can be proxied through a worker for bypassing restrictions:
- `proxifyYandexAudioUrl()` — Rewrites URL through proxy worker
- `unproxifyYandexAudioUrl()` — Extracts original URL
- Proxy host: `vot-worker.toil.cc`

#### Sync Strategy

The browser extension relies on the `<audio>` element's native sync with the video element:
- Both elements share the same timeline (video position → audio position)
- The `audioPlayer.init()` method sets up position tracking
- On video seek, the audio player seeks to match

For the **Android/ReVanced port**, we'll need to implement sync via ExoPlayer:
- Create a second ExoPlayer instance (Shadow Player)
- Poll video position periodically
- If `|videoPos - audioPos| > 500ms`, seek the audio player
- Use `ExoPlayer.seekTo()` for correction

---

### Audio Sync Strategy

#### Target: <500ms drift between video and translation audio

#### Browser Implementation

The browser extension syncs by:
1. Listening to video element events (`play`, `pause`, `seeked`, `timeupdate`)
2. When video plays → audio plays
3. When video pauses → audio pauses
4. When video seeks → audio seeks to same position
5. Periodic position check during playback

#### Android Port Strategy (Planned)

For ExoPlayer-based implementation:
1. **Periodic sync check**: Every 200-500ms, compare positions
2. **Drift detection**: If `|videoPlayer.currentPosition - audioPlayer.currentPosition| > 500ms`
3. **Correction**: `audioPlayer.seekTo(videoPlayer.currentPosition)`
4. **Event-based sync**: Listen to `Player.Listener` for state changes
5. **Playback rate matching**: Both players use same playback speed
6. **Buffer management**: Pre-buffer translation audio to reduce sync delays

#### Key Sync Events
- `onPlaybackStateChanged` → Start/stop audio player
- `onPositionDiscontinuity` → Seek audio player (video seek detected)
- `onPlaybackParametersChanged` → Match playback speed
- `onPlayerError` → Handle audio player errors gracefully

---

### Audio Ducking

**Source**: `src/videoHandler/modules/ducking.ts`

#### Overview

Audio ducking reduces the original video volume when translated speech is detected, and restores it during silence in the translation. This creates a natural "voice-over" effect.

#### Smart Ducking Algorithm

The system uses an **RMS envelope follower** with **hysteresis gate** for speech detection:

##### Configuration (`SmartDuckingConfig`)

| Parameter | Default | Description |
|-----------|---------|-------------|
| `tickMs` | 50 | Ducking loop interval (ms) |
| `thresholdOnRms` | 0.012 | RMS level to open speech gate |
| `thresholdOffRms` | 0.009 | RMS level to close speech gate (hysteresis) |
| `rmsAttackTauMs` | 60 | RMS envelope attack time constant |
| `rmsReleaseTauMs` | 240 | RMS envelope release time constant |
| `holdMs` | 520 | Hold gate open this long after speech stops |
| `attackTauMs` | 110 | Volume duck-down time constant |
| `releaseTauMs` | 600 | Volume restore time constant |
| `maxDownPerSec` | 3.5 | Max volume decrease rate (per second) |
| `maxUpPerSec` | 0.9 | Max volume increase rate (per second) |
| `rmsMissingGraceMs` | 200 | Grace period when RMS data unavailable |
| `maxDtMs` | 250 | Max time step (prevents large jumps) |
| `externalBaselineDelta01` | 0.02 | Threshold for external baseline change detection |
| `unduckTolerance01` | 0.01 | Tolerance for un-ducking check |
| `volumeStep01` | 0.01 | Volume quantization step |
| `applyDeltaThreshold01` | 0.005 | Min delta to actually apply volume change |

##### Algorithm Flow (per tick)

1. **RMS Envelope Update**: Exponential moving average of audio RMS
   ```
   envelope += (rmsValue - envelope) * alpha
   alpha = 1 - exp(-dtMs / tauMs)
   ```

2. **Speech Gate**: Hysteresis comparator
   - Gate opens when `envelope >= thresholdOnRms`
   - Gate closes when `envelope < thresholdOffRms` AND `holdMs` elapsed since last sound
   - Missing RMS data: gate stays open after grace period

3. **Volume Calculation**: Exponential approach to target
   - Gate open → target = `duckingTarget` (e.g., 0.15 = 15% volume)
   - Gate closed → target = `baseline` (original volume)
   - Volume moves toward target with attack/release time constants
   - Rate-limited by `maxDownPerSec` / `maxUpPerSec`

4. **Volume Application**: Quantized to steps, applied only if delta exceeds threshold

##### Decision Types

| Decision | Description |
|----------|-------------|
| `apply` | Set video volume to computed value |
| `noop` | No change needed (delta too small) |
| `stop` | Stop ducking entirely, restore volume |

##### Ducking Lifecycle

```
Translation starts → initSmartDuckingRuntime(currentVolume)
                   → setInterval(tick, 50ms)
                   ↓
Speech detected   → duck video volume down (attack: 110ms τ)
Speech ends       → hold 520ms → restore volume (release: 600ms τ)
                   ↓
Translation stops → stopSmartVolumeDucking() → restore baseline → clearInterval
```

##### Baseline Tracking

The system tracks the user's "intended" volume:
- On start: `baseline = currentVideoVolume`
- During ducking: if user manually changes volume, baseline updates
- On stop: volume restored to baseline

##### RMS Computation

RMS (Root Mean Square) of translated audio for speech detection:

```javascript
// Float time-domain data (preferred)
analyser.getFloatTimeDomainData(floatData);
let sum = 0;
for (const value of floatData) sum += value * value;
rms = Math.sqrt(sum / floatData.length);

// Byte fallback (8-bit quantization)
analyser.getByteTimeDomainData(data);
for (const rawValue of data) {
  const normalized = (rawValue - 128) / 128;
  sum += normalized * normalized;
}
rms = Math.sqrt(sum / data.length);
```

Uses Web Audio API `AnalyserNode` connected to the translation audio element.

---

### Polling/Retry Logic

**Source**: `src/core/translationHandler.ts` (`VOTTranslationHandler.translateVideoImpl`)

#### Translation Request Flow

```
1. Send translateVideo request
   ↓
2. Check response status:
   ├─ FINISHED (status=1) → Return audio URL ✓
   ├─ PART_CONTENT (status=4) → Return audio URL ✓
   ├─ FAILED (status=3) → Throw error ✗
   ├─ SESSION_REQUIRED (status=6) → Throw error ✗
   ├─ WAITING (status=0) → Show ETA, schedule retry ↻
   ├─ LONG_WAITING (status=2) → Show ETA, schedule retry ↻
   └─ AUDIO_REQUESTED (status=5) → Download & upload audio, then retry ↻
```

#### Retry Strategy

- **Retry delay**: 20 seconds (`scheduleRetry(..., 20000, signal)`)
- **Retry mechanism**: Recursive call to `translateVideoImpl` after timeout
- **Cancellation**: Via `AbortController` signal — cleans up timeout on abort
- **Max DT clamping**: Each retry timeout is stored in `handler.autoRetry` for cleanup

#### Audio Download Flow (AUDIO_REQUESTED)

When the server can't extract audio itself:
1. Server returns `AUDIO_REQUESTED` status
2. Client downloads video audio via `AudioDownloader`
3. Audio uploaded to Yandex via `requestVtransAudio` (Protobuf)
4. Supports chunked upload (`partialAudioInfo`) for large files
5. After upload, immediately retries translation request
6. Timeout: 15 seconds for download completion

#### Lively Voice Fallback

If "lively" (natural-sounding) voice fails:
1. First attempt with `useLivelyVoice: true`
2. If server rejects (error contains "обычная озвучка"), retry with `useLivelyVoice: false`
3. Subsequent retries keep lively voice disabled

#### Translation Cache & Refresh

- Translations cached with key: `videoId + requestLang + responseLang + translationHelp`
- Cache TTL: ~30 minutes (refresh scheduled at `YANDEX_TTL_MS - 5min`)
- `refreshTranslationAudio()` re-requests translation and updates cache
- Stale action detection via generation counter (`actionsGeneration`)

#### Error Handling

- Abort errors → silently ignored (user cancelled)
- VOT client errors → mapped to localized UI messages
- Server error messages preserved when available
- Desktop notification on failure (if enabled and had async wait)

---

### Key Classes and File Paths (Reference)

#### VOT.js Library (`@vot.js/*`)

| Class/Module | Path | Purpose |
|-------------|------|---------|
| `VOTClient` | `@vot.js/core/dist/client.js` | Main API client (Protobuf, sessions, requests) |
| `MinimalClient` | `@vot.js/core/dist/client.js` | Base HTTP client with session management |
| `YandexVOTProtobuf` | `@vot.js/core/dist/protobuf.js` | Protobuf encode/decode for all message types |
| `YandexSessionProtobuf` | `@vot.js/core/dist/protobuf.js` | Session request/response Protobuf |
| `getSignature` | `@vot.js/shared/dist/secure.js` | HMAC-SHA-256 signing |
| `getSecYaHeaders` | `@vot.js/shared/dist/secure.js` | Build auth headers (Vtrans-Signature, Sec-Vtrans-*) |
| `getUUID` | `@vot.js/shared/dist/secure.js` | Generate random 32-char hex UUID |
| `config` | `@vot.js/shared/dist/data/config.js` | API host, HMAC key, user agent, version |
| `Protobuf messages` | `@vot.js/shared/dist/protos/yandex.js` | All Protobuf message definitions |

#### Browser Extension (voice-over-translation)

| File | Purpose |
|------|---------|
| `src/core/translationHandler.ts` | Translation orchestration, polling, retries, audio download |
| `src/core/translationOrchestrator.ts` | Higher-level translation coordination |
| `src/videoHandler/modules/translation.ts` | Audio player management, sync, ducking integration |
| `src/videoHandler/modules/translationShared.ts` | Translation helpers (cache, request, apply) |
| `src/videoHandler/modules/ducking.ts` | Smart audio ducking algorithm |
| `src/videoHandler/modules/proxyShared.ts` | Audio URL proxy/unproxy |
| `src/utils/volume.ts` | Volume utilities (percent ↔ 0..1, quantization, clamping) |
| `src/ui/components/votButton.ts` | VOT toggle button UI component |
| `src/ui/components/votMenu.ts` | Translation settings menu |
| `src/ui/views/settings.ts` | Settings page (language, volume, proxy) |
| `src/audioDownloader/` | Client-side audio extraction (for AUDIO_REQUESTED) |

#### Configuration Constants

| Constant | Value | Source |
|----------|-------|--------|
| API Host | `api.browser.yandex.ru` | `@vot.js/shared/dist/data/config.js` |
| VOT Proxy Host | `vot.toil.cc/v1` | config |
| Worker Host | `vot-worker.toil.cc` | config |
| HMAC Key | `bt8xH3VOlb4mqf0nqAibnDOoiPlXsisf` | config |
| Component Version | `25.6.0.2259` | config |
| User Agent | YaBrowser UA string | config |
| Default Duration | 343 seconds | config |
| Ducking Tick | 50ms | ducking.ts |
| Retry Delay | 20 seconds | translationHandler.ts |
| Cache Refresh | ~25 minutes | translationHandler.ts |

---

## VOT Integration Plan (ReVanced)

### Approach

Since ReVanced uses a shell-based patching system (not a Gradle project), VOT integration will be implemented as:

1. **Custom ReVanced Patch**: A Java/Kotlin patch that hooks into YouTube's player
2. **Integration Module**: VOT code packaged into `revanced-integrations`
3. **Minimal Hooks**: Patch injects calls to VOT module at key player lifecycle points

### Components to Port

1. **VOTClient (Java/Kotlin)**: Protobuf requests + HMAC signing for Yandex API
2. **Shadow Player**: Second ExoPlayer instance for translation audio
3. **Audio Sync**: Position polling + seek correction (<500ms)
4. **Audio Ducking**: Port the smart ducking algorithm (RMS-based)
5. **UI**: Button overlay on player + settings activity/fragment
6. **Proxy Support**: Optional audio URL proxying

### Package Structure (Planned)

```
app/revanced/integrations/vot/
├── api/
│   ├── VOTClient.java          — Yandex API client
│   ├── VOTProtobuf.java        — Protobuf encode/decode
│   ├── VOTSignature.java       — HMAC signing
│   └── VOTSession.java         — Session management
├── player/
│   ├── ShadowPlayer.java       — Second ExoPlayer for translation
│   ├── AudioSyncManager.java   — Position sync logic
│   └── AudioDuckingManager.java — Smart ducking
├── ui/
│   ├── VOTButton.java          — Player overlay button
│   └── VOTSettingsFragment.java — Settings UI
└── VOTManager.java             — Main entry point / coordinator
```
