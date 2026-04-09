package com.google.ai.edge.gallery.customtasks.aijournal

import android.content.Context
import com.google.ai.edge.gallery.customtasks.aijournal.data.JournalDao
import com.google.ai.edge.gallery.customtasks.aijournal.data.JournalDatabase
import com.google.ai.edge.gallery.customtasks.common.CustomTask
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object AiJournalModule {
  @Provides
  @IntoSet
  fun provideTask(): CustomTask {
    return AiJournalTask()
  }

  @Provides
  @Singleton
  fun provideJournalDatabase(@ApplicationContext context: Context): JournalDatabase {
    return JournalDatabase.getInstance(context)
  }

  @Provides
  @Singleton
  fun provideJournalDao(database: JournalDatabase): JournalDao {
    return database.journalDao()
  }
}
