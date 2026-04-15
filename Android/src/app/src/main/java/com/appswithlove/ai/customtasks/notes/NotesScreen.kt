package com.appswithlove.ai.customtasks.notes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.appswithlove.ai.R
import com.appswithlove.ai.customtasks.notes.data.Note
import com.appswithlove.ai.ui.modelmanager.ModelManagerViewModel
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(
  modelManagerViewModel: ModelManagerViewModel,
  bottomPadding: Dp,
  viewModel: NotesViewModel = hiltViewModel(),
) {
  val modelManagerUiState by modelManagerViewModel.uiState.collectAsState()
  val model = modelManagerUiState.selectedModel
  val notes by viewModel.notes.collectAsState()
  val lastDeleted by viewModel.lastDeleted.collectAsState()

  var showEditor by remember { mutableStateOf(false) }
  var editingNote by remember { mutableStateOf<Note?>(null) }

  val snackbarHostState = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()
  val undoLabel = stringResource(R.string.notes_snackbar_undo)
  val deletedLabel = stringResource(R.string.notes_snackbar_deleted)

  LaunchedEffect(lastDeleted) {
    val deleted = lastDeleted ?: return@LaunchedEffect
    scope.launch {
      val result = snackbarHostState.showSnackbar(
        message = deletedLabel,
        actionLabel = undoLabel,
        withDismissAction = true,
      )
      if (result == SnackbarResult.ActionPerformed) {
        viewModel.undoDelete()
      } else {
        viewModel.clearLastDeleted()
      }
    }
  }

  Scaffold(
    snackbarHost = { SnackbarHost(snackbarHostState) { Snackbar(it) } },
    floatingActionButton = {
      FloatingActionButton(
        onClick = {
          editingNote = null
          showEditor = true
        },
      ) {
        Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.notes_add))
      }
    },
    containerColor = Color.Transparent,
  ) { inner ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(inner)
        .padding(bottom = bottomPadding),
    ) {
      if (notes.isEmpty()) {
        EmptyState()
      } else {
        LazyColumn(
          modifier = Modifier.fillMaxSize(),
          contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 16.dp,
            vertical = 12.dp,
          ),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          items(notes, key = { it.id }) { note ->
            SwipeableNoteRow(
              note = note,
              onTap = {
                editingNote = note
                showEditor = true
              },
              onDelete = { viewModel.deleteNote(note) },
            )
          }
        }
      }
    }
  }

  if (showEditor) {
    NoteEditorDialog(
      initial = editingNote,
      onDismiss = { showEditor = false },
      onSave = { title, content ->
        val current = editingNote
        if (current == null) {
          viewModel.insertNote(
            title = title,
            content = content,
            source = "MANUAL",
            model = model,
          )
        } else {
          viewModel.updateNote(current.copy(title = title.trim(), content = content.trim()))
        }
        showEditor = false
      },
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableNoteRow(
  note: Note,
  onTap: () -> Unit,
  onDelete: () -> Unit,
) {
  val state = rememberSwipeToDismissBoxState(
    confirmValueChange = { value ->
      if (value == SwipeToDismissBoxValue.EndToStart || value == SwipeToDismissBoxValue.StartToEnd) {
        onDelete()
        true
      } else false
    },
  )

  SwipeToDismissBox(
    state = state,
    backgroundContent = {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(horizontal = 24.dp),
        contentAlignment = Alignment.CenterEnd,
      ) {
        Icon(
          Icons.Outlined.Delete,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.error,
        )
      }
    },
  ) {
    NoteCard(note = note, onTap = onTap)
  }
}

@Composable
private fun NoteCard(note: Note, onTap: () -> Unit) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onTap),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceVariant,
    ),
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      if (note.title.isNotBlank()) {
        Text(
          text = note.title,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
          maxLines = 1,
        )
      }
      Text(
        text = note.content,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 3,
        modifier = Modifier.padding(top = if (note.title.isNotBlank()) 4.dp else 0.dp),
      )
      Text(
        text = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
          .format(Date(note.updatedAt)),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
      )
    }
  }
}

@Composable
private fun EmptyState() {
  Column(
    modifier = Modifier.fillMaxSize().padding(32.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Icon(
      Icons.AutoMirrored.Outlined.StickyNote2,
      contentDescription = null,
      modifier = Modifier.size(64.dp),
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
      text = stringResource(R.string.notes_empty_title),
      style = MaterialTheme.typography.titleMedium,
      modifier = Modifier.padding(top = 16.dp),
    )
    Text(
      text = stringResource(R.string.notes_empty_hint),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(top = 4.dp),
    )
  }
}

@Composable
private fun NoteEditorDialog(
  initial: Note?,
  onDismiss: () -> Unit,
  onSave: (title: String, content: String) -> Unit,
) {
  var title by remember { mutableStateOf(initial?.title ?: "") }
  var content by remember { mutableStateOf(initial?.content ?: "") }

  androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
    Card {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(20.dp),
      ) {
        Text(
          text = stringResource(
            if (initial == null) R.string.notes_editor_new else R.string.notes_editor_edit,
          ),
          style = MaterialTheme.typography.titleLarge,
        )
        OutlinedTextField(
          value = title,
          onValueChange = { title = it },
          label = { Text(stringResource(R.string.notes_editor_title_label)) },
          singleLine = true,
          modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        )
        OutlinedTextField(
          value = content,
          onValueChange = { content = it },
          label = { Text(stringResource(R.string.notes_editor_content_label)) },
          minLines = 4,
          modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .height(200.dp),
        )
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
          horizontalArrangement = Arrangement.End,
        ) {
          TextButton(onClick = onDismiss) {
            Text(stringResource(R.string.notes_editor_cancel))
          }
          Button(
            onClick = { onSave(title, content) },
            enabled = content.isNotBlank(),
            modifier = Modifier.padding(start = 8.dp),
          ) {
            Text(stringResource(R.string.notes_editor_save))
          }
        }
      }
    }
  }
}
