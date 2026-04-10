# Changelog

## 1.1.0 (2026-04-10)

### AI Journal — Function Calling Overhaul

- **Tool-based journal saving**: The LLM now calls `saveJournalEntry` directly instead of outputting raw JSON. Entries are saved automatically when the user shares experiences — no explicit "save" needed.
- **Tool-based recall**: The LLM calls `searchJournal` to query the database on demand. No more pre-loading all entries into context, which was slow on small models.
- **Anti-hallucination**: Improved system prompt prevents fabricated journal entries on ambiguous input. Greetings and small talk get conversational responses instead.
- **Process log**: Long-press any AI response to see a bottom sheet with the full inference trace — system prompt, user input, tool calls with results, thinking tokens, and final response.
- **Two-pass voice flow**: Voice messages are transcribed in a tool-free session (tools break audio on Gemma 4), then the transcript is processed through the normal tool-enabled path.
- **Structured entity extraction**: `saveJournalEntry` takes 6 explicit parameters (summary, people, activities, mood, locations, events) for proper entity classification in the history tab.

### Agent Chat — Mobile Action Skills

Five new built-in Agent Skills ported from Mobile Actions:

- **flashlight** — Turn the device flashlight on or off
- **create-contact** — Create a new contact with name, phone, email
- **show-location** — Open a location on the map
- **wifi-settings** — Open device WiFi settings
- **calendar-event** — Create a calendar event with title and datetime

All skills are auto-enabled and use the existing `run_intent` tool pattern. IntentHandler extended with handlers for all mobile action intents.

### App Icon

- Shifted foreground icon down to center the heart within rounded/circular launcher masks (fixes clipping on Pixel devices).

## 1.0.0

Initial release with AI Journal, Agent Chat, Tiny Garden, Insurance Card Scanner, and Benchmark tasks.
