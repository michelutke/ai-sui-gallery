package com.google.ai.edge.gallery.customtasks.aijournal.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
  @PrimaryKey val id: Long,
  val text: String,
  val isUser: Boolean,
  val timestamp: Long,
  val inputType: String,
  val isLoading: Boolean = false,
  val audioDurationSec: Float? = null,
  val transcript: String? = null,
  val processLog: String? = null,
)
