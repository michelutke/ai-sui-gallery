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

### Custom Task Extension Pattern

To add a new AI feature: define `ActionType` enum + `Action` class, annotate functions with `@Tool`/`@ToolParam`, implement logic in a ViewModel. See `Function_Calling_Guide.md` and `customtasks/examplecustomtask/` for reference.

### Proto Files

`app/src/main/proto/settings.proto` and `benchmark.proto` generate typed data classes via the Protobuf Lite plugin.

## Dependencies

Managed via version catalog at `Android/src/gradle/libs.versions.toml`.

Key versions: AGP 8.8.2, Kotlin 2.1.0, Compose BOM 2026.02.00, Hilt 2.57.2, LiteRT LM 0.9.0-alpha06.
