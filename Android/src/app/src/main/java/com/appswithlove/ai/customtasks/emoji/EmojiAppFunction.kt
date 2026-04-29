package com.appswithlove.ai.customtasks.emoji

import android.util.Log
import androidx.appfunctions.AppFunctionContext
import androidx.appfunctions.AppFunctionSerializable
import androidx.appfunctions.service.AppFunction
import com.appswithlove.ai.runtime.LoadedModelRegistry
import com.appswithlove.ai.ui.llmchat.LlmChatModelHelper
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "EmojiAppFn"
private const val LLM_TIMEOUT_MS = 8_000L

/** AppFunctions-facing result for a generated emoji. */
@AppFunctionSerializable
data class EmojiResult(
  /** The chosen emoji character(s). */
  val emoji: String,
  /** The word/phrase from the input that matched, or empty string if none. */
  val matched: String,
  /** Source of the result: "map", "llm", or "fallback". */
  val source: String,
)

/**
 * Exposes an emoji generator to system agents (e.g. Gemini) via Android App Functions.
 *
 * Fast path: keyword lookup over a curated table. Fallback path: if an on-device LLM chat
 * model is currently loaded, ask it for a single emoji. Final fallback: a sparkle. Runs only
 * on API 36+; the library is inert below.
 */
class EmojiAppFunction @Inject constructor(
  private val loadedModelRegistry: LoadedModelRegistry,
) {

  /**
   * Returns a single emoji character that matches a short description.
   *
   * Use for creative prompts like "an emoji for a rainy Monday", "emoji for celebration",
   * or anytime the user wants an emoji picked from a mood, weather, object, or activity.
   *
   * @param appFunctionContext The context in which the AppFunction is executed.
   * @param description Free-form text describing the desired emoji.
   * @return The chosen emoji plus metadata about how it was produced.
   */
  @AppFunction(isDescribedByKDoc = true)
  suspend fun generateEmoji(
    appFunctionContext: AppFunctionContext,
    description: String,
  ): EmojiResult {
    val normalized = description.lowercase().trim()

    // Fast path — keyword lookup.
    for ((keyword, emoji) in EMOJI_MAP) {
      if (normalized.contains(keyword)) {
        return EmojiResult(emoji = emoji, matched = keyword, source = "map")
      }
    }

    // LLM fallback — use whichever chat model is already loaded.
    val model = loadedModelRegistry.currentChatModel
    if (model != null) {
      val emoji = runCatching { askLlm(model, description) }.getOrNull()
      if (!emoji.isNullOrBlank()) {
        return EmojiResult(emoji = emoji, matched = description.take(40), source = "llm")
      }
    }

    return EmojiResult(emoji = "✨", matched = "", source = "fallback")
  }

  private suspend fun askLlm(
    model: com.appswithlove.ai.data.Model,
    description: String,
  ): String? {
    // Reset conversation with the emoji system prompt. This may disrupt an ongoing chat in
    // another task, but AppFunctions are voice-driven and unlikely to collide with an
    // active chat session.
    LlmChatModelHelper.resetConversation(
      model = model,
      systemInstruction = Contents.of(listOf(Content.Text(EMOJI_SYSTEM_PROMPT))),
    )

    return withTimeoutOrNull(LLM_TIMEOUT_MS) {
      suspendCancellableCoroutine { cont ->
        val buffer = StringBuilder()
        try {
          LlmChatModelHelper.runInference(
            model = model,
            input = "Text: $description\nEmoji:",
            resultListener = { partial, done, _ ->
              buffer.append(partial)
              if (done) {
                val emoji = buffer.toString().firstEmoji()
                if (cont.isActive) cont.resume(emoji)
              }
            },
            cleanUpListener = {},
            onError = { msg ->
              Log.w(TAG, "LLM emoji fallback failed: $msg")
              if (cont.isActive) cont.resume(null)
            },
          )
        } catch (e: Exception) {
          Log.w(TAG, "LLM emoji fallback threw", e)
          if (cont.isActive) cont.resume(null)
        }
      }
    }
  }
}

private const val EMOJI_SYSTEM_PROMPT =
  "You are an emoji assistant. When given a text after 'Text:', respond with ONLY a single emoji on the 'Emoji:' line."

private val EMOJI_MAP: List<Pair<String, String>> = listOf(
  // Moods — order matters, longer phrases first.
  "angry" to "😡", "furious" to "🤬", "sad" to "😢", "cry" to "😭",
  "happy" to "😊", "joy" to "😄", "laugh" to "😂", "love" to "❤️",
  "heart" to "❤️", "tired" to "😴", "sleep" to "💤", "bored" to "🥱",
  "sick" to "🤒", "scared" to "😱", "shock" to "😲", "cool" to "😎",
  "proud" to "🏆", "celebrate" to "🎉", "party" to "🥳", "smile" to "🙂",
  "wink" to "😉", "shy" to "🙈", "confused" to "😕", "think" to "🤔",
  "hug" to "🤗", "kiss" to "😘", "sweat" to "😅", "yay" to "🎉",
  // Weather / time.
  "rain" to "🌧️", "storm" to "⛈️", "thunder" to "⛈️", "snow" to "❄️",
  "sun" to "☀️", "sunny" to "☀️", "cloud" to "☁️", "fog" to "🌫️",
  "wind" to "💨", "hot" to "🔥", "cold" to "🥶", "freeze" to "🥶",
  "night" to "🌙", "star" to "⭐", "morning" to "🌅", "rainbow" to "🌈",
  "monday" to "😫", "friday" to "🎉", "weekend" to "🍹",
  // Food / drink.
  "coffee" to "☕", "tea" to "🍵", "beer" to "🍺", "wine" to "🍷",
  "water" to "💧", "pizza" to "🍕", "burger" to "🍔", "cake" to "🎂",
  "cookie" to "🍪", "chocolate" to "🍫", "apple" to "🍎", "banana" to "🍌",
  "bread" to "🥖", "cheese" to "🧀", "salad" to "🥗", "fish" to "🐟",
  "meat" to "🍖", "food" to "🍽️", "eat" to "🍽️", "hungry" to "🍴",
  // Activities.
  "work" to "💼", "meeting" to "📅", "call" to "📞", "email" to "📧",
  "write" to "✍️", "read" to "📖", "book" to "📚", "code" to "💻",
  "computer" to "💻", "game" to "🎮", "run" to "🏃", "walk" to "🚶",
  "bike" to "🚴", "swim" to "🏊", "travel" to "✈️", "flight" to "✈️",
  "train" to "🚆", "car" to "🚗", "music" to "🎵", "sing" to "🎤",
  "dance" to "💃", "sport" to "⚽", "football" to "⚽", "tennis" to "🎾",
  "ski" to "🎿", "hike" to "🥾", "camp" to "⛺",
  // Places / nature.
  "home" to "🏠", "house" to "🏠", "office" to "🏢", "school" to "🏫",
  "beach" to "🏖️", "mountain" to "⛰️", "alps" to "🏔️", "forest" to "🌲",
  "tree" to "🌳", "flower" to "🌸", "plant" to "🌱", "garden" to "🌷",
  "city" to "🏙️", "world" to "🌍",
  // Animals.
  "dog" to "🐶", "cat" to "🐱", "bird" to "🐦", "horse" to "🐴",
  "cow" to "🐮", "chicken" to "🐔", "bear" to "🐻", "fox" to "🦊",
  "rabbit" to "🐰",
  // Generic objects.
  "money" to "💰", "gift" to "🎁", "idea" to "💡", "warning" to "⚠️",
  "question" to "❓", "check" to "✅", "yes" to "✅", "no" to "❌",
  "fire" to "🔥", "rocket" to "🚀", "time" to "⏰", "clock" to "⏰",
  "camera" to "📷", "phone" to "📱",
)
