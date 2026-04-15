package com.appswithlove.ai.customtasks.aijournal.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
  tableName = "journal_summaries",
  indices = [Index("periodType"), Index("periodStart")],
)
data class JournalSummary(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val periodType: String, // DAILY, WEEKLY, MONTHLY
  val periodStart: Long,
  val periodEnd: Long,
  val summaryText: String,
  val tokenCount: Int = 0,
)
