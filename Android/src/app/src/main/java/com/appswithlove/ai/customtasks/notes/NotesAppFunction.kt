package com.appswithlove.ai.customtasks.notes

import androidx.appfunctions.AppFunctionContext
import androidx.appfunctions.AppFunctionSerializable
import androidx.appfunctions.service.AppFunction
import com.appswithlove.ai.customtasks.notes.data.Note
import com.appswithlove.ai.customtasks.notes.data.NoteDao
import javax.inject.Inject

/** AppFunctions-facing DTO for a note. Decouples Room entity from agent schema. */
@AppFunctionSerializable
data class NoteDto(
  val id: Long,
  val title: String,
  val content: String,
  val createdAt: Long,
)

/**
 * Exposes the Notes feature to Gemini and other system agents via Android App Functions.
 *
 * Only reachable on Android 16+ (API 36). The `appfunctions-service` library automatically
 * registers the platform service; no manifest entry needed.
 */
class NotesAppFunction @Inject constructor(
  private val noteDao: NoteDao,
) {

  /**
   * Creates a new note and saves it on-device.
   *
   * Use for quick captures like todos, reminders, shopping lists, or ideas. The note appears
   * in the user's Notes list immediately. Prefer this for generic note-taking voice commands
   * such as "note X", "save X", "remember X", "notiere X".
   *
   * @param appFunctionContext The context in which the AppFunction is executed.
   * @param content The body text of the note (required).
   * @param title A short title for the note (optional — auto-generated if omitted).
   * @return The created note with its assigned id.
   */
  @AppFunction(isDescribedByKDoc = true)
  suspend fun createNote(
    appFunctionContext: AppFunctionContext,
    content: String,
    title: String? = null,
  ): NoteDto {
    val now = System.currentTimeMillis()
    val id = noteDao.insert(
      Note(
        title = (title ?: "").trim(),
        content = content.trim(),
        createdAt = now,
        updatedAt = now,
        source = "VOICE",
      )
    )
    return NoteDto(
      id = id,
      title = (title ?: "").trim(),
      content = content.trim(),
      createdAt = now,
    )
  }

  /**
   * Searches the user's notes for a keyword. Matches the title or body text.
   *
   * Use when the user asks what they noted about a topic, person, or event.
   *
   * @param appFunctionContext The context in which the AppFunction is executed.
   * @param query The search keyword.
   * @param limit Maximum number of results to return (default 10).
   * @return Matching notes, newest first.
   */
  @AppFunction(isDescribedByKDoc = true)
  suspend fun searchNotes(
    appFunctionContext: AppFunctionContext,
    query: String,
    limit: Int = 10,
  ): List<NoteDto> {
    return noteDao.search(query, limit).map { n ->
      NoteDto(
        id = n.id,
        title = n.title,
        content = n.content,
        createdAt = n.createdAt,
      )
    }
  }
}
