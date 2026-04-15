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

package com.appswithlove.ai.ui.modelmanager

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.appswithlove.ai.GalleryTopAppBar
import com.appswithlove.ai.data.AppBarAction
import com.appswithlove.ai.data.AppBarActionType
import com.appswithlove.ai.data.Model
import com.appswithlove.ai.data.Task

/** A screen to manage models. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelManager(
  task: Task,
  viewModel: ModelManagerViewModel,
  enableAnimation: Boolean,
  navigateUp: () -> Unit,
  onModelClicked: (Model) -> Unit,
  sharedTransitionScope: SharedTransitionScope,
  animatedVisibilityScope: AnimatedVisibilityScope,
  modifier: Modifier = Modifier,
) {
  val title = task.label
  val modelCount by remember {
    derivedStateOf {
      val trigger = task.updateTrigger.value
      if (trigger >= 0) {
        task.models.size
      } else {
        -1
      }
    }
  }

  LaunchedEffect(modelCount) {
    if (modelCount == 0) {
      navigateUp()
    }
  }

  with(sharedTransitionScope) {
  Scaffold(
    modifier = modifier.sharedBounds(
      rememberSharedContentState(key = "task-card-${task.id}"),
      animatedVisibilityScope = animatedVisibilityScope,
      enter = fadeIn(animationSpec = tween(300)),
      exit = fadeOut(animationSpec = tween(300)),
    ),
    containerColor = MaterialTheme.colorScheme.surfaceContainer,
    topBar = {
      GalleryTopAppBar(
        title = "",
        leftAction = AppBarAction(actionType = AppBarActionType.NAVIGATE_UP, actionFn = navigateUp),
      )
    },
  ) { innerPadding ->
    ModelList(
      task = task,
      modelManagerViewModel = viewModel,
      contentPadding = innerPadding,
      enableAnimation = false,
      onModelClicked = onModelClicked,
      onBenchmarkClicked = {},
      sharedTransitionScope = sharedTransitionScope,
      animatedVisibilityScope = animatedVisibilityScope,
      modifier = Modifier.fillMaxSize(),
    )
  }
  } // with(sharedTransitionScope)
}
