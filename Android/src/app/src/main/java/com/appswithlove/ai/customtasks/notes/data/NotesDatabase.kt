package com.appswithlove.ai.customtasks.notes.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
  entities = [Note::class],
  version = 1,
  exportSchema = false,
)
abstract class NotesDatabase : RoomDatabase() {
  abstract fun noteDao(): NoteDao

  companion object {
    @Volatile
    private var INSTANCE: NotesDatabase? = null

    fun getInstance(context: Context): NotesDatabase {
      return INSTANCE ?: synchronized(this) {
        INSTANCE ?: Room.databaseBuilder(
          context.applicationContext,
          NotesDatabase::class.java,
          "notes.db",
        ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
      }
    }
  }
}
