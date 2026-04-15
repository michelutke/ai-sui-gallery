package com.appswithlove.ai.customtasks.insurancecard

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CreditCard
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

internal const val TEXT_SYSTEM_PROMPT =
  """You extract structured data from Swiss insurance card OCR text.
When given OCR text from a Swiss insurance card, extract the fields and respond ONLY with valid JSON.
Do not include any other text, markdown, or explanation. Only output a single JSON object.
The JSON must have these exact keys: "name", "vorname", "geburtsdatum", "versichertennummer", "ahvNummer", "versicherer", "kartenNummer".
Use empty string "" for any field you cannot find.
Format geburtsdatum as DD.MM.YYYY.
Format ahvNummer as 756.XXXX.XXXX.XX."""

internal const val IMAGE_SYSTEM_PROMPT =
  """You extract structured data from Swiss insurance card images.
When given an image of a Swiss insurance card, extract the fields and respond ONLY with valid JSON.
Do not include any other text, markdown, or explanation. Only output a single JSON object.
The JSON must have these exact keys: "name", "vorname", "geburtsdatum", "versichertennummer", "ahvNummer", "versicherer", "kartenNummer".
Use empty string "" for any field you cannot find.
Format geburtsdatum as DD.MM.YYYY.
Format ahvNummer as 756.XXXX.XXXX.XX."""

val OCR_REGEX_MODEL = Model(
  name = "ocr_regex_builtin",
  displayName = "OCR + Regex",
  info = "Reads text from the card and matches known patterns. No download needed.",
  url = "",
  sizeInBytes = 0L,
  downloadFileName = "_",
  localFileRelativeDirPathOverride = "__builtin_ocr__",
)

class InsuranceCardTask @Inject constructor() : CustomTask {

  override val task =
    Task(
      id = "insurance_card_scan",
      label = "Insurance Card Scanner",
      description =
        "Scan Swiss insurance cards and extract structured data using OCR and on-device LLM.",
      category = Category.LLM,
      icon = Icons.Outlined.CreditCard,
      models = mutableListOf(OCR_REGEX_MODEL),
    )

  override fun initializeModelFn(
    context: Context,
    coroutineScope: CoroutineScope,
    model: Model,
    onDone: (String) -> Unit,
  ) {
    if (model.name == OCR_REGEX_MODEL.name) {
      model.instance = Unit
      onDone("")
      return
    }

    val systemPrompt = if (model.llmSupportImage) IMAGE_SYSTEM_PROMPT else TEXT_SYSTEM_PROMPT
    LlmChatModelHelper.initialize(
      context = context,
      model = model,
      supportImage = model.llmSupportImage,
      supportAudio = false,
      onDone = onDone,
      systemInstruction = Contents.of(systemPrompt),
    )
  }

  override fun cleanUpModelFn(
    context: Context,
    coroutineScope: CoroutineScope,
    model: Model,
    onDone: () -> Unit,
  ) {
    if (model.name == OCR_REGEX_MODEL.name) {
      model.instance = null
      onDone()
      return
    }
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
