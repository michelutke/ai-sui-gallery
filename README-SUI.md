# SUI AI Gallery

Swiss fork of [Google AI Edge Gallery](https://github.com/google-ai-edge/gallery) with custom Swiss-specific AI use cases running fully on-device via LiteRT-LM.

## What Changed from Upstream

### Branding
- App name: "SUI AI Gallery"
- Application ID: `com.appswithlove.aiedge.gallery`

### HuggingFace Token (no OAuth)
- HF token injected via `BuildConfig.HF_TOKEN` from `local.properties`
- Bypasses browser-based OAuth flow for gated model downloads
- Setup: add `hf.token=hf_YOUR_TOKEN` to `Android/src/local.properties` (gitignored)

### Model Allowlist
- Bundled in `app/src/main/assets/model_allowlist.json` as fallback
- No dependency on GitHub-hosted versioned allowlists
- Uses real HuggingFace commit hashes (not date strings)

### Bug Fixes to Upstream
- `groupTasksByCategory` NPE: used `task.id` instead of `task.category.id` for category lookup — custom tasks crashed the sort
- Model allowlist loading: added assets fallback when internet fetch fails and no cached file exists

## Custom Tasks

### Emoji Generator (`emoji_generator`)
**Location:** `customtasks/emoji/`

Real-time emoji generation from text input using on-device LLM inference.

- **EmojiTask.kt** — CustomTask implementation with few-shot system prompt
- **EmojiScreen.kt** — Text input with 500ms debounce, circular emoji display
- **EmojiUtils.kt** — Unicode emoji extraction via regex
- **EmojiModule.kt** — Hilt `@Provides @IntoSet` registration

**How it works:**
1. User types text
2. 500ms debounce triggers LLM inference
3. System instruction contains few-shot examples (Text → Emoji pairs)
4. First emoji extracted from response via regex
5. Conversation reset before each query to keep context clean

**i18n:** English, German, French, Italian

### Insurance Card Scanner (`insurance_card_scan`)
**Location:** `customtasks/insurancecard/`

Two-step pipeline: CameraX capture → MLKit OCR → LLM structuring for Swiss insurance cards.

- **InsuranceCardTask.kt** — CustomTask implementation
- **InsuranceCardScreen.kt** — Camera preview, capture, OCR, LLM processing, validated result display
- **InsuranceCardResult.kt** — Data class with Swiss-specific fields
- **InsuranceCardValidator.kt** — Field validation with Swiss rules
- **InsuranceCardModule.kt** — Hilt registration

**Swiss fields extracted:**
| Field | Validation |
|-------|-----------|
| Name (Nachname) | Non-empty, no digits, min 2 chars |
| Vorname | Non-empty, no digits, min 2 chars |
| Geburtsdatum | Swiss date formats, range check |
| Versichertennummer | Optional, min length |
| AHV-Nummer | 13-digit EAN-13, prefix 756, checksum verified |
| Versicherer | Cross-checked against 25+ known Swiss insurers |
| Kartennummer | Optional |

**How it works:**
1. CameraX back camera with card overlay guide
2. User captures photo → cropped to card aspect ratio
3. MLKit TextRecognition extracts raw OCR text
4. LLM receives OCR text with JSON extraction prompt (includes example output)
5. Early JSON detection: parses as soon as `{...}` is complete (avoids waiting for max tokens)
6. Result displayed with per-field validation icons (check/warning/error)
7. Overall status banner shows if all required fields are valid

**i18n:** English, German, French, Italian

## Dependencies Added
- `com.google.mlkit:text-recognition:16.0.1` — OCR for insurance card scanner

## Available Models
All LLM models in the allowlist support both custom tasks:
- Gemma 3n E2B (3.1GB, vision support)
- Gemma 3n E4B (4.4GB, vision support)
- Gemma 3 1B q4 (554MB, text only)
- Qwen 2.5 1.5B q8 (1.6GB, text only)
- Qwen3 0.6B (614MB, text only)

## Setup

```bash
# 1. Add HuggingFace token to local.properties (gitignored)
echo "hf.token=hf_YOUR_TOKEN" >> Android/src/local.properties

# 2. Accept gated model licenses on huggingface.co (Gemma models)

# 3. Build
cd Android/src && ./gradlew assembleDebug

# 4. Install
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Project Structure (custom additions)
```
app/src/main/java/com/google/ai/edge/gallery/
├── customtasks/
│   ├── emoji/
│   │   ├── EmojiTask.kt
│   │   ├── EmojiScreen.kt
│   │   ├── EmojiUtils.kt
│   │   └── EmojiModule.kt
│   └── insurancecard/
│       ├── InsuranceCardTask.kt
│       ├── InsuranceCardScreen.kt
│       ├── InsuranceCardResult.kt
│       ├── InsuranceCardValidator.kt
│       └── InsuranceCardModule.kt
app/src/main/assets/
└── model_allowlist.json          # Bundled fallback
app/src/main/res/
├── values/strings.xml            # English
├── values-de/strings.xml         # German
├── values-fr/strings.xml         # French
└── values-it/strings.xml         # Italian
```
