package com.google.ai.edge.gallery.customtasks.aijournal

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.runtime.Composable
import com.google.ai.edge.gallery.customtasks.common.CustomTask
import com.google.ai.edge.gallery.customtasks.common.CustomTaskData
import com.google.ai.edge.gallery.data.Category
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.Task
import com.google.ai.edge.gallery.ui.llmchat.LlmChatModelHelper
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope

internal const val JOURNAL_SYSTEM_PROMPT = """You are a personal journal assistant. When the user shares a journal entry:
1. Extract structured data and return a JSON block: {"summary": "...", "people": [], "activities": [], "mood": "...", "locations": [], "events": []}
2. After the JSON block, respond with a brief natural confirmation of what was saved.

When the user asks a question about their past:
1. Search the provided journal context for relevant information.
2. Answer naturally, citing dates and details from the journal.
3. If you don't have enough information, say so honestly.

Always distinguish between a journal entry (user sharing/reflecting about their day) and a question (user asking about the past). For journal entries, ALWAYS include the JSON extraction block."""

class AiJournalTask @Inject constructor() : CustomTask {
  override val task = Task(
    id = "ai_journal",
    label = "AI Journal",
    description = "Private on-device journal with AI structuring and natural language recall",
    shortDescription = "Journal with AI recall",
    category = Category.LLM,
    icon = Icons.Outlined.AutoStories,
    models = mutableListOf(),
    handleModelConfigChangesInTask = true,
  )

  override fun initializeModelFn(
    context: Context,
    coroutineScope: CoroutineScope,
    model: Model,
    onDone: (String) -> Unit,
  ) {
    LlmChatModelHelper.initialize(
      context = context,
      model = model,
      supportImage = false,
      supportAudio = model.llmSupportAudio,
      onDone = onDone,
      systemInstruction = Contents.of(listOf(Content.Text(JOURNAL_SYSTEM_PROMPT))),
    )
  }

  override fun cleanUpModelFn(
    context: Context,
    coroutineScope: CoroutineScope,
    model: Model,
    onDone: () -> Unit,
  ) {
    LlmChatModelHelper.cleanUp(model = model, onDone = onDone)
  }

  @Composable
  override fun MainScreen(data: Any) {
    val customTaskData = data as CustomTaskData
    AiJournalScreen(
      modelManagerViewModel = customTaskData.modelManagerViewModel,
      bottomPadding = customTaskData.bottomPadding,
    )
  }
}
