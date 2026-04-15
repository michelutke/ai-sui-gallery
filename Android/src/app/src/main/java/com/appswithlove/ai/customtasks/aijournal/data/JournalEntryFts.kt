package com.appswithlove.ai.customtasks.aijournal.data

import androidx.room.Entity
import androidx.room.Fts4

@Fts4(contentEntity = JournalEntry::class)
@Entity(tableName = "journal_entries_fts")
data class JournalEntryFts(
  val rawText: String,
)
