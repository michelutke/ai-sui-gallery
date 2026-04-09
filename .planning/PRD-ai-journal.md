# PRD: AI Journal — On-Device Personal Journal with AI Structuring & Recall

## Context

New use case for the Showcases app. A fully private, on-device AI journal where users speak or type about their day. Gemma 4 E4B structures entries into categories (people, activities, mood, locations) and stores them locally. Users can query their journal history naturally ("when did I last meet Hans?") and get answers from accumulated journal data. Everything stays on-device — zero cloud dependency.

---

## Screens

### 1. Chat Tab (default)

Single input field at the bottom (text + mic button). AI auto-distinguishes journal entries from questions.

**Journal entry flow:**
1. User types or records voice
2. Voice: <30s → Gemma 4 native audio input. ≥30s → ML Kit STT
3. Voice messages show as audio bubbles with a label: `Gemma` or `STT` indicating transcription engine
4. Long-press on voice bubble → shows transcribed text overlay
5. Entry sent → Gemma extracts structured data (people, mood, activity, location, date)
6. AI responds with brief confirmation: "Saved. Logged: hiking with Anna, mood: happy, Zürich"
7. Raw text + structured data persisted to SQLite

**Query flow:**
1. User types a question (AI detects intent automatically)
2. System assembles context: tiered journal data + relevant FTS5 results
3. Gemma answers from journal knowledge
4. Answer shown as AI chat bubble

**UI elements:**
- Chat bubbles: user (right-aligned), AI (left-aligned)
- Voice recording: hold-to-record mic button or tap-to-toggle
- Voice bubble shows waveform + duration + engine badge (`Gemma`/`STT`)
- Long-press voice bubble → transcript popover

### 2. History Tab

No AI involved — pure DB read. Shows structured journal data grouped by day.

**Day cards:**
- Date header (e.g. "Wednesday, April 9, 2026")
- List of extracted entries for that day, each showing:
  - Time
  - Summary text (from AI extraction)
  - Tag chips: people (blue), activities (green), mood (orange emoji), locations (purple)
- Tap day card → expands to show full raw entries for that day
- Scrollable reverse-chronological list

**Filter/search bar at top:**
- Filter by: person, activity, mood, location, date range
- FTS5 keyword search across raw entries

---

## Data Model (SQLite via Room)

### journal_entries
| Column | Type | Description |
|--------|------|-------------|
| id | INTEGER PK | Auto-increment |
| timestamp | INTEGER | Unix millis |
| raw_text | TEXT | Original user input |
| input_type | TEXT | `text`, `voice_gemma`, `voice_stt` |
| audio_duration_ms | INTEGER? | Duration if voice input |

### journal_entities
| Column | Type | Description |
|--------|------|-------------|
| id | INTEGER PK | Auto-increment |
| entry_id | INTEGER FK | → journal_entries.id |
| entity_type | TEXT | `PERSON`, `ACTIVITY`, `MOOD`, `LOCATION`, `EVENT` |
| entity_value | TEXT | Extracted value |

### journal_summaries
| Column | Type | Description |
|--------|------|-------------|
| id | INTEGER PK | Auto-increment |
| period_type | TEXT | `DAILY`, `WEEKLY`, `MONTHLY` |
| period_start | INTEGER | Unix millis |
| period_end | INTEGER | Unix millis |
| summary_text | TEXT | LLM-generated summary |
| token_count | INTEGER | Estimated tokens for budget tracking |

---

## Voice Input Logic

```
if (recording_duration < 30s) {
  → Gemma 4 native audio input (direct to LLM)
  → badge: "Gemma"
} else {
  → ML Kit GenAI Speech Recognition (streaming STT)
  → transcribed text sent to Gemma as text input
  → badge: "STT"
}
```

Long-press on any voice bubble → overlay/bottom sheet showing the full transcribed text.

---

## AI Prompting Strategy

### System prompt (extraction mode)
```
You are a personal journal assistant. When the user shares a journal entry:
1. Extract and return JSON: {"summary": "...", "people": [], "activities": [], "mood": "...", "locations": [], "events": []}
2. Respond with a brief natural confirmation of what was saved.

When the user asks a question about their past:
1. Search the provided journal context for relevant information.
2. Answer naturally, citing dates and details from the journal.
3. If you don't have enough information, say so honestly.

Always distinguish between a journal entry (user sharing/reflecting) and a question (user asking about the past).
```

### Context assembly for queries (tiered loading)
```
Tier 1: Raw entries last 7 days              (~1,890 tokens)
Tier 2: Weekly summaries weeks 2-8           (~1,000 tokens)
Tier 3: Monthly summaries months 3-6         (~800 tokens)
Tier 4: Entity tuples month 7+              (~600 tokens)
+ FTS5 results for specific entity matches   (variable)
= ~4,300 tokens for 1 year of data
```

---

## Background Processing (WorkManager)

**Weekly job (Sunday night):**
1. Summarize past 7 days raw entries → `journal_summaries` (WEEKLY)
2. Extract/verify entities for past week → `journal_entities`

**Monthly job (1st of month):**
1. Summarize 4 weekly summaries → `journal_summaries` (MONTHLY)

**Daily job:**
1. Generate daily summary → `journal_summaries` (DAILY) — used in History tab cards

---

## What's IN v1

- Text input journaling
- Voice input with dual engine (Gemma <30s, STT ≥30s)
- Engine badge on voice messages (Gemma/STT)
- Long-press voice bubble → show transcript
- AI structured extraction (people, mood, activities, locations, events)
- Brief save confirmation with extracted tags
- Natural language querying over journal history
- Chat tab (conversation UI)
- History tab (day cards from DB, no AI, with tag chips)
- Filter/search in History tab
- SQLite/Room persistence
- Tiered context loading for queries (raw → weekly → monthly → entities)
- Background summarization via WorkManager (daily/weekly/monthly)
- Gemma 4 E4B as default model

## What's NOT in v1

- EmbeddingGemma semantic search (v2 — "when was I happiest?")
- Export/import journal data
- Photo/image attachments to entries
- Calendar integration
- Mood trends / analytics charts
- Multi-device sync
- End-to-end encryption at rest (rely on device encryption)
- Widget for quick voice entry

---

## Technical Integration

Follows existing custom task pattern:
- `customtasks/aijournal/` directory
- `AiJournalTask.kt` implements `CustomTask` interface
- `AiJournalModule.kt` Hilt module with `@Provides @IntoSet`
- Auto-discovered by app via Hilt multibinding — no nav changes needed
- Uses `LlmChatModelHelper` for Gemma initialization
- New Room database for journal persistence (separate from DataStore)
- New `NavRoutes` not needed — uses existing `ModelRoute` pattern

---

## Files to Create

```
customtasks/aijournal/
├── AiJournalTask.kt          — CustomTask impl, model init, system prompt
├── AiJournalScreen.kt        — Main screen with Chat/History tabs
├── AiJournalChatTab.kt       — Chat UI, voice recording, message bubbles
├── AiJournalHistoryTab.kt    — Day cards, filters, DB-only reads
├── AiJournalViewModel.kt     — State management, LLM interaction, context assembly
├── AiJournalModule.kt        — Hilt DI
├── data/
│   ├── JournalDatabase.kt    — Room database definition
│   ├── JournalDao.kt         — DAO with FTS5 queries
│   ├── JournalEntry.kt       — Entity: raw entries
│   ├── JournalEntity.kt      — Entity: extracted people/mood/etc
│   └── JournalSummary.kt     — Entity: tiered summaries
├── worker/
│   └── JournalSummarizationWorker.kt — WorkManager for background summarization
```

## Verification

1. Build + install on device
2. Open AI Journal from Use Cases
3. Type a journal entry → verify AI extracts tags and confirms
4. Record voice <30s → verify Gemma badge, long-press shows transcript
5. Record voice >30s → verify STT badge, long-press shows transcript
6. Ask "what did I do today?" → verify AI answers from context
7. Switch to History tab → verify day cards show with correct tags
8. Filter by person name → verify results
9. Wait for daily summary job → verify summary appears
10. Add entries over multiple days → verify tiered context works for queries
