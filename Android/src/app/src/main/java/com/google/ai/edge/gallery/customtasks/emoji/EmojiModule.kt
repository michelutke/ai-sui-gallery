package com.google.ai.edge.gallery.customtasks.emoji

import com.google.ai.edge.gallery.customtasks.common.CustomTask
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
internal object EmojiModule {
  @Provides
  @IntoSet
  fun provideTask(): CustomTask {
    return EmojiTask()
  }
}
