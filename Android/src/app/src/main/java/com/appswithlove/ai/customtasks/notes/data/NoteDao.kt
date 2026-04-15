package com.appswithlove.ai.customtasks.notes.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

  @Insert
  suspend fun insert(note: Note): Long

  @Update
  suspend fun update(note: Note)

  @Query("DELETE FROM notes WHERE id = :id")
  suspend fun deleteById(id: Long)

  @Query("SELECT * FROM notes WHERE id = :id")
  suspend fun getById(id: Long): Note?

  @Query("SELECT * FROM notes ORDER BY updatedAt DESC")
  fun observeAll(): Flow<List<Note>>

  @Query(
    """
    SELECT * FROM notes
    WHERE title LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%'
    ORDER BY updatedAt DESC
    LIMIT :limit
    """
  )
  suspend fun search(query: String, limit: Int = 20): List<Note>

  @Query("UPDATE notes SET title = :title WHERE id = :id")
  suspend fun updateTitle(id: Long, title: String)
}
