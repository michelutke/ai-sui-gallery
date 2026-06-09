/*
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.appswithlove.ai.ui.common.chat

// import com.appswithlove.ai.ui.preview.PreviewChatModel
// import com.appswithlove.ai.ui.preview.PreviewModelManagerViewModel
// import com.appswithlove.ai.ui.preview.TASK_TEST1
// import com.appswithlove.ai.ui.theme.GalleryTheme

import android.graphics.Bitmap
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.appswithlove.ai.R
import com.appswithlove.ai.data.BuiltInTaskId
import com.appswithlove.ai.data.ConfigKeys
import com.appswithlove.ai.data.Model
import com.appswithlove.ai.data.ModelDownloadStatusType
import com.appswithlove.ai.data.Task
import com.appswithlove.ai.ui.common.ModelConfigDialog
import com.appswithlove.ai.ui.common.ModelPageAppBar
import com.appswithlove.ai.ui.common.copyBitmapToClipboard
import com.appswithlove.ai.ui.common.saveBitmapToMediaStore
import com.appswithlove.ai.ui.common.shareBitmap
import com.appswithlove.ai.ui.modelmanager.ModelInitializationStatusType
import com.appswithlove.ai.ui.modelmanager.ModelManagerViewModel
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val TAG = "AGChatView"

data class SendMessageTrigger(val model: Model, val messages: List<ChatMessage>)

/**
 * A composable that displays a chat interface, allowing users to interact with different models
 * associated with a given task.
 *
 * This composable provides a horizontal pager for switching between models, a model selector for
 * configuring the selected model, and a chat panel for sending and receiving messages. It also
 * manages model initialization, cleanup, and download status, and handles navigation and system
 * back gestures.
 */
@Composable
fun ChatView(
  task: Task,
  viewModel: ChatViewModel,
  modelManagerViewModel: ModelManagerViewModel,
  onSendMessage: (Model, List<ChatMessage>) -> Unit,
  onRunAgainClicked: (Model, ChatMessage) -> Unit,
  onBenchmarkClicked: (Model, ChatMessage, Int, Int) -> Unit,
  navigateUp: () -> Unit,
  modifier: Modifier = Modifier,
  onResetSessionClicked: (Model, List<ChatMessage>, () -> Unit) -> Unit = { _, _, _ -> },
  onStreamImageMessage: (Model, ChatMessageImage) -> Unit = { _, _ -> },
  onStopButtonClicked: (Model) -> Unit = {},
  onSkillClicked: () -> Unit = {},
  showStopButtonInInputWhenInProgress: Boolean = false,
  composableBelowMessageList: @Composable (Model) -> Unit = {},
  showImagePicker: Boolean = false,
  showAudioPicker: Boolean = false,
  emptyStateComposable: @Composable (Model) -> Unit = {},
  allowEditingSystemPrompt: Boolean = false,
  curSystemPrompt: String = "",
  onSystemPromptChanged: (String) -> Unit = {},
  sendMessageTrigger: SendMessageTrigger? = null,
) {
  val uiState by viewModel.uiState.collectAsState()
  val modelManagerUiState by modelManagerViewModel.uiState.collectAsState()
  val selectedModel = modelManagerUiState.selectedModel

  // Image viewer related.
  var selectedImageIndex by remember { mutableIntStateOf(-1) }
  var allImageViewerImages by remember { mutableStateOf<List<Bitmap>>(listOf()) }
  var showImageViewer by remember { mutableStateOf(false) }

  // Chat history drawer.
  val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
  val allHistorySessions by viewModel.historySessions.collectAsState()
  val historySessions =
    remember(allHistorySessions, task.id) { allHistorySessions.filter { it.taskId == task.id } }

  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var navigatingUp by remember { mutableStateOf(false) }
  var showConfigDialog by remember { mutableStateOf(false) }

  // Saves the current session then starts a fresh one. Shared by the history drawer and the
  // in-chat toolbar's "new chat" action.
  val startNewChat = {
    viewModel.saveSession(
      sessionId = viewModel.currentSessionId,
      messages = uiState.messagesByModel[selectedModel.name] ?: emptyList(),
      originalModel = selectedModel.name,
      taskId = task.id,
    )
    onResetSessionClicked(selectedModel, emptyList()) {}
    viewModel.currentSessionId = UUID.randomUUID().toString()
  }

  // Auto-save the current session whenever the model finishes responding.
  val currentMessages = uiState.messagesByModel[selectedModel.name] ?: emptyList()
  LaunchedEffect(uiState.inProgress) {
    if (!uiState.inProgress && currentMessages.isNotEmpty()) {
      viewModel.saveSession(
        sessionId = viewModel.currentSessionId,
        messages = currentMessages,
        originalModel = selectedModel.name,
        taskId = task.id,
      )
    }
  }

  val handleNavigateUp = {
    navigatingUp = true
    navigateUp()

    // clean up all models.
    scope.launch(Dispatchers.Default) {
      for (model in task.models) {
        modelManagerViewModel.cleanupModel(context = context, task = task, model = model)
      }
    }
  }

  // Initialize model when model/download state changes.
  val curDownloadStatus = modelManagerUiState.modelDownloadStatus[selectedModel.name]
  LaunchedEffect(curDownloadStatus, selectedModel.name) {
    if (!navigatingUp) {
      if (curDownloadStatus?.status == ModelDownloadStatusType.SUCCEEDED) {
        Log.d(TAG, "Initializing model '${selectedModel.name}' from ChatView launched effect")
        modelManagerViewModel.initializeModel(context, task = task, model = selectedModel)
      }
    }
  }

  LaunchedEffect(sendMessageTrigger) {
    sendMessageTrigger?.let { trigger -> onSendMessage(trigger.model, trigger.messages) }
  }

  // Handle system's edge swipe.
  BackHandler {
    val modelInitializationStatus =
      modelManagerUiState.modelInitializationStatus[selectedModel.name]
    val isModelInitializing =
      modelInitializationStatus?.status == ModelInitializationStatusType.INITIALIZING
    if (!isModelInitializing && !uiState.inProgress) {
      handleNavigateUp()
    }
  }

  // Right-side chat-history drawer. Wrap in RTL so the ModalNavigationDrawer slides in from the
  // right, then flip the contents back to LTR.
  CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
    ModalNavigationDrawer(
      drawerState = drawerState,
      gesturesEnabled = drawerState.isOpen,
      drawerContent = {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
          ModalDrawerSheet {
            ChatHistorySideSheetContent(
              history = historySessions,
              onHistoryItemClicked = { sessionId ->
                val session = historySessions.firstOrNull { it.sessionId == sessionId }
                if (session != null) {
                  viewModel.saveSession(
                    sessionId = viewModel.currentSessionId,
                    messages = uiState.messagesByModel[selectedModel.name] ?: emptyList(),
                    originalModel = selectedModel.name,
                    taskId = task.id,
                  )
                  val messages = deserializeProtoMessages(session.messagesList)
                  onResetSessionClicked(selectedModel, messages) {
                    for (msg in messages) {
                      viewModel.addMessage(selectedModel, msg)
                    }
                  }
                  viewModel.currentSessionId = session.sessionId
                }
                scope.launch { drawerState.close() }
              },
              onHistoryItemDeleted = { sessionId -> viewModel.deleteSession(sessionId) },
              onHistoryItemsDeleteAll = { viewModel.clearAllSessions() },
              onNewChatClicked = {
                startNewChat()
                scope.launch { drawerState.close() }
              },
              onDismissed = { scope.launch { drawerState.close() } },
            )
          }
        }
      },
    ) {
      CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Scaffold(
          modifier = modifier,
          topBar = {
            ModelPageAppBar(
              task = task,
              model = selectedModel,
              modelManagerViewModel = modelManagerViewModel,
              // Config / history / new-chat moved to the in-chat floating toolbar above the input.
              canShowResetSessionButton = false,
              showConfigButton = false,
              shouldShowHistoryButton = false,
              isResettingSession = uiState.isResettingSession,
              inProgress = uiState.inProgress,
              modelPreparing = uiState.preparing,
              onConfigChanged = { old, new ->
          // Filter out config values that are not relevant to the task.
          //
          // - The "reset conversation turn count" is only valid for tiny garden task.
          val filteredOld = old.toMutableMap()
          val filteredNew = new.toMutableMap()
          if (task.id != BuiltInTaskId.LLM_TINY_GARDEN) {
            filteredOld.remove(ConfigKeys.RESET_CONVERSATION_TURN_COUNT.label)
            filteredNew.remove(ConfigKeys.RESET_CONVERSATION_TURN_COUNT.label)
          }
          viewModel.addConfigChangedMessage(
            oldConfigValues = filteredOld,
            newConfigValues = filteredNew,
            model = selectedModel,
          )
        },
        onBackClicked = { handleNavigateUp() },
        onModelSelected = { prevModel, curModel ->
          if (prevModel.name != curModel.name) {
            modelManagerViewModel.cleanupModel(context = context, task = task, model = prevModel)
          }
          modelManagerViewModel.selectModel(model = curModel)
        },
        allowEditingSystemPrompt = allowEditingSystemPrompt,
        curSystemPrompt = curSystemPrompt,
        onSystemPromptChanged = onSystemPromptChanged,
      )
    },
  ) { innerPadding ->
    Box {
      val curModelDownloadStatus = modelManagerUiState.modelDownloadStatus[selectedModel.name]

      composableBelowMessageList(selectedModel)

      if (showConfigDialog) {
        ModelConfigDialog(
          model = selectedModel,
          task = task,
          modelManagerViewModel = modelManagerViewModel,
          onDismissed = { showConfigDialog = false },
          onConfigChanged = { old, new ->
            val filteredOld = old.toMutableMap()
            val filteredNew = new.toMutableMap()
            if (task.id != BuiltInTaskId.LLM_TINY_GARDEN) {
              filteredOld.remove(ConfigKeys.RESET_CONVERSATION_TURN_COUNT.label)
              filteredNew.remove(ConfigKeys.RESET_CONVERSATION_TURN_COUNT.label)
            }
            viewModel.addConfigChangedMessage(
              oldConfigValues = filteredOld,
              newConfigValues = filteredNew,
              model = selectedModel,
            )
          },
          allowEditingSystemPrompt = allowEditingSystemPrompt,
          curSystemPrompt = curSystemPrompt,
          onSystemPromptChanged = onSystemPromptChanged,
        )
      }

      Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        AnimatedContent(
          targetState = curModelDownloadStatus?.status == ModelDownloadStatusType.SUCCEEDED
        ) { targetState ->
          when (targetState) {
            // Main UI when model is downloaded.
            true ->
              ChatPanel(
                modelManagerViewModel = modelManagerViewModel,
                task = task,
                selectedModel = selectedModel,
                viewModel = viewModel,
                innerPadding = innerPadding,
                navigateUp = navigateUp,
                onSendMessage = { model, messages -> onSendMessage(model, messages) },
                onRunAgainClicked = onRunAgainClicked,
                onBenchmarkClicked = onBenchmarkClicked,
                onStreamImageMessage = onStreamImageMessage,
                onStreamEnd = { averageFps ->
                  viewModel.addMessage(
                    model = selectedModel,
                    message =
                      ChatMessageInfo(
                        content = "Live camera session ended. Average FPS: $averageFps"
                      ),
                  )
                },
                onStopButtonClicked = { onStopButtonClicked(selectedModel) },
                onImageSelected = { bitmaps, selectedBitmapIndex ->
                  selectedImageIndex = selectedBitmapIndex
                  allImageViewerImages = bitmaps
                  showImageViewer = true
                },
                onSkillClicked = onSkillClicked,
                modifier = Modifier.weight(1f),
                showStopButtonInInputWhenInProgress = showStopButtonInInputWhenInProgress,
                showImagePicker = showImagePicker,
                showAudioPicker = showAudioPicker,
                emptyStateComposable = emptyStateComposable,
                showInputToolbar = true,
                showConfigInToolbar = selectedModel.configs.isNotEmpty(),
                onConfigClicked = { showConfigDialog = true },
                onHistoryClicked = { scope.launch { drawerState.open() } },
                onNewChatClicked = startNewChat,
              )
            // Model download
            false ->
              ModelDownloadStatusInfoPanel(
                model = selectedModel,
                task = task,
                modelManagerViewModel = modelManagerViewModel,
              )
          }
        }
      }

      // Image viewer.
      if (showImageViewer) {
        Dialog(
          onDismissRequest = { showImageViewer = false },
          properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
          val dialogSnackbarHostState = remember { SnackbarHostState() }
          val pagerState =
            rememberPagerState(
              pageCount = { allImageViewerImages.size },
              initialPage = selectedImageIndex,
            )
          val scrollEnabled = remember { mutableStateOf(true) }
          Box(modifier = Modifier.fillMaxSize()) {
            HorizontalPager(
              state = pagerState,
              userScrollEnabled = scrollEnabled.value,
              modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.95f)),
            ) { page ->
              allImageViewerImages[page].let { image ->
                ZoomableImage(
                  bitmap = image.asImageBitmap(),
                  pagerState = pagerState,
                  modifier = Modifier.fillMaxSize(),
                )
              }
            }

            val curBitmap = allImageViewerImages.getOrNull(pagerState.currentPage)

            // Top item: ArrowBack (top left).
            Row(
              modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(16.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              IconButton(onClick = { showImageViewer = false }) {
                Icon(
                  Icons.AutoMirrored.Rounded.ArrowBack,
                  contentDescription = stringResource(R.string.close),
                  tint = Color.White,
                )
              }
            }

            // Bottom items: Share, Copy, Save.
            val copySuccessMsg = stringResource(R.string.snackbar_copy_to_clipboard_success)
            val saveSuccessMsg = stringResource(R.string.snackbar_save_to_album_success)
            val saveFailedMsg = stringResource(R.string.snackbar_save_to_album_failed)
            Row(
              modifier =
                Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(bottom = 24.dp),
              horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
              // Share button
              IconButton(
                onClick = {
                  curBitmap?.let { bitmap -> scope.launch { context.shareBitmap(bitmap) } }
                },
                modifier = Modifier.size(64.dp),
              ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                  Icon(
                    Icons.Rounded.Share,
                    contentDescription = stringResource(R.string.share),
                    tint = Color.White,
                  )
                  Spacer(modifier = Modifier.height(4.dp))
                  Text(
                    text = stringResource(R.string.share),
                    color = Color.White,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                  )
                }
              }

              // Copy button
              IconButton(
                onClick = {
                  curBitmap?.let { bitmap ->
                    scope.launch {
                      context.copyBitmapToClipboard(bitmap)
                      dialogSnackbarHostState.showSnackbar(copySuccessMsg)
                    }
                  }
                },
                modifier = Modifier.size(64.dp),
              ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                  Icon(
                    Icons.Rounded.ContentCopy,
                    contentDescription = stringResource(R.string.copy),
                    tint = Color.White,
                  )
                  Spacer(modifier = Modifier.height(4.dp))
                  Text(
                    text = stringResource(R.string.copy),
                    color = Color.White,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                  )
                }
              }

              // Save button
              IconButton(
                onClick = {
                  curBitmap?.let { bitmap ->
                    scope.launch {
                      val success =
                        context.saveBitmapToMediaStore(
                          bitmap,
                          "chat_image_${System.currentTimeMillis()}.png",
                        )
                      if (success) {
                        dialogSnackbarHostState.showSnackbar(saveSuccessMsg)
                      } else {
                        dialogSnackbarHostState.showSnackbar(saveFailedMsg)
                      }
                    }
                  }
                },
                modifier = Modifier.size(64.dp),
              ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                  Icon(
                    Icons.Rounded.Download,
                    contentDescription = stringResource(R.string.save),
                    tint = Color.White,
                  )
                  Spacer(modifier = Modifier.height(4.dp))
                  Text(
                    text = stringResource(R.string.save),
                    color = Color.White,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                  )
                }
              }
            }
            SnackbarHost(
              hostState = dialogSnackbarHostState,
              modifier = Modifier.align(Alignment.BottomCenter),
            )
          }
        }
      }
    }
      }
      }
    }
  }
}

/**
 * Converts persisted [ChatMessageProto] entries back into runtime [ChatMessage] objects.
 *
 * Only text-based message types are persisted/restored (image and audio messages are not saved to
 * history), matching [ChatViewModel.saveSession].
 */
private fun deserializeProtoMessages(
  protoMessages: List<com.appswithlove.ai.proto.ChatMessageProto>
): List<ChatMessage> {
  return protoMessages.mapNotNull { protoMsg ->
    val side =
      when (protoMsg.side) {
        com.appswithlove.ai.proto.ChatSideProto.CHAT_SIDE_USER -> ChatSide.USER
        com.appswithlove.ai.proto.ChatSideProto.CHAT_SIDE_MODEL -> ChatSide.AGENT
        com.appswithlove.ai.proto.ChatSideProto.CHAT_SIDE_SYSTEM -> ChatSide.SYSTEM
        else -> ChatSide.SYSTEM
      }
    when (protoMsg.messageType) {
      "TEXT" ->
        ChatMessageText(
          content = protoMsg.content,
          side = side,
          latencyMs = protoMsg.latencyMs,
          isMarkdown = protoMsg.isMarkdown,
          accelerator = protoMsg.accelerator,
          hideSenderLabel = protoMsg.hideSenderLabel,
        )
      "THINKING" ->
        ChatMessageThinking(
          content = protoMsg.content,
          side = side,
          inProgress = protoMsg.inProgress,
          accelerator = protoMsg.accelerator,
          hideSenderLabel = protoMsg.hideSenderLabel,
        )
      "INFO" -> ChatMessageInfo(protoMsg.content)
      "WARNING" -> ChatMessageWarning(protoMsg.content)
      "ERROR" -> ChatMessageError(protoMsg.content)
      else -> null
    }
  }
}
