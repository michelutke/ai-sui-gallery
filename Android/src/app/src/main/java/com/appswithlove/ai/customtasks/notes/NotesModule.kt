package com.appswithlove.ai.customtasks.notes

import android.content.Context
import com.appswithlove.ai.customtasks.common.CustomTask
import com.appswithlove.ai.customtasks.notes.data.NoteDao
import com.appswithlove.ai.customtasks.notes.data.NotesDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object NotesModule {
  @Provides
  @IntoSet
  fun provideTask(): CustomTask {
    return NotesTask()
  }

  @Provides
  @Singleton
  fun provideNotesDatabase(@ApplicationContext context: Context): NotesDatabase {
    return NotesDatabase.getInstance(context)
  }

  @Provides
  @Singleton
  fun provideNoteDao(database: NotesDatabase): NoteDao {
    return database.noteDao()
  }
}
