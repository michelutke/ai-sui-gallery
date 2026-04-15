package com.appswithlove.ai.customtasks.notes

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.runtime.Composable
import com.appswithlove.ai.customtasks.common.CustomTask
import com.appswithlove.ai.customtasks.common.CustomTaskData
import com.appswithlove.ai.data.Category
import com.appswithlove.ai.data.Model
import com.appswithlove.ai.data.Task
import com.appswithlove.ai.ui.llmchat.LlmChatModelHelper
import com.google.ai.edge.litertlm.Contents
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope

internal const val NOTES_TASK_ID = "notes"

internal const val TITLE_SYSTEM_PROMPT =
  """You generate a short title for a note. Output ONLY the title, max 5 words, no quotes, no punctuation at end.
Input format: "Note: <content>"
Output format: just the title, one line."""

val NOTES_NO_AI_MODEL = Model(
  name = "notes_no_ai_builtin",
  displayName = "Without AI",
  info = "Manual notes only. No download needed.",
  url = "",
  sizeInBytes = 0L,
  downloadFileName = "_",
  localFileRelativeDirPathOverride = "__builtin_notes_no_ai__",
)

class NotesTask @Inject constructor() : CustomTask {

  override val task = Task(
    id = NOTES_TASK_ID,
    label = "Notes",
    description = "Quick on-device notes. Pick an AI model for auto-generated titles, or use without AI.",
    shortDescription = "Quick notes with AI titles",
    category = Category.LLM,
    icon = Icons.AutoMirrored.Outlined.StickyNote2,
    models = mutableListOf(NOTES_NO_AI_MODEL),
  )

  override fun initializeModelFn(
    context: Context,
    coroutineScope: CoroutineScope,
    model: Model,
    onDone: (String) -> Unit,
  ) {
    if (model.name == NOTES_NO_AI_MODEL.name) {
      model.instance = Unit
      onDone("")
      return
    }
    LlmChatModelHelper.initialize(
      context = context,
      model = model,
      supportImage = false,
      supportAudio = false,
      onDone = onDone,
      systemInstruction = Contents.of(TITLE_SYSTEM_PROMPT),
    )
  }

  override fun cleanUpModelFn(
    context: Context,
    coroutineScope: CoroutineScope,
    model: Model,
    onDone: () -> Unit,
  ) {
    if (model.name == NOTES_NO_AI_MODEL.name) {
      model.instance = null
      onDone()
      return
    }
    LlmChatModelHelper.cleanUp(model = model, onDone = onDone)
  }

  @Composable
  override fun MainScreen(data: Any) {
    val customTaskData = data as CustomTaskData
    NotesScreen(
      modelManagerViewModel = customTaskData.modelManagerViewModel,
      bottomPadding = customTaskData.bottomPadding,
    )
  }
}
