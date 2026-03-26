package com.google.ai.edge.gallery.customtasks.emoji

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EmojiEmotions
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

private const val SYSTEM_PROMPT =
  "You are an emoji assistant. When given a text after 'Text:', respond with ONLY a single emoji on the 'Emoji:' line. " +
    "Examples:\nText: I love you\nEmoji: \u2764\uFE0F\nText: It's cold\nEmoji: \uD83E\uDD76\nText: I'm happy\nEmoji: \uD83D\uDE00"

class EmojiTask @Inject constructor() : CustomTask {
  override val task =
    Task(
      id = "emoji_generator",
      label = "Emoji Generator",
      description = "Generate contextual emojis from text in real-time",
      category = Category.LLM,
      icon = Icons.Outlined.EmojiEmotions,
      models = mutableListOf(),
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
      supportAudio = false,
      onDone = onDone,
      systemInstruction = Contents.of(listOf(Content.Text(SYSTEM_PROMPT))),
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
    EmojiScreen(
      modelManagerViewModel = customTaskData.modelManagerViewModel,
      bottomPadding = customTaskData.bottomPadding,
    )
  }
}
