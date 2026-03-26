package com.google.ai.edge.gallery.customtasks.insurancecard

import com.google.ai.edge.gallery.customtasks.common.CustomTask
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
internal object InsuranceCardModule {
  @Provides
  @IntoSet
  fun provideTask(): CustomTask {
    return InsuranceCardTask()
  }
}
