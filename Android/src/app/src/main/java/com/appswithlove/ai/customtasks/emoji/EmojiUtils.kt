package com.appswithlove.ai.customtasks.emoji

/** Unicode ranges covering common emoji (supplementary + dingbats + misc symbols). */
private val EMOJI_PATTERN =
  Regex(
    "[\\x{1F600}-\\x{1F64F}]" + // Emoticons
      "|[\\x{1F300}-\\x{1F5FF}]" + // Misc symbols & pictographs
      "|[\\x{1F680}-\\x{1F6FF}]" + // Transport & map symbols
      "|[\\x{1F1E0}-\\x{1F1FF}]" + // Flags
      "|[\\x{2702}-\\x{27B0}]" + // Dingbats
      "|[\\x{2600}-\\x{26FF}]" + // Misc symbols
      "|[\\x{FE00}-\\x{FE0F}]" + // Variation selectors
      "|[\\x{1F900}-\\x{1F9FF}]" + // Supplemental symbols
      "|[\\x{1FA00}-\\x{1FA6F}]" + // Chess symbols
      "|[\\x{1FA70}-\\x{1FAFF}]" + // Symbols extended-A
      "|[\\x{2764}]" // Heart
  )

/** Returns the first emoji found in the string, or null if none found. */
fun String.firstEmoji(): String? = EMOJI_PATTERN.find(this)?.value
