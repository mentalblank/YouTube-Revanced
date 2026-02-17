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

## VOT Integration (Planned)
- Shadow Player (secondary ExoPlayer for translation audio)
- Yandex Translation API client with Protobuf + HMAC signing
- Audio synchronization (<500ms drift)
- Audio ducking (original volume reduction)
- UI toggle button in player
- Translation settings (language, duck volume)
