package com.appswithlove.ai.customtasks.notes

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.appswithlove.ai.customtasks.notes.data.Note
import com.appswithlove.ai.customtasks.notes.data.NoteDao
import com.appswithlove.ai.data.Model
import com.appswithlove.ai.ui.llmchat.LlmChatModelHelper
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val TAG = "NotesVM"
private const val TITLE_PROMPT_PREFIX = "Note: "

data class DeletedNote(val note: Note, val undone: Boolean = false)

data class NotesUiState(
  val notes: List<Note> = emptyList(),
  val lastDeleted: DeletedNote? = null,
)

@HiltViewModel
class NotesViewModel @Inject constructor(
  private val noteDao: NoteDao,
) : ViewModel() {

  val notes = noteDao.observeAll().stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5_000),
    initialValue = emptyList(),
  )

  private val _lastDeleted = MutableStateFlow<DeletedNote?>(null)
  val lastDeleted = _lastDeleted.asStateFlow()

  private val inferenceMutex = Mutex()

  /**
   * Insert a note. If [model] is a real LLM (not sentinel) and [title] is blank, schedule async
   * title generation and update the row when done.
   */
  fun insertNote(title: String, content: String, source: String, model: Model?) {
    if (content.isBlank()) return
    val now = System.currentTimeMillis()
    viewModelScope.launch(Dispatchers.IO) {
      val id = noteDao.insert(
        Note(
          title = title.trim(),
          content = content.trim(),
          createdAt = now,
          updatedAt = now,
          source = source,
        )
      )
      if (title.isBlank() && model != null && model.name != NOTES_NO_AI_MODEL.name) {
        generateTitleAsync(id, content.trim(), model)
      }
    }
  }

  fun updateNote(note: Note) {
    viewModelScope.launch(Dispatchers.IO) {
      noteDao.update(note.copy(updatedAt = System.currentTimeMillis()))
    }
  }

  fun deleteNote(note: Note) {
    viewModelScope.launch(Dispatchers.IO) {
      noteDao.deleteById(note.id)
      _lastDeleted.value = DeletedNote(note)
    }
  }

  fun undoDelete() {
    val deleted = _lastDeleted.value?.note ?: return
    viewModelScope.launch(Dispatchers.IO) {
      noteDao.insert(deleted.copy(id = 0))
      _lastDeleted.value = null
    }
  }

  fun clearLastDeleted() {
    _lastDeleted.value = null
  }

  private fun generateTitleAsync(id: Long, content: String, model: Model) {
    viewModelScope.launch(Dispatchers.Default) {
      inferenceMutex.withLock {
        try {
          LlmChatModelHelper.resetConversation(
            model = model,
            systemInstruction = Contents.of(listOf(Content.Text(TITLE_SYSTEM_PROMPT))),
          )
          val accumulated = StringBuilder()
          LlmChatModelHelper.runInference(
            model = model,
            input = TITLE_PROMPT_PREFIX + content,
            resultListener = { partialResult, done, _ ->
              accumulated.append(partialResult)
              if (done) {
                val title = accumulated.toString()
                  .lineSequence()
                  .map { it.trim() }
                  .firstOrNull { it.isNotBlank() }
                  ?.take(80)
                  ?.trimEnd('.', ',', '!', '?', ':', ';')
                  ?: ""
                if (title.isNotBlank()) {
                  viewModelScope.launch(Dispatchers.IO) {
                    noteDao.updateTitle(id, title)
                  }
                }
              }
            },
            cleanUpListener = {},
            onError = { msg -> Log.w(TAG, "Title gen error: $msg") },
          )
        } catch (e: Exception) {
          Log.w(TAG, "Title gen failed", e)
        }
      }
    }
  }
}
