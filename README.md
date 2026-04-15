# AppsWithLove AI Gallery

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

On-device generative AI showcase app by [AppsWithLove](https://appswithlove.com). Forked from [google-ai-edge/gallery](https://github.com/google-ai-edge/gallery) and adapted for AppsWithLove demos, Swiss use cases, and internal distribution via Updraft.

All inference runs locally via [LiteRT-LM](https://github.com/google-ai-edge/LiteRT-LM). Nothing leaves the device.

## App identity

| | |
|---|---|
| Application ID | `com.appswithlove.ai` |
| Kotlin namespace | `com.appswithlove.ai` |
| Distribution | [Updraft](https://app.getupdraft.com) (internal), push to `main` triggers build+deploy |
| Repo | `michelutke/ai-sui-gallery` (this repo) |
| Upstream | `google-ai-edge/gallery` (ref `upstream`) |

## What's different vs. upstream

### Security hardening

- **Firebase Analytics + FCM removed**: app does not phone home. No `google-services.json`, no `FirebaseApp.initializeApp`, no measurement services or GCM permissions.
- **Backup disabled**: `android:allowBackup="false"`. DataStore prefs, cached models, and auth tokens can't leak via `adb backup` or Google Drive auto-backup.
- **R8 + resource shrinking enabled** for release builds. Keep rules in `Android/src/app/proguard-rules.pro` cover LiteRT LM, `@Tool`/`@ToolParam` function-calling, Gson reflection, Room, AppAuth, WorkManager.

### Distribution

- No Play Store / App Store. Releases ship to Updraft via `.github/workflows/build_android.yaml`.
- Release signing keys live in GitHub Secrets (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`). Keystore backup in 1Password.

### Custom tasks added

Located under `Android/src/app/src/main/java/com/appswithlove/ai/customtasks/`:

| Task | Path | What it does |
|---|---|---|
| Agent Chat (Skills) | `agentchat/` | LLM + pluggable JS-based skill bundles. 13 built-in skills, including 6 device actions (flashlight, contact, email, map, WiFi, calendar) |
| AI Journal | `aijournal/` | Private on-device journal with AI structuring + natural language recall |
| Emoji Generator | `emoji/` | Real-time emoji generation from text (debounced, few-shot) |
| Insurance Card Scanner | `insurancecard/` | CameraX + ML Kit OCR + LLM (or regex) extraction of Swiss KVG cards |
| Tiny Garden | `tinygarden/` | Function-calling mini-game on a 3x3 grid (uses TinyGarden-270M) |

The original `mobileactions/` task was removed — its 6 device actions are now exposed as Agent Chat skills via the existing `runIntent` tool. See the chips row in Agent Chat for one-tap prompts.

## Model allowlist + GitHub release mirror

The allowlist is stored in three places (kept in sync):

- `model_allowlist.json` — repo root (source of truth)
- `Android/src/app/src/main/assets/model_allowlist.json` — bundled fallback if network load fails
- `model_allowlists/<version>.json` — version-pinned copy the app fetches at startup; current version is `1_0_11.json`

### Why we mirror some models

Some upstream models on HuggingFace are **gated** — they return HTTP 401 to any anonymous request because they require a logged-in HF user that has accepted the upstream license (typically Gemma).

`TinyGarden-270M` is the canonical example. To make the app work without any HF credentials, we mirror the gated `.litertlm` files to a **GitHub release** on this repo and add a `"url"` override to the allowlist entry. `AllowedModel.toModel()` in `Android/src/app/src/main/java/com/appswithlove/ai/data/ModelAllowlist.kt` uses that `url` verbatim when present, bypassing the usual `huggingface.co/{modelId}/resolve/{commit}/{file}` URL construction.

### Where the mirrored files live

[`michelutke/ai-sui-gallery` GitHub Releases](https://github.com/michelutke/ai-sui-gallery/releases) — each release tag holds one batch of model files as release assets.

| Release | Files | Reason for mirroring |
|---|---|---|
| [`models-v1`](https://github.com/michelutke/ai-sui-gallery/releases/tag/models-v1) | `tiny_garden_q8_ekv1024.litertlm` (288 MB, sha256 `38067934eac6845417a456602da8714e1c0a5ae28970140d3b5f6abaf48e3141`) | Upstream repo on HF is gated; returns 401 without auth |

### Adding or updating a mirrored model

1. Verify the model is worth mirroring — ungated HF models should stay on HF (faster CDN, better for updates).
2. Download the `.litertlm` file from HF with an authed token that has accepted the model's license.
3. Verify size + SHA-256 match the upstream `huggingface.co/.../resolve/<commit>/<file>` response.
4. Cut a new release on this repo: `gh release create models-vN --repo michelutke/ai-sui-gallery --title "Models mirror vN" ...`. Include the Gemma Terms notice in the body when the model is Gemma-derived.
5. `gh release upload models-vN <file> --repo michelutke/ai-sui-gallery`.
6. Edit all three `model_allowlist.json` copies: add `"url": "https://github.com/michelutke/ai-sui-gallery/releases/download/models-vN/<file>"` to the model entry. When bumping the app `versionName`, add a new `model_allowlists/<version>.json` that matches.
7. Build, install fresh (clear app data), confirm the download works without a token.

### Gemma license

All Gemma-derived models distributed through this mirror are governed by the [Gemma Terms of Use](https://ai.google.dev/gemma/terms) and the [Gemma Prohibited Use Policy](https://ai.google.dev/gemma/prohibited_use_policy). The app presents the Gemma terms dialog on first launch (`GemmaTermsOfUseDialog.kt`).

## Development

See [DEVELOPMENT.md](DEVELOPMENT.md) for upstream-inherited build instructions. AppsWithLove-specific notes:

### HuggingFace auth (optional)

The app's OAuth flow for HF-gated downloads is **not configured** — `ProjectConfig.kt` ships placeholder client ID + redirect URI. Currently this is fine because:

- All public HF models in the allowlist download without auth.
- Any gated model is mirrored to GitHub releases (see above).

If upstream OAuth needs to be re-enabled, register a HuggingFace OAuth app under an AppsWithLove account and fill in `ProjectConfig.kt` + `build.gradle.kts` → `manifestPlaceholders["appAuthRedirectScheme"]`.

### Build + install

```bash
cd Android/src
./gradlew installDebug       # debug build on connected device
./gradlew bundleRelease      # signed AAB for Updraft (needs keystore env vars)
./gradlew :app:lint          # lint
./gradlew test               # unit tests
```

## Further reading

- [CLAUDE.md](CLAUDE.md) — working conventions + architecture map for contributors using Claude Code
- [DEVELOPMENT.md](DEVELOPMENT.md) — upstream build setup
- [Function_Calling_Guide.md](Function_Calling_Guide.md) — how `@Tool` / `@ToolParam` are used to give models tool access
- [CHANGELOG.md](CHANGELOG.md) — release notes for Updraft builds
- [CONTRIBUTING.md](CONTRIBUTING.md) — upstream contribution guidelines (mostly inherited)

## License

Apache License 2.0 — see [LICENSE](LICENSE). Retains all upstream Google LLC copyright notices. Gemma-derived model redistributions additionally bound by the [Gemma Terms](https://ai.google.dev/gemma/terms).
