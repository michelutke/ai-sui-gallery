package com.appswithlove.ai.customtasks.emoji

import com.appswithlove.ai.customtasks.common.CustomTask
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
