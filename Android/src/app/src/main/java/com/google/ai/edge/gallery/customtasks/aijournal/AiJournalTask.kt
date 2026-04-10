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
import com.google.ai.edge.litertlm.ToolProvider
import com.google.ai.edge.litertlm.tool
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope

internal const val JOURNAL_SYSTEM_PROMPT = """You are a personal journal assistant running on-device.

When the user shares experiences, stories, reflections, or events from their life, use the saveJournalEntry tool to save it. Then respond with a brief natural confirmation.

When the user asks about their past, use the searchJournal tool to look up relevant entries. You can call it multiple times with different keywords. Answer naturally using the results.

If the input is a greeting or small talk, just respond conversationally. Do NOT fabricate entries."""

class AiJournalTask @Inject constructor(
  journalTools: AiJournalTools,
) : CustomTask {

  val tools: List<ToolProvider> = listOf(tool(journalTools))

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
      tools = tools,
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
