package com.google.ai.edge.gallery.ui.theme

import androidx.compose.runtime.mutableStateOf

object LocaleSettings {
  /** Empty string = system default, otherwise a BCP 47 language tag (e.g. "de", "fr"). */
  val languageTag = mutableStateOf("")
}
