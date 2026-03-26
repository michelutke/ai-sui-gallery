# Swiss AI Gallery — Migration Spec

## Goal
Fork the Google AI Edge Gallery app into a Swiss-branded version. Add two custom use cases (Emoji Generator, Insurance Card Scanner) as native CustomTasks using the existing plugin architecture. Add BuildConfig-based HF token for gated model downloads.

## Source
- Base: `/Users/miggi/awlsrc/projects/ai-sui-gallery`
- Use cases from: `/Users/miggi/awlsrc/projects/android-local-ai`

## Architecture Summary
The Gallery uses a **Hilt multibinding plugin system**:
- Implement `CustomTask` interface (4 methods: task metadata, init, cleanup, MainScreen)
- Register via `@Module @Provides @IntoSet`
- Models mapped from `model_allowlist.json` via `taskTypes` matching `task.id`
- Navigation auto-routes: `model/{taskId}/{modelName}` → `customTask.MainScreen()`

---

## Phase 1: Fork Setup & HF Token
**Goal:** Rebrand app, add BuildConfig HF token, verify build.

### Tasks
1. Update `applicationId` → `com.appswithlove.aiedge.gallery`
2. Update `namespace` → `com.appswithlove.aiedge.gallery` (or keep original to minimize refactor)
3. Update app name in `strings.xml` → "SUI AI Gallery"
4. Add to `local.properties` (gitignored): `hf.token=<YOUR_HF_TOKEN>`
5. Read token in `build.gradle.kts` via `localProperties` → `BuildConfig.HF_TOKEN`
6. Inject token into download flow — modify `DownloadAndTryButton.kt` or `DownloadRepository.kt`:
   - Before OAuth check: if `BuildConfig.HF_TOKEN` is non-empty, use it as Bearer token
   - Skip OAuth flow entirely when BuildConfig token is present
7. Verify: `./gradlew assembleDebug` succeeds
8. Verify: gated model download works with BuildConfig token

### Success Criteria
- [ ] App builds with new applicationId
- [ ] App name shows "SUI AI Gallery"
- [ ] HF token injected via BuildConfig, not committed
- [ ] Gated model downloads work without OAuth browser flow

### Unresolved
- Keep original package namespace to avoid massive import refactor? Or full rename?

---

## Phase 2: Emoji Generator CustomTask
**Goal:** Add emoji generation as a CustomTask using LLM inference with few-shot prompting.

### Files to Create
```
customtasks/emoji/
├── EmojiTask.kt          — CustomTask implementation
├── EmojiScreen.kt        — Compose UI
├── EmojiModule.kt        — Hilt @Provides @IntoSet
└── EmojiUtils.kt         — Emoji extraction regex
```

### EmojiTask Implementation
```kotlin
class EmojiTask : CustomTask {
    override val task = Task(
        id = "emoji_generator",
        label = "Emoji Generator",
        category = CategoryInfo(Category.LLM, 2),
        icon = Icons.Outlined.EmojiEmotions,
        description = "Generate contextual emojis from text input in real-time",
        models = mutableListOf()  // populated from allowlist
    )

    override fun initializeModelFn(...) {
        LlmChatModelHelper.initialize(
            context, model, supportImage = false, supportAudio = false,
            onDone = onDone,
            systemInstruction = Contents.of(listOf(Content.Text(FEW_SHOT_PROMPT)))
        )
    }

    override fun cleanUpModelFn(...) {
        LlmChatModelHelper.cleanUp(model, onDone)
    }

    @Composable
    override fun MainScreen(data: Any) {
        EmojiScreen(data = data as CustomTaskData)
    }
}
```

### EmojiScreen Design
- Text input field with debounce (500ms)
- Large emoji display circle (like current app)
- Few-shot prompt: "Text: I love you\nEmoji: ❤️\n..."
- Real-time: type → debounce → infer → extract emoji → display
- Uses `LlmChatModelHelper.runInference()` for streaming

### Model Allowlist Entry
Add `"emoji_generator"` to `taskTypes` for Gemma 3 1B model.

### Tests
- `EmojiUtilsTest` — emoji extraction regex
- `EmojiTaskTest` — task metadata, ID, category

---

## Phase 3: Insurance Card Scanner CustomTask
**Goal:** Add camera-based insurance card OCR as a CustomTask using MLKit text-recognition + LLM for structured extraction.

### Files to Create
```
customtasks/insurancecard/
├── InsuranceCardTask.kt       — CustomTask implementation
├── InsuranceCardScreen.kt     — Camera + OCR + LLM UI
├── InsuranceCardModule.kt     — Hilt @Provides @IntoSet
└── InsuranceCardViewModel.kt  — Camera + OCR logic (non-Hilt, screen-scoped)
```

### Approach
Two-step pipeline:
1. **MLKit OCR** — capture image → extract raw text from card
2. **LLM structuring** — pass raw text to Gemma → extract structured fields (name, insurance number, date of birth, insurer)

This is better than pure OCR because the LLM can understand context and fix OCR errors.

### InsuranceCardScreen Design
- Camera preview (CameraX, back camera default)
- Capture button → crop to card aspect ratio (13:4)
- OCR via MLKit `TextRecognition` → raw text
- Send raw text to LLM: "Extract insurance card fields from this text: {raw_text}. Return JSON with: name, insuranceNumber, dateOfBirth, insurer"
- Display structured result in card format
- Retake button

### Dependencies Needed
- `com.google.mlkit:text-recognition` — add to Gallery's `libs.versions.toml`
- CameraX already present in Gallery deps

### Model Allowlist Entry
Add `"insurance_card_scan"` to `taskTypes` for Gemma 3 1B model.

### Tests
- `InsuranceCardTaskTest` — task metadata
- `InsuranceCardViewModelTest` — OCR flow states, mock text recognition

---

## Phase 4: Model Allowlist & Config
**Goal:** Update allowlist with correct task type mappings.

### Tasks
1. Add `"emoji_generator"` and `"insurance_card_scan"` to Gemma 3 1B's `taskTypes`
2. Add same to Qwen 2.5 1.5B if present
3. Verify model-to-task mapping works at startup
4. Add system prompts as default configs if supported

### Updated allowlist entry (Gemma 3 1B):
```json
{
    "name": "Gemma3-1B-IT q4",
    "taskTypes": ["llm_chat", "llm_prompt_lab", "emoji_generator", "insurance_card_scan"],
    ...
}
```

---

## Phase 5: Integration Testing & Polish
**Goal:** End-to-end validation, all tests green.

### Tasks
1. Run full test suite
2. Test on Pixel 8 Pro:
   - Download Gemma 3 1B with BuildConfig token
   - Emoji generator: type text → see emoji
   - Insurance card: capture → OCR → structured result
3. Verify home screen shows both new tasks in correct category
4. Verify model initialization/cleanup lifecycle
5. Clean up any dead code

### Success Criteria
- [ ] All tests green
- [ ] Both custom tasks appear on home screen
- [ ] Emoji generation works end-to-end
- [ ] Insurance card scan works end-to-end
- [ ] HF token not in any committed file

---

## Dependency Changes (Gallery app)

### Add
```toml
textRecognition = "16.0.1"
text-recognition = { module = "com.google.mlkit:text-recognition", version.ref = "textRecognition" }
```

### Keep (already present)
- CameraX, Hilt, LiteRT-LM, Compose, Material 3, WorkManager

---

## Token Security
- `hf.token` in `local.properties` (gitignored by default in Android projects)
- Read at build time → `BuildConfig.HF_TOKEN`
- Used as Bearer token for HF downloads, bypassing OAuth flow
- **Never committed** — `.gitignore` already covers `local.properties`

---

## Unresolved Questions
1. Keep original `com.google.ai.edge.gallery` package namespace or rename to `com.appswithlove`? (renaming = massive refactor, keeping = simpler)
2. Insurance card OCR: Swiss-specific fields (AHV number, Versichertennummer)? Or generic?
3. LLM prompt language: German/French/Italian or English?
4. Should Emoji Generator support the `/think` mode that Qwen3 uses?
5. Remove TinyGarden and MobileActions custom tasks from the fork? Or keep as demos?
