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

package com.google.ai.edge.gallery.ui.common

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.ai.edge.gallery.R
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.Task
import com.google.ai.edge.gallery.ui.common.modelitem.StatusIcon
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerUiState
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel
import com.google.ai.edge.gallery.ui.theme.labelSmallNarrow

@Composable
fun ModelPicker(
  task: Task,
  modelManagerViewModel: ModelManagerViewModel,
  onModelSelected: (Model) -> Unit,
) {
  val modelManagerUiState by modelManagerViewModel.uiState.collectAsState()
  var showMemoryWarning by remember { mutableStateOf(false) }
  var modelToPick by remember { mutableStateOf<Model?>(null) }
  val context = LocalContext.current

  // Partition into built-in (no download) and AI models
  val builtInModels = task.models.filter { it.url.isEmpty() && it.sizeInBytes == 0L }
  val aiModels = task.models.filter { it.url.isNotEmpty() || it.sizeInBytes > 0L }
  val showSections = builtInModels.isNotEmpty() && aiModels.isNotEmpty()

  val onRowClicked: (Model) -> Unit = { model ->
    if (isMemoryLow(context = context, model = model)) {
      modelToPick = model
      showMemoryWarning = true
    } else {
      onModelSelected(model)
    }
  }

  Column(modifier = Modifier.padding(bottom = 8.dp)) {
    // Title
    Row(
      modifier = Modifier.padding(horizontal = 16.dp).padding(top = 4.dp, bottom = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Icon(
        task.icon ?: ImageVector.vectorResource(task.iconVectorResourceId!!),
        tint = getTaskIconColor(task = task),
        modifier = Modifier.size(16.dp),
        contentDescription = null,
      )
      Text(
        "${task.label} models",
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.titleMedium,
        color = getTaskIconColor(task = task),
      )
    }

    if (showSections) {
      Text(
        "Without AI",
        modifier = Modifier.padding(horizontal = 40.dp).padding(top = 8.dp, bottom = 4.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    for (model in builtInModels) {
      ModelPickerRow(
        model = model,
        uiState = modelManagerUiState,
        task = task,
        showDownloadInfo = false,
        onClick = { onRowClicked(model) },
      )
    }

    if (showSections) {
      Text(
        "AI Models",
        modifier = Modifier.padding(horizontal = 40.dp).padding(top = 12.dp, bottom = 4.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    for (model in aiModels) {
      ModelPickerRow(
        model = model,
        uiState = modelManagerUiState,
        task = task,
        showDownloadInfo = true,
        onClick = { onRowClicked(model) },
      )
    }
  }

  if (showMemoryWarning) {
    MemoryWarningAlert(
      onProceeded = {
        val curModelToPick = modelToPick
        if (curModelToPick != null) {
          onModelSelected(curModelToPick)
        }
        showMemoryWarning = false
      },
      onDismissed = { showMemoryWarning = false },
    )
  }
}

@Composable
private fun ModelPickerRow(
  model: Model,
  uiState: ModelManagerUiState,
  task: Task,
  showDownloadInfo: Boolean,
  onClick: () -> Unit,
) {
  val selected = model.name == uiState.selectedModel.name
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
    modifier =
      Modifier.fillMaxWidth()
        .clickable { onClick() }
        .background(if (selected) MaterialTheme.colorScheme.surfaceContainer else Color.Transparent)
        .padding(horizontal = 16.dp, vertical = 8.dp),
  ) {
    Spacer(modifier = Modifier.width(24.dp))
    Column(modifier = Modifier.weight(1f)) {
      Text(
        model.displayName.ifEmpty { model.name },
        style = MaterialTheme.typography.bodyMedium,
      )
      if (showDownloadInfo) {
        Row(
          horizontalArrangement = Arrangement.spacedBy(4.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          StatusIcon(
            task = task,
            model = model,
            downloadStatus = uiState.modelDownloadStatus[model.name],
          )
          Text(
            if (model.localFileRelativeDirPathOverride.isEmpty())
              model.sizeInBytes.humanReadableSize()
            else "{ext_file_dir}/${model.localFileRelativeDirPathOverride}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = labelSmallNarrow.copy(lineHeight = 10.sp),
          )
        }
      } else if (model.info.isNotEmpty()) {
        Text(
          model.info,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          style = labelSmallNarrow.copy(lineHeight = 12.sp),
        )
      }
    }
    if (selected) {
      Icon(
        Icons.Filled.CheckCircle,
        modifier = Modifier.size(16.dp),
        contentDescription = stringResource(R.string.cd_selected_icon),
      )
    }
  }
}
