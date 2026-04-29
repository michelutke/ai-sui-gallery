package com.appswithlove.ai.ui.navigation

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Tiny singleton used to pass a "navigate to task" signal from [com.appswithlove.ai.MainActivity]
 * (which reads the launch intent) to the Compose nav graph, which then performs the push.
 *
 * Separate from the AppFunctions runtime because those functions run in the service context and
 * never instantiate an Activity; the PendingIntent they return targets `MainActivity`, and the
 * extras it carries land here.
 */
object DeepLinkBus {
  const val EXTRA_OPEN_TASK_ID = "open_task_id"

  val pendingTaskId = MutableStateFlow<String?>(null)

  fun consume() {
    pendingTaskId.value = null
  }
}
