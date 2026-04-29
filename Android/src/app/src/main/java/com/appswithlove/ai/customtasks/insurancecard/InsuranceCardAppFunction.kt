package com.appswithlove.ai.customtasks.insurancecard

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.appfunctions.AppFunctionContext
import androidx.appfunctions.AppFunctionSerializable
import androidx.appfunctions.service.AppFunction
import com.appswithlove.ai.MainActivity
import com.appswithlove.ai.ui.navigation.DeepLinkBus
import javax.inject.Inject

/** AppFunctions-facing result — a launch intent plus a human-readable confirmation. */
@AppFunctionSerializable
data class ScannerLaunchResult(
  /** PendingIntent the agent fires to bring the scanner to the foreground. */
  val launchIntent: PendingIntent,
  /** Short confirmation the agent can speak back to the user. */
  val message: String,
)

/**
 * Exposes the Swiss insurance (KVG) card scanner to system agents via Android App Functions.
 *
 * The scanner itself is interactive — the user needs to point the camera at the card — so the
 * function returns a PendingIntent that launches the scanner screen in the foreground rather
 * than running any extraction logic itself.
 */
class InsuranceCardAppFunction @Inject constructor() {

  /**
   * Opens the insurance card scanner in the app so the user can point their camera at a Swiss
   * KVG / EHIC health insurance card and capture it for OCR.
   *
   * Use for voice commands about scanning, photographing, or reading an insurance card
   * ("scan my insurance card", "scanne meine Krankenkassenkarte", "lis ma carte d'assurance").
   *
   * @param appFunctionContext The context in which the AppFunction is executed.
   * @return A PendingIntent that brings the scanner to the foreground, plus a confirmation string.
   */
  @AppFunction(isDescribedByKDoc = true)
  suspend fun openInsuranceCardScanner(
    appFunctionContext: AppFunctionContext,
  ): ScannerLaunchResult {
    val ctx: Context = appFunctionContext.context
    val intent = Intent(ctx, MainActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
      putExtra(DeepLinkBus.EXTRA_OPEN_TASK_ID, "insurance_card_scan")
    }
    val pending = PendingIntent.getActivity(
      ctx,
      /* requestCode = */ 0,
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    return ScannerLaunchResult(
      launchIntent = pending,
      message = "Opening the insurance card scanner — point your camera at the card.",
    )
  }
}
