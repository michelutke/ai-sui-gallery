package com.appswithlove.ai.customtasks.aijournal.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "journal_entries")
data class JournalEntry(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val timestamp: Long,
  val rawText: String,
  val inputType: String, // "text", "voice_gemma", "voice_stt"
  val audioDurationMs: Long? = null,
)
