---
name: calendar-event
description: Create a calendar event.
---

# Calendar Event

## Instructions

Call the `run_intent` tool with the following exact parameters:

- intent: create_calendar_event
- parameters: A JSON string with the following fields:
  - title: the event title. String.
  - datetime: the event date and time in ISO format (e.g. 2026-04-15T14:30:00). String.
