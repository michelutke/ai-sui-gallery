# Upstream sync — June 2026

Selective port of features from upstream [`google-ai-edge/gallery`](https://github.com/google-ai-edge/gallery)
(~110 commits ahead of our fork's divergence point, April 2026).

A straight `git merge` was not possible: this fork renamed the whole package
(`com.google.ai.edge.gallery` → `com.appswithlove.ai`), so every upstream edit lands on paths that no
longer exist here. Each feature was therefore ported by hand — adapting package paths, stripping
Firebase, and preserving our custom UI / i18n / AgentChat / model-allowlist mirror.

## Ported

| Feature | Notes |
|---------|-------|
| **litert-lm 0.10.0 → 0.11.0** | Drop-in. mcp/ktor/mlkit-genai deps intentionally skipped (MCP + AICore out of scope). |
| **Bug fixes** | `NumberFormatException` in `BenchmarkResultsViewer` on non-US locales; always return to Home after an OS kill (`super.onCreate(null)`); "turn off all skills" now resets the conversation; single-turn model-download button is interactable. |
| **Copy-to-clipboard** | Long-press menu on user / thinking messages + a copy button on assistant messages (`LongPressCopyContainer`). |
| **Image download / share / save** | Fullscreen image viewer gains Share / Copy / Save-to-album actions (`Context.shareBitmap` / `copyBitmapToClipboard` / `saveBitmapToMediaStore`). |
| **Speculative decoding** | Per-task toggle gated by a new `ModelCapability.SPECULATIVE_DECODING` + litert-lm `Capabilities` check. Dormant until an allowlist model declares the capability. |
| **System prompt editing** | Adds **persistence** (`SystemPromptRepository` → `UserData`) to the existing edit UI and enables it on the built-in chat / image / audio tasks. `CustomTask.initializeModelFn` now takes a `systemInstruction`. Our custom AgentChat system-prompt flow is preserved. |
| **Chat history** | New `chat_history.proto` stored in `UserData.chat_sessions`. Right-side history drawer (`ChatHistorySideSheet`) with save-on-idle, load (restores context via litert-lm `initialMessages`), new chat, delete, and clear-all. Text messages only. |
| **TPU accelerator** | `Accelerator.TPU` support in the model helper / benchmark. |

## Model capability model (backward-compatible)

`llmSupportThinking` is **kept** alongside the new `capabilities: []` list and `capabilityToTaskTypes`
map. Allowlists already published to the GitHub mirror keep working unchanged — no republish required.
New optional parameters (`taskId`, `initialMessages`, `systemInstruction`, `userDataDataStore`) all
default, so custom tasks were not forced to change behaviour.

## In-chat toolbar (Material 3)

Config, chat history and "new chat" moved out of the (overlapping) top app bar into a Material 3
floating toolbar directly above the message input (`ChatInputToolbar`):

- a rounded **pill** holding the config + history tools, and
- a separate rounded **new-chat** button to its right.

The config dialog was extracted into a reusable `ModelConfigDialog` shared by the app bar and the
toolbar. The top app bar now shows only the model selector.

## Deliberately skipped

- **MCP**, **scheduled notifications / calendar skills**, **AICore (Gemini Nano)**, **macOS app** — out of scope.
- **All Firebase / FCM analytics** — keeps the fork's no-phone-home posture.
- **Model-card "related models" grouping + show-more menu** — would rewrite our custom
  `ModelItem` / `ModelPicker`; only the safe TPU-label bit was taken.
- **Image / audio in chat history** — needs bitmap/audio file persistence; text history only.

## Security / release notes

No new ProGuard rules required: existing keeps cover `com.appswithlove.ai.data.**`,
`com.appswithlove.ai.proto.**`, enums, and protobuf-lite. Firebase remains fully removed.
