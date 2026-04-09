package com.google.ai.edge.gallery.customtasks.aijournal.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
  tableName = "journal_entities",
  foreignKeys = [
    ForeignKey(
      entity = JournalEntry::class,
      parentColumns = ["id"],
      childColumns = ["entryId"],
      onDelete = ForeignKey.CASCADE,
    )
  ],
  indices = [Index("entryId"), Index("entityType")],
)
data class JournalEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val entryId: Long,
  val entityType: String, // PERSON, ACTIVITY, MOOD, LOCATION, EVENT
  val entityValue: String,
)
