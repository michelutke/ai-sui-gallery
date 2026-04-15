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

package com.appswithlove.ai.ui.navigation

import android.os.Bundle
import android.util.Log
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.EaseOutExpo
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.ui.NavDisplay
import androidx.navigation3.ui.defaultPredictivePopTransitionSpec
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import com.appswithlove.ai.customtasks.common.CustomTaskData
import com.appswithlove.ai.customtasks.common.CustomTaskDataForBuiltinTask
import com.appswithlove.ai.data.ModelDownloadStatusType
import com.appswithlove.ai.data.Task
import com.appswithlove.ai.data.isLegacyTasks
import com.appswithlove.ai.ui.benchmark.BenchmarkScreen
import com.appswithlove.ai.ui.common.ErrorDialog
import com.appswithlove.ai.ui.common.ModelPageAppBar
import com.appswithlove.ai.ui.common.chat.ModelDownloadStatusInfoPanel
import com.appswithlove.ai.ui.home.ArticleDetailScreen
import com.appswithlove.ai.ui.home.MainScreen
import com.appswithlove.ai.ui.home.getArticleById
import com.appswithlove.ai.ui.modelmanager.GlobalModelManager
import com.appswithlove.ai.ui.modelmanager.ModelInitializationStatusType
import com.appswithlove.ai.ui.modelmanager.ModelManager
import com.appswithlove.ai.ui.modelmanager.ModelManagerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val TAG = "AGGalleryNavGraph"
private const val ENTER_ANIMATION_DURATION_MS = 500
private val ENTER_ANIMATION_EASING = EaseOutExpo
private const val ENTER_ANIMATION_DELAY_MS = 100
private const val EXIT_ANIMATION_DURATION_MS = 500
private val EXIT_ANIMATION_EASING = EaseOutExpo

/** Navigation routes using Navigation 3. */
@Composable
fun GalleryNavHost(
  modelManagerViewModel: ModelManagerViewModel,
  modifier: Modifier = Modifier,
) {
  val lifecycleOwner = LocalLifecycleOwner.current
  val backStack = rememberNavBackStack(HomeRoute)
  var lastNavigatedModelName = remember { "" }

  // Track whether app is in foreground.
  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_START,
        Lifecycle.Event.ON_RESUME -> {
          modelManagerViewModel.setAppInForeground(foreground = true)
        }
        Lifecycle.Event.ON_STOP,
        Lifecycle.Event.ON_PAUSE -> {
          modelManagerViewModel.setAppInForeground(foreground = false)
        }
        else -> {}
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  SharedTransitionLayout {
    NavDisplay(
      backStack = backStack,
      onBack = { backStack.removeLastOrNull() },
      entryDecorators = listOf(
        rememberSaveableStateHolderNavEntryDecorator(),
        rememberViewModelStoreNavEntryDecorator(),
      ),
      sharedTransitionScope = this@SharedTransitionLayout,
      predictivePopTransitionSpec = defaultPredictivePopTransitionSpec(),
      transitionSpec = {
        fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
      },
      popTransitionSpec = {
        fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
      },
      entryProvider = entryProvider {
        // Home screen with bottom navigation.
        entry<HomeRoute> {
          MainScreen(
            modelManagerViewModel = modelManagerViewModel,
            sharedTransitionScope = this@SharedTransitionLayout,
            animatedVisibilityScope = LocalNavAnimatedContentScope.current,
            navigateToTaskScreen = { task ->
              backStack.add(ModelListRoute(taskId = task.id))
            },
            navigateToArticle = { articleId ->
              backStack.add(ArticleDetailRoute(articleId = articleId))
            },
            modifier = modifier,
          )
        }

        // Article detail.
        entry<ArticleDetailRoute> { navKey ->
          val article = getArticleById(navKey.articleId)
          article?.let {
            ArticleDetailScreen(
              article = it,
              sharedTransitionScope = this@SharedTransitionLayout,
              animatedVisibilityScope = LocalNavAnimatedContentScope.current,
              onBack = { backStack.removeLastOrNull() },
            )
          }
        }

        // Model list.
        entry<ModelListRoute> { navKey ->
          val task = modelManagerViewModel.getTaskById(navKey.taskId)
          task?.let {
            ModelManager(
              viewModel = modelManagerViewModel,
              task = it,
              enableAnimation = false,
              sharedTransitionScope = this@SharedTransitionLayout,
              animatedVisibilityScope = LocalNavAnimatedContentScope.current,
              onModelClicked = { model ->
                backStack.add(ModelRoute(taskId = navKey.taskId, modelName = model.name))
              },
              navigateUp = { backStack.removeLastOrNull() },
            )
          }
        }

        // Model page.
        entry<ModelRoute> { navKey ->
          val scope = rememberCoroutineScope()
          val context = LocalContext.current

          modelManagerViewModel.getModelByName(name = navKey.modelName)?.let { initialModel ->
            if (lastNavigatedModelName != navKey.modelName) {
              modelManagerViewModel.selectModel(initialModel)
              lastNavigatedModelName = navKey.modelName
            }

            val customTask = modelManagerViewModel.getCustomTaskByTaskId(id = navKey.taskId)
            if (customTask != null) {
              if (isLegacyTasks(customTask.task.id)) {
                customTask.MainScreen(
                  data = CustomTaskDataForBuiltinTask(
                    modelManagerViewModel = modelManagerViewModel,
                    onNavUp = {
                      lastNavigatedModelName = ""
                      backStack.removeLastOrNull()
                    },
                  )
                )
              } else {
                var disableAppBarControls by remember { mutableStateOf(false) }
                var hideTopBar by remember { mutableStateOf(false) }
                var customNavigateUpCallback by remember { mutableStateOf<(() -> Unit)?>(null) }
                CustomTaskScreen(
                  task = customTask.task,
                  modelManagerViewModel = modelManagerViewModel,
                  onNavigateUp = {
                    if (customNavigateUpCallback != null) {
                      customNavigateUpCallback?.invoke()
                    } else {
                      lastNavigatedModelName = ""
                      backStack.removeLastOrNull()

                      for (curModel in customTask.task.models) {
                        val instanceToCleanUp = curModel.instance
                        scope.launch(Dispatchers.Default) {
                          modelManagerViewModel.cleanupModel(
                            context = context,
                            task = customTask.task,
                            model = curModel,
                            instanceToCleanUp = instanceToCleanUp,
                          )
                        }
                      }
                    }
                  },
                  disableAppBarControls = disableAppBarControls,
                  hideTopBar = hideTopBar,
                  useThemeColor = customTask.task.useThemeColor,
                ) { bottomPadding ->
                  customTask.MainScreen(
                    data = CustomTaskData(
                      modelManagerViewModel = modelManagerViewModel,
                      bottomPadding = bottomPadding,
                      setAppBarControlsDisabled = { disableAppBarControls = it },
                      setTopBarVisible = { hideTopBar = !it },
                      setCustomNavigateUpCallback = { customNavigateUpCallback = it },
                    )
                  )
                }
              }
            }
          }
        }

        // Global model manager.
        entry<ModelManagerRoute> {
          GlobalModelManager(
            viewModel = modelManagerViewModel,
            navigateUp = { backStack.removeLastOrNull() },
            onModelSelected = { task, model ->
              backStack.add(ModelRoute(taskId = task.id, modelName = model.name))
            },
            onBenchmarkClicked = { model ->
              backStack.add(BenchmarkRoute(modelName = model.name))
            },
          )
        }

        // Benchmark.
        entry<BenchmarkRoute> { navKey ->
          modelManagerViewModel.getModelByName(name = navKey.modelName)?.let { model ->
            BenchmarkScreen(
              initialModel = model,
              modelManagerViewModel = modelManagerViewModel,
              onBackClicked = { backStack.removeLastOrNull() },
            )
          }
        }
      },
    )
  } // SharedTransitionLayout

  // Handle incoming intents for deep links
  val intent = androidx.activity.compose.LocalActivity.current?.intent
  val data = intent?.data
  if (data != null) {
    intent.data = null
    Log.d(TAG, "navigation link clicked: $data")
    if (data.toString().startsWith("com.appswithlove.ai://model/")) {
      if (data.pathSegments.size >= 2) {
        val taskId = data.pathSegments[data.pathSegments.size - 2]
        val modelName = data.pathSegments.last()
        modelManagerViewModel.getModelByName(name = modelName)?.let { model ->
          backStack.add(ModelRoute(taskId = taskId, modelName = model.name))
        }
      } else {
        Log.e(TAG, "Malformed deep link URI received: $data")
      }
    } else if (data.toString() == "com.appswithlove.ai://global_model_manager") {
      backStack.add(ModelManagerRoute)
    }
  }
}

@Composable
private fun CustomTaskScreen(
  task: Task,
  modelManagerViewModel: ModelManagerViewModel,
  disableAppBarControls: Boolean,
  hideTopBar: Boolean,
  useThemeColor: Boolean,
  onNavigateUp: () -> Unit,
  content: @Composable (bottomPadding: Dp) -> Unit,
) {
  val modelManagerUiState by modelManagerViewModel.uiState.collectAsState()
  val selectedModel = modelManagerUiState.selectedModel
  val scope = rememberCoroutineScope()
  val context = LocalContext.current
  var showErrorDialog by remember { mutableStateOf(false) }
  var appBarHeight by remember { mutableIntStateOf(0) }

  val curDownloadStatus = modelManagerUiState.modelDownloadStatus[selectedModel.name]
  LaunchedEffect(curDownloadStatus, selectedModel.name) {
    if (curDownloadStatus?.status == ModelDownloadStatusType.SUCCEEDED) {
      Log.d(TAG, "Initializing model '${selectedModel.name}' from CustomTaskScreen launched effect")
      modelManagerViewModel.initializeModel(context, task = task, model = selectedModel)
    }
  }

  val modelInitializationStatus = modelManagerUiState.modelInitializationStatus[selectedModel.name]
  LaunchedEffect(modelInitializationStatus) {
    showErrorDialog = modelInitializationStatus?.status == ModelInitializationStatusType.ERROR
  }

  Scaffold(
    containerColor = MaterialTheme.colorScheme.surfaceContainer,
    topBar = {
      AnimatedVisibility(
        !hideTopBar,
        enter = slideInVertically { -it },
        exit = slideOutVertically { -it },
      ) {
        ModelPageAppBar(
          task = task,
          model = selectedModel,
          modelManagerViewModel = modelManagerViewModel,
          inProgress = disableAppBarControls,
          modelPreparing = disableAppBarControls,
          canShowResetSessionButton = false,
          useThemeColor = useThemeColor,
          modifier = Modifier.onGloballyPositioned { coordinates -> appBarHeight = coordinates.size.height },
          hideModelSelector = task.models.size <= 1,
          onConfigChanged = { _, _ -> },
          onBackClicked = { onNavigateUp() },
          onModelSelected = { prevModel, newSelectedModel ->
            val instanceToCleanUp = prevModel.instance
            scope.launch(Dispatchers.Default) {
              if (prevModel.name != newSelectedModel.name) {
                modelManagerViewModel.cleanupModel(
                  context = context,
                  task = task,
                  model = prevModel,
                  instanceToCleanUp = instanceToCleanUp,
                )
              }
              Log.d(TAG, "from model picker. new: ${newSelectedModel.name}")
              modelManagerViewModel.selectModel(model = newSelectedModel)
            }
          },
        )
      }
    }
  ) { innerPadding ->
    val targetPaddingDp =
      if (!hideTopBar && appBarHeight > 0) {
        with(LocalDensity.current) { appBarHeight.toDp() }
      } else {
        WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
      }

    val animatedTopPadding by animateDpAsState(
      targetValue = targetPaddingDp,
      animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
      label = "TopPaddingAnimation",
    )

    Box(
      modifier = Modifier.padding(
        top = if (!hideTopBar) innerPadding.calculateTopPadding() else animatedTopPadding,
        start = innerPadding.calculateStartPadding(LocalLayoutDirection.current),
        end = innerPadding.calculateStartPadding(LocalLayoutDirection.current),
      )
    ) {
      val curModelDownloadStatus = modelManagerUiState.modelDownloadStatus[selectedModel.name]
      AnimatedContent(
        targetState = curModelDownloadStatus?.status == ModelDownloadStatusType.SUCCEEDED
      ) { targetState ->
        when (targetState) {
          true -> content(innerPadding.calculateBottomPadding())
          false -> ModelDownloadStatusInfoPanel(
            model = selectedModel,
            task = task,
            modelManagerViewModel = modelManagerViewModel,
          )
        }
      }
    }
  }

  if (showErrorDialog) {
    ErrorDialog(
      error = modelInitializationStatus?.error ?: "",
      onDismiss = {
        showErrorDialog = false
        onNavigateUp()
      },
    )
  }
}
