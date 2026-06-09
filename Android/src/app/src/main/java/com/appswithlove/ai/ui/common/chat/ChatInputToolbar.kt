/*
 * Copyright 2026 AppsWithLove
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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddComment
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.appswithlove.ai.R

/**
 * A Material 3 floating toolbar shown directly above the chat input field.
 *
 * Following the M3 toolbar pattern (https://m3.material.io/components/toolbars/overview): the
 * recurring tools (configuration + chat history) live in a rounded pill, while the primary "new
 * chat" action sits in its own rounded container to the right.
 */
@Composable
fun ChatInputToolbar(
  onConfigClicked: () -> Unit,
  onHistoryClicked: () -> Unit,
  onNewChatClicked: () -> Unit,
  modifier: Modifier = Modifier,
  showConfig: Boolean = true,
) {
  Row(
    modifier = modifier,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    // Tools pill: configuration + history.
    Surface(
      shape = RoundedCornerShape(50),
      color = MaterialTheme.colorScheme.surfaceContainerHigh,
      tonalElevation = 3.dp,
      shadowElevation = 3.dp,
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        if (showConfig) {
          IconButton(onClick = onConfigClicked) {
            Icon(
              imageVector = Icons.Rounded.Tune,
              contentDescription = stringResource(R.string.cd_model_settings_icon),
              tint = MaterialTheme.colorScheme.onSurface,
              modifier = Modifier.size(20.dp),
            )
          }
        }
        IconButton(onClick = onHistoryClicked) {
          Icon(
            imageVector = Icons.Rounded.History,
            contentDescription = stringResource(R.string.cd_chat_history),
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(20.dp),
          )
        }
      }
    }

    // Separate primary action: new chat.
    Surface(
      shape = RoundedCornerShape(16.dp),
      color = MaterialTheme.colorScheme.primaryContainer,
      tonalElevation = 3.dp,
      shadowElevation = 3.dp,
    ) {
      IconButton(onClick = onNewChatClicked) {
        Icon(
          imageVector = Icons.Rounded.AddComment,
          contentDescription = stringResource(R.string.new_chat),
          tint = MaterialTheme.colorScheme.onPrimaryContainer,
          modifier = Modifier.size(20.dp),
        )
      }
    }
  }
}
