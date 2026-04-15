# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

This is the **AppsWithLove fork** of `google-ai-edge/gallery`. See [README.md](README.md) for the product-level overview. This file is the engineering reference.

## Build Commands

All Gradle commands run from `Android/src/`:

```bash
./gradlew assembleDebug       # Build debug APK
./gradlew assembleRelease     # Build release APK (R8-minified, signed)
./gradlew installDebug        # Build and install debug on connected device
./gradlew :app:lint           # Run lint checks
./gradlew test                # Run unit tests
./gradlew :app:test --tests "com.appswithlove.ai.SomeTest"  # Single test
```

CI runs on Ubuntu with Java 21 (`.github/workflows/build_android.yaml`).

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

## Security posture

The fork differs from upstream Google on several hardening decisions — don't silently undo these:

- **No Firebase.** `firebase-analytics`, `firebase-messaging`, `FirebaseApp.initializeApp`, and all FCM manifest entries are removed. App does not phone home. If you add telemetry, use an AppsWithLove-hosted self-hosted analytics stack, behind an opt-in toggle.
- **R8 + shrinkResources ON for release.** Release AABs are obfuscated and tree-shaken. Keep rules in `Android/src/app/proguard-rules.pro`. Any new code that uses reflection (Gson deserialization, annotation scanning, JNI callbacks) needs a matching keep rule or it will fail at runtime only in release builds.
- **`android:allowBackup="false"`** in `AndroidManifest.xml`. No `adb backup` or Google Drive auto-sync of app data.
- **HF OAuth is not configured.** `ProjectConfig.kt` ships placeholder client ID + redirect URI. This is deliberate — gated model downloads route through the GitHub release mirror instead (see below). No personal token is embedded in the APK.

## Architecture

Single-module Android app (`Android/src/app/`) targeting Android 12+ (minSdk 31). Kotlin namespace: `com.appswithlove.ai`.

**Stack:** Kotlin 2.2.0 + Jetpack Compose (BOM 2026.02.00) + Hilt 2.57.2 + LiteRT LM 0.10.0 (on-device LLM inference). AGP 8.9.2.

### Key Layers

- **`data/`** — Models, repositories, allowlists. `DataStoreRepository` (local prefs via Protobuf), `DownloadRepository` (model download/cache), `Tasks.kt` + `Categories.kt` define available AI tasks.
- **`runtime/`** — `LlmModelHelper.kt` wraps LiteRT LM inference API.
- **`ui/`** — Compose screens per feature: `home/`, `llmchat/`, `llmsingleturn/`, `modelmanager/`, `benchmark/`. Shared components in `ui/common/`.
- **`customtasks/`** — Extensible feature modules (`agentchat/`, `aijournal/`, `emoji/`, `examplecustomtask/`, `insurancecard/`, `tinygarden/`). Most use LLM function-calling via `@Tool` / `@ToolParam`.
- **`di/`** — Hilt modules.
- **`worker/`** — WorkManager background tasks.

### Navigation

Single-activity (`MainActivity.kt`) with Compose Navigation 3; graph defined in `ui/navigation/GalleryNavGraph.kt`.

### Model Allowlist System

Three copies of `model_allowlist.json` are kept in sync:

1. **`model_allowlist.json`** (repo root) — source of truth
2. **`model_allowlists/<version>.json`** (e.g. `1_0_11.json`) — version-pinned copy the app fetches from `https://raw.githubusercontent.com/.../<version>.json` at startup based on the app's `versionName`
3. **`Android/src/app/src/main/assets/model_allowlist.json`** — bundled fallback when the network fetch fails

Load order at runtime: GitHub versioned URL → disk cache → bundled asset. When updating models, update all three. When bumping `versionName` add a new `model_allowlists/<new-version>.json`.

Current models (post-MobileActions-task removal): Gemma-4-E2B, Gemma-4-E4B, Qwen2.5-1.5B, Qwen3.5-0.8B, TinyGarden-270M.

#### GitHub release mirror for gated models

Some upstream HF models are gated (return 401 without an authed session that has accepted the upstream license). We mirror those to GitHub releases on `michelutke/ai-sui-gallery` and override the download URL in the allowlist:

```json
{
  "name": "TinyGarden-270M",
  "modelId": "litert-community/functiongemma-270m-ft-tiny-garden",
  "modelFile": "tiny_garden_q8_ekv1024.litertlm",
  "commitHash": "c205853ff82da86141a1105faa2344a8b176dfe7",
  "url": "https://github.com/michelutke/ai-sui-gallery/releases/download/models-v1/tiny_garden_q8_ekv1024.litertlm",
  ...
}
```

`AllowedModel.toModel()` in `data/ModelAllowlist.kt` uses `url` verbatim when present, bypassing the `huggingface.co/{modelId}/resolve/{commit}/{file}` construction.

**Adding a mirrored model:**

1. Download the `.litertlm` from HF with an authed token that has accepted the license. Verify SHA-256 and file size against HF `content-length`.
2. `gh release create models-vN --repo michelutke/ai-sui-gallery --title "Models mirror vN" --notes "..."` — include Gemma Terms pointer in notes for Gemma-derived models.
3. `gh release upload models-vN <file> --repo michelutke/ai-sui-gallery`.
4. Add `"url": "https://github.com/michelutke/ai-sui-gallery/releases/download/models-vN/<file>"` to the model entry in all three allowlist copies.
5. Clear app data (`adb shell pm clear com.appswithlove.ai`), rebuild, reinstall, confirm tokenless download works end-to-end.

### Agent Chat (`customtasks/agentchat/`)

Skill-based LLM agent. Skills are JS-based bundles under `Android/src/app/src/main/assets/skills/<skill-name>/` containing a `SKILL.md` (name + description + instructions for the LLM) and `scripts/index.html` (entry point exposing `window['ai_edge_gallery_get_result']`).

- `AgentTools.kt` exposes three `@Tool` methods to the LLM: `loadSkill`, `runJs`, `runIntent`. The LLM reads a skill's markdown via `loadSkill`, then calls `runJs` or `runIntent` to execute.
- `IntentHandler.kt` implements the native bridge for `runIntent` — supports flashlight, contacts, email, SMS, map, WiFi, calendar.
- `SkillManagerViewModel.kt` holds the `TRYOUT_CHIPS` list (horizontal chip row in the UI) — add a chip here to surface a new built-in skill prominently.
- Skills under `assets/skills/` auto-register as built-in Skills on first launch.

### Insurance Card Scanner (`customtasks/insurancecard/`)

Dual-mode extraction for Swiss KVG insurance cards. User selects mode via model picker:

**Mode 1: OCR + Regex (no AI model needed)**
CameraX capture → ML Kit OCR (with `rotationDegrees` for correct orientation) → regex/pattern extraction → result display

**Mode 2: LLM extraction (multimodal or text-only)**
- Multimodal models (e.g. Gemma 4): image sent directly to LLM → JSON response → `InsuranceCardLlmParser` parses result
- Text-only models (e.g. Qwen 2.5): ML Kit OCR first → OCR text sent to LLM → JSON parse with regex fallback

**Key files:**
- `InsuranceCardTask.kt` — `OCR_REGEX_MODEL` sentinel (built-in, no download), branched init for OCR vs LLM mode, system prompts for text and image extraction
- `InsuranceCardScreen.kt` — `captureAndProcess()` branches into 3 paths (OCR-regex, multimodal LLM, text-only LLM), conversation reset between scans
- `InsuranceCardLlmParser.kt` — Gson-based JSON parser for LLM responses, strips markdown fences

**OCR+Regex extraction strategy (back of card / EHIC format):**
- AHV number: `756.XXXX.XXXX.XX` regex pattern
- Birth date: `DD.MM.YYYY` or `DD/MM/YYYY`, filtered for past dates only (skips Ablaufdatum)
- Card number: 20-digit pattern (`80756...` or `8231...`)
- Versicherer: field 7 `XXXXX - InsurerName` pattern, matched against official BAG insurer list
- Name/Vorname: ALL-CAPS letters-only lines, filtered against label words and short codes. Converted to title case.

**Front-of-card fallback:** label-based extraction with multi-language support (DE/FR/IT).

**Camera crop:** overlay uses ID-1 credit card aspect ratio (85.6/54mm). Crop maps preview coordinates → camera image coords accounting for FILL_CENTER scaling.

**Built-in model pattern:** To add a non-AI option to any task, create a `Model` with `url = ""`, `sizeInBytes = 0L`, and `localFileRelativeDirPathOverride` set (triggers instant SUCCEEDED status). Set `model.instance = Unit` in `initializeModelFn` to pass the initialization gate. The `ModelPicker` auto-sections "Without AI" and "AI Models" when both types exist.

### Custom Task Extension Pattern

To add a new AI feature: define `ActionType` enum + `Action` class, annotate functions with `@Tool`/`@ToolParam`, implement logic in a ViewModel. Register via Hilt `@IntoSet` (see `AiJournalModule.kt` or `EmojiModule.kt`). See `Function_Calling_Guide.md` and `customtasks/examplecustomtask/` for reference.

When adding `@Tool`-annotated classes: also add a `-keep class com.appswithlove.ai.customtasks.<newtask>.YourTools { *; }` to `proguard-rules.pro`, otherwise function calling silently fails in release builds due to R8 renaming.

### Proto Files

`app/src/main/proto/settings.proto`, `benchmark.proto`, `skill.proto` generate typed data classes via the Protobuf Lite plugin. Package is `com.appswithlove.ai.proto`.

## Dependencies

Managed via version catalog at `Android/src/gradle/libs.versions.toml`.

Key versions: AGP 8.9.2, Kotlin 2.2.0, Compose BOM 2026.02.00, Hilt 2.57.2, LiteRT LM 0.10.0, Room 2.7.1, Moshi 1.15.2.

`guava` and `androidx.documentfile` are explicit deps because Firebase used to pull them transitively; removing Firebase would otherwise break `TinyGardenScreen.kt` (uses `com.google.common.io.BaseEncoding`) and `SkillManagerViewModel.kt` (uses `DocumentFile` for directory import).
