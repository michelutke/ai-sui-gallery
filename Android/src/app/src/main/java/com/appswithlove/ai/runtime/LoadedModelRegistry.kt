package com.appswithlove.ai.runtime

import com.appswithlove.ai.data.Model
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-scoped holder for the most recently initialized chat-capable LLM model.
 *
 * Read from non-UI contexts (AppFunctions, background services) that cannot obtain a
 * `ModelManagerViewModel` instance. [ModelManagerViewModel] updates it on init/cleanup.
 *
 * Sentinel models (e.g. OCR-only with `instance = Unit`) are filtered out.
 */
@Singleton
class LoadedModelRegistry @Inject constructor() {

  @Volatile
  var currentChatModel: Model? = null
    private set

  fun onInitialized(model: Model) {
    val inst = model.instance
    if (inst != null && inst !== Unit) {
      currentChatModel = model
    }
  }

  fun onCleanedUp(model: Model) {
    if (currentChatModel === model) {
      currentChatModel = null
    }
  }
}
