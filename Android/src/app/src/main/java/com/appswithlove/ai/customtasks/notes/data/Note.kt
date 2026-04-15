package com.appswithlove.ai.customtasks.notes.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class Note(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val title: String,
  val content: String,
  val createdAt: Long,
  val updatedAt: Long,
  val source: String, // "MANUAL" | "VOICE"
)
