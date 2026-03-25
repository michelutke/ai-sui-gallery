package com.google.ai.edge.gallery.customtasks.insurancecard

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.runtime.Composable
import com.google.ai.edge.gallery.customtasks.common.CustomTask
import com.google.ai.edge.gallery.customtasks.common.CustomTaskData
import com.google.ai.edge.gallery.data.Category
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.Task
import com.google.ai.edge.gallery.ui.llmchat.LlmChatModelHelper
import com.google.ai.edge.litertlm.Contents
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope

private const val SYSTEM_PROMPT =
  """You extract structured data from Swiss insurance card OCR text.
When given OCR text from a Swiss insurance card, extract the fields and respond ONLY with valid JSON.
Do not include any other text, markdown, or explanation. Only output a single JSON object."""

class InsuranceCardTask @Inject constructor() : CustomTask {

  override val task =
    Task(
      id = "insurance_card_scan",
      label = "Insurance Card Scanner",
      description =
        "Scan Swiss insurance cards and extract structured data using OCR and on-device LLM.",
      category = Category.LLM,
      icon = Icons.Outlined.CreditCard,
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
      systemInstruction = Contents.of(SYSTEM_PROMPT),
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
    InsuranceCardScreen(
      task = task,
      modelManagerViewModel = customTaskData.modelManagerViewModel,
      bottomPadding = customTaskData.bottomPadding,
    )
  }
}
