# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

All Gradle commands run from `Android/src/`:

```bash
./gradlew assembleDebug       # Build debug APK
./gradlew assembleRelease     # Build release APK
./gradlew installDebug        # Build and install debug on device
./gradlew :app:lint           # Run lint checks
./gradlew test                # Run unit tests
./gradlew :app:test --tests "com.google.ai.edge.gallery.SomeTest"  # Single test
```

CI runs on Ubuntu with Java 21 (`build_android.yaml`).

## CI/CD — Updraft Deployment

`build_android.yaml` has two jobs:

1. **build** — Builds signed release AAB, uploads as GitHub artifact
2. **deploy** — Downloads AAB, uploads to Updraft via curl API

Triggers on push to `main` (Android paths) or `workflow_dispatch`. PRs only run the build job.

### Release Signing

`build.gradle.kts` reads signing config from env vars (`KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`). Falls back to debug signing when env vars are absent (local dev).

### GitHub Secrets Required

| Secret | Description |
|--------|-------------|
| `KEYSTORE_BASE64` | Base64-encoded release keystore (.jks) |
| `KEYSTORE_PASSWORD` | Keystore password |
| `KEY_ALIAS` | Key alias (`release`) |
| `KEY_PASSWORD` | Key password |
| `UPDRAFT_APP_KEY` | Updraft app key (from dashboard) |
| `UPDRAFT_API_KEY` | Updraft API key (from dashboard) |

Keystore `.jks` file is stored in 1Password.

## Local Setup

Before building, configure HuggingFace OAuth (required for model downloads):

1. Create a HuggingFace OAuth app
2. Set `clientId` and `redirectUri` in `Android/src/app/src/main/java/com/google/ai/edge/gallery/common/ProjectConfig.kt`
3. Update `manifestPlaceholders["appAuthRedirectScheme"]` in `Android/src/app/build.gradle.kts`

## Architecture

Single-module Android app (`Android/src/app/`) targeting Android 12+ (minSdk 31).

**Stack:** Kotlin + Jetpack Compose + Hilt + LiteRT LM (on-device LLM inference)

### Key Layers

- **`data/`** — Models, repositories, allowlists. `DataStoreRepository` (local prefs via Protobuf), `DownloadRepository` (model download/cache), `Tasks.kt` + `Categories.kt` define available AI tasks.
- **`runtime/`** — `LlmModelHelper.kt` wraps LiteRT LM inference API.
- **`ui/`** — Compose screens per feature: `home/`, `llmchat/`, `llmsingleturn/`, `modelmanager/`, `benchmark/`. Shared components in `ui/common/`.
- **`customtasks/`** — Extensible feature modules using LLM function-calling (`tinygarden/`, `mobileactions/`, `examplecustomtask/`).
- **`di/`** — Hilt modules.
- **`worker/`** — WorkManager background tasks.

### Navigation

Single-activity (`MainActivity.kt`) with Compose Navigation; graph defined in `ui/navigation/GalleryNavGraph.kt`.

### Model Allowlist System

`model_allowlist.json` (root) is the current allowlist. Versioned snapshots live in `model_allowlists/`. Controls which models are available per app version.

### Insurance Card Scanner (`customtasks/insurancecard/`)

OCR-based extraction pipeline for Swiss KVG insurance cards (no LLM needed):

**Flow:** CameraX capture → ML Kit OCR (with `rotationDegrees` for correct orientation) → regex/pattern extraction → result display

**Extraction strategy (back of card / EHIC format):**
- AHV number: `756.XXXX.XXXX.XX` regex pattern
- Birth date: `DD.MM.YYYY` or `DD/MM/YYYY`, filtered for past dates only (skips Ablaufdatum)
- Card number: 20-digit pattern (`80756...` or `8231...`)
- Versicherer: field 7 `XXXXX - InsurerName` pattern, matched against official BAG insurer list
- Name/Vorname: ALL-CAPS letters-only lines, filtered against label words and short codes. Converted to title case.

**Front-of-card fallback:** label-based extraction with multi-language support (DE/FR/IT).

**Camera crop:** overlay uses ID-1 credit card aspect ratio (85.6/54mm). Crop maps preview coordinates → camera image coords accounting for FILL_CENTER scaling.

### Custom Task Extension Pattern

To add a new AI feature: define `ActionType` enum + `Action` class, annotate functions with `@Tool`/`@ToolParam`, implement logic in a ViewModel. See `Function_Calling_Guide.md` and `customtasks/examplecustomtask/` for reference.

### Proto Files

`app/src/main/proto/settings.proto` and `benchmark.proto` generate typed data classes via the Protobuf Lite plugin.

## Dependencies

Managed via version catalog at `Android/src/gradle/libs.versions.toml`.

Key versions: AGP 8.8.2, Kotlin 2.2.0, Compose BOM 2026.02.00, Hilt 2.57.2, LiteRT LM 0.10.0.
