package com.appswithlove.ai.customtasks.insurancecard

import com.appswithlove.ai.customtasks.common.CustomTask
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
