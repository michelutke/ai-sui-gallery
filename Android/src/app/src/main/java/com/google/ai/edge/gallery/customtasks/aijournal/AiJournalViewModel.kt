package com.google.ai.edge.gallery.customtasks.aijournal

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.edge.gallery.customtasks.aijournal.data.ChatMessageEntity
import com.google.ai.edge.gallery.customtasks.aijournal.data.EntryWithEntities
import com.google.ai.edge.gallery.customtasks.aijournal.data.JournalDao
import com.google.ai.edge.gallery.customtasks.aijournal.data.JournalEntry
import com.google.ai.edge.gallery.customtasks.aijournal.data.JournalEntity
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.SAMPLE_RATE
import com.google.ai.edge.gallery.data.Task
import com.google.ai.edge.gallery.ui.llmchat.LlmChatModelHelper
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.gson.Gson
import com.google.gson.JsonObject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "AiJournalVM"

data class ChatMessage(
  val id: Long = System.nanoTime(),
  val text: String,
  val isUser: Boolean,
  val timestamp: Long = System.currentTimeMillis(),
  val inputType: String = "text", // "text", "voice_gemma", "voice_stt"
  val isLoading: Boolean = false,
  val audioDurationSec: Float? = null, // for voice messages
  val transcript: String? = null, // LLM-generated transcript for voice messages
)

data class AiJournalUiState(
  val messages: List<ChatMessage> = emptyList(),
  val isProcessing: Boolean = false,
  val historyEntries: List<EntryWithEntities> = emptyList(),
  val filterPerson: String? = null,
  val filterActivity: String? = null,
  val filterMood: String? = null,
  val filterLocation: String? = null,
  val filterQuery: String = "",
  val availablePeople: List<String> = emptyList(),
  val availableActivities: List<String> = emptyList(),
  val availableMoods: List<String> = emptyList(),
  val availableLocations: List<String> = emptyList(),
)

@HiltViewModel
class AiJournalViewModel @Inject constructor(
  private val journalDao: JournalDao,
) : ViewModel() {

  private val _uiState = MutableStateFlow(AiJournalUiState())
  val uiState = _uiState.asStateFlow()
  private val gson = Gson()

  /** Exposed for AudioRecorderPanel which needs a Task reference. */
  val task: Task get() = AiJournalTask().task

  init {
    loadHistory()
    loadFilterOptions()
    loadChatHistory()
  }

  private fun loadChatHistory() {
    viewModelScope.launch(Dispatchers.IO) {
      val saved = journalDao.getAllChatMessages()
      if (saved.isNotEmpty()) {
        val messages = saved.map { entity ->
          ChatMessage(
            id = entity.id,
            text = entity.text,
            isUser = entity.isUser,
            timestamp = entity.timestamp,
            inputType = entity.inputType,
            audioDurationSec = entity.audioDurationSec,
            transcript = entity.transcript,
          )
        }
        _uiState.update { it.copy(messages = messages) }
      }
    }
  }

  private fun persistChatMessage(message: ChatMessage) {
    viewModelScope.launch(Dispatchers.IO) {
      journalDao.insertChatMessage(
        ChatMessageEntity(
          id = message.id,
          text = message.text,
          isUser = message.isUser,
          timestamp = message.timestamp,
          inputType = message.inputType,
          audioDurationSec = message.audioDurationSec,
          transcript = message.transcript,
        )
      )
    }
  }

  fun sendMessage(text: String, model: Model, inputType: String = "text") {
    if (text.isBlank()) return

    val userMessage = ChatMessage(text = text, isUser = true, inputType = inputType)
    val loadingMessage = ChatMessage(text = "", isUser = false, isLoading = true)
    persistChatMessage(userMessage)

    _uiState.update { it.copy(
      messages = it.messages + userMessage + loadingMessage,
      isProcessing = true,
    ) }

    viewModelScope.launch(Dispatchers.Default) {
      try {
        val context = assembleContext(text)
        val fullPrompt = if (context.isNotBlank()) {
          "<<JOURNAL CONTEXT>>\n$context\n<</JOURNAL CONTEXT>>\n\nUser: $text"
        } else {
          text
        }

        val accumulated = StringBuilder()
        LlmChatModelHelper.runInference(
          model = model,
          input = fullPrompt,
          resultListener = { partialResult, done, _ ->
            accumulated.append(partialResult)
            if (done) {
              val response = accumulated.toString()
              processResponse(response, text, inputType)
            } else {
              _uiState.update { state ->
                val msgs = state.messages.toMutableList()
                val lastIdx = msgs.lastIndex
                if (lastIdx >= 0 && msgs[lastIdx].isLoading || (!msgs[lastIdx].isUser && msgs[lastIdx] == state.messages.last())) {
                  msgs[lastIdx] = ChatMessage(
                    text = accumulated.toString(),
                    isUser = false,
                    isLoading = false,
                  )
                }
                state.copy(messages = msgs)
              }
            }
          },
          cleanUpListener = {},
          onError = { error ->
            Log.e(TAG, "Inference error: $error")
            _uiState.update { state ->
              val msgs = state.messages.toMutableList()
              if (msgs.isNotEmpty() && !msgs.last().isUser) {
                msgs[msgs.lastIndex] = ChatMessage(
                  text = "Error: $error",
                  isUser = false,
                  isLoading = false,
                )
              }
              state.copy(messages = msgs, isProcessing = false)
            }
          },
        )
      } catch (e: Exception) {
        Log.e(TAG, "Send error", e)
        _uiState.update { state ->
          val msgs = state.messages.toMutableList()
          if (msgs.isNotEmpty() && !msgs.last().isUser) {
            msgs[msgs.lastIndex] = ChatMessage(
              text = "Error: ${e.message}",
              isUser = false,
              isLoading = false,
            )
          }
          state.copy(messages = msgs, isProcessing = false)
        }
      }
    }
  }

  fun sendAudioMessage(pcmBytes: ByteArray, model: Model) {
    if (pcmBytes.isEmpty()) return

    val durationSec = pcmBytes.size.toFloat() / (SAMPLE_RATE * 2) // 16-bit = 2 bytes/sample
    val userMessage = ChatMessage(
      text = "Voice message",
      isUser = true,
      inputType = "voice_gemma",
      audioDurationSec = durationSec,
    )
    val loadingMessage = ChatMessage(text = "", isUser = false, isLoading = true)
    val userMsgId = userMessage.id
    persistChatMessage(userMessage)

    _uiState.update { it.copy(
      messages = it.messages + userMessage + loadingMessage,
      isProcessing = true,
    ) }

    viewModelScope.launch(Dispatchers.Default) {
      try {
        val wavBytes = pcmToWav(pcmBytes, SAMPLE_RATE)
        val prompt = "Process this voice journal entry."
        val accumulated = StringBuilder()
        LlmChatModelHelper.runInference(
          model = model,
          input = prompt,
          audioClips = listOf(wavBytes),
          resultListener = { partialResult, done, _ ->
            accumulated.append(partialResult)
            if (done) {
              val response = accumulated.toString()
              // Update the user voice bubble with the transcript
              _uiState.update { state ->
                val msgs = state.messages.toMutableList()
                val userIdx = msgs.indexOfFirst { it.id == userMsgId }
                if (userIdx >= 0) {
                  msgs[userIdx] = msgs[userIdx].copy(transcript = response)
                }
                state.copy(messages = msgs)
              }
              // Persist transcript
              viewModelScope.launch(Dispatchers.IO) {
                journalDao.updateTranscript(userMsgId, response)
              }
              processResponse(response, response, "voice_gemma")
            } else {
              _uiState.update { state ->
                val msgs = state.messages.toMutableList()
                if (msgs.isNotEmpty() && !msgs.last().isUser) {
                  msgs[msgs.lastIndex] = ChatMessage(
                    text = accumulated.toString(),
                    isUser = false,
                    isLoading = false,
                  )
                }
                state.copy(messages = msgs)
              }
            }
          },
          cleanUpListener = {},
          onError = { error ->
            Log.e(TAG, "Audio inference error: $error")
            _uiState.update { state ->
              val msgs = state.messages.toMutableList()
              if (msgs.isNotEmpty() && !msgs.last().isUser) {
                msgs[msgs.lastIndex] = ChatMessage(
                  text = "Error: $error",
                  isUser = false,
                  isLoading = false,
                )
              }
              state.copy(messages = msgs, isProcessing = false)
            }
          },
        )
      } catch (e: Exception) {
        Log.e(TAG, "Audio send error", e)
        _uiState.update { state ->
          val msgs = state.messages.toMutableList()
          if (msgs.isNotEmpty() && !msgs.last().isUser) {
            msgs[msgs.lastIndex] = ChatMessage(
              text = "Error: ${e.message}",
              isUser = false,
              isLoading = false,
            )
          }
          state.copy(messages = msgs, isProcessing = false)
        }
      }
    }
  }

  private fun processResponse(response: String, userText: String, inputType: String) {
    val extracted = parseExtraction(response)
    val displayText = if (extracted != null) {
      // Remove JSON block from display, show only confirmation
      response.replace(Regex("\\{[^}]*\"summary\"[^}]*\\}"), "").trim()
        .ifBlank { buildConfirmation(extracted) }
    } else {
      response
    }

    val aiMessage = ChatMessage(text = displayText, isUser = false, isLoading = false)

    _uiState.update { state ->
      val msgs = state.messages.toMutableList()
      if (msgs.isNotEmpty() && !msgs.last().isUser) {
        msgs[msgs.lastIndex] = aiMessage
      }
      state.copy(messages = msgs, isProcessing = false)
    }

    persistChatMessage(aiMessage)

    // Persist if extraction found (= journal entry, not a query)
    if (extracted != null) {
      viewModelScope.launch(Dispatchers.IO) {
        persistEntry(userText, inputType, extracted)
        loadHistory()
        loadFilterOptions()
      }
    }
  }

  private fun buildConfirmation(data: JsonObject): String {
    val parts = mutableListOf<String>()
    data.get("summary")?.asString?.let { parts.add(it) }
    data.getAsJsonArray("people")?.let { arr ->
      if (arr.size() > 0) parts.add("People: ${arr.joinToString { it.asString }}")
    }
    data.get("mood")?.asString?.let { if (it.isNotBlank()) parts.add("Mood: $it") }
    data.getAsJsonArray("activities")?.let { arr ->
      if (arr.size() > 0) parts.add("Activities: ${arr.joinToString { it.asString }}")
    }
    data.getAsJsonArray("locations")?.let { arr ->
      if (arr.size() > 0) parts.add("Locations: ${arr.joinToString { it.asString }}")
    }
    return "Saved. " + parts.joinToString(", ")
  }

  private fun parseExtraction(response: String): JsonObject? {
    return try {
      // Find JSON block in response
      val jsonPattern = Regex("\\{[^{}]*\"summary\"[^{}]*\\}")
      val match = jsonPattern.find(response) ?: return null
      gson.fromJson(match.value, JsonObject::class.java)
    } catch (e: Exception) {
      Log.w(TAG, "Failed to parse extraction", e)
      null
    }
  }

  private suspend fun persistEntry(rawText: String, inputType: String, data: JsonObject) {
    val entryId = journalDao.insertEntry(
      JournalEntry(
        timestamp = System.currentTimeMillis(),
        rawText = rawText,
        inputType = inputType,
      )
    )

    val entities = mutableListOf<JournalEntity>()
    data.getAsJsonArray("people")?.forEach { el ->
      entities.add(JournalEntity(entryId = entryId, entityType = "PERSON", entityValue = el.asString))
    }
    data.getAsJsonArray("activities")?.forEach { el ->
      entities.add(JournalEntity(entryId = entryId, entityType = "ACTIVITY", entityValue = el.asString))
    }
    data.get("mood")?.asString?.takeIf { it.isNotBlank() }?.let {
      entities.add(JournalEntity(entryId = entryId, entityType = "MOOD", entityValue = it))
    }
    data.getAsJsonArray("locations")?.forEach { el ->
      entities.add(JournalEntity(entryId = entryId, entityType = "LOCATION", entityValue = el.asString))
    }
    data.getAsJsonArray("events")?.forEach { el ->
      entities.add(JournalEntity(entryId = entryId, entityType = "EVENT", entityValue = el.asString))
    }

    if (entities.isNotEmpty()) {
      journalDao.insertEntities(entities)
    }
  }

  private suspend fun assembleContext(query: String): String {
    val sb = StringBuilder()
    val now = System.currentTimeMillis()
    val dayMs = 86_400_000L
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    // Tier 1: Raw entries last 7 days
    val tier1Start = now - 7 * dayMs
    val recentEntries = journalDao.getEntriesBetween(tier1Start, now)
    if (recentEntries.isNotEmpty()) {
      sb.appendLine("=== Recent entries (last 7 days) ===")
      for (entry in recentEntries) {
        val entities = journalDao.getEntitiesForEntry(entry.id)
        sb.appendLine("[${dateFormat.format(Date(entry.timestamp))}] ${entry.rawText}")
        if (entities.isNotEmpty()) {
          sb.appendLine("  Tags: ${entities.joinToString { "${it.entityType}:${it.entityValue}" }}")
        }
      }
    }

    // Tier 2: Weekly summaries weeks 2-8
    val tier2Start = now - 8 * 7 * dayMs
    val weeklySummaries = journalDao.getWeeklySummariesBetween(tier2Start, tier1Start)
    if (weeklySummaries.isNotEmpty()) {
      sb.appendLine("\n=== Weekly summaries (weeks 2-8) ===")
      for (summary in weeklySummaries) {
        sb.appendLine("[${dateFormat.format(Date(summary.periodStart))}] ${summary.summaryText}")
      }
    }

    // Tier 3: Monthly summaries months 3-6
    val tier3Start = now - 6 * 30 * dayMs
    val monthlySummaries = journalDao.getMonthlySummariesFrom(tier3Start)
    if (monthlySummaries.isNotEmpty()) {
      sb.appendLine("\n=== Monthly summaries ===")
      for (summary in monthlySummaries) {
        sb.appendLine("[${dateFormat.format(Date(summary.periodStart))}] ${summary.summaryText}")
      }
    }

    // FTS5 search for entity matches
    try {
      val ftsResults = journalDao.searchEntries(query, limit = 5)
      if (ftsResults.isNotEmpty()) {
        sb.appendLine("\n=== Relevant past entries ===")
        for (entry in ftsResults) {
          sb.appendLine("[${dateFormat.format(Date(entry.timestamp))}] ${entry.rawText}")
        }
      }
    } catch (e: Exception) {
      Log.w(TAG, "FTS search failed", e)
    }

    return sb.toString()
  }

  fun loadHistory() {
    viewModelScope.launch(Dispatchers.IO) {
      val state = _uiState.value
      val entries = if (state.filterQuery.isNotBlank()) {
        try { journalDao.searchEntries(state.filterQuery) } catch (_: Exception) { emptyList() }
      } else if (state.filterPerson != null || state.filterActivity != null || state.filterMood != null || state.filterLocation != null) {
        journalDao.filterEntries(
          person = state.filterPerson,
          activity = state.filterActivity,
          mood = state.filterMood,
          location = state.filterLocation,
        )
      } else {
        journalDao.getRecentEntries(100)
      }

      val entryIds = entries.map { it.id }
      val allEntities = if (entryIds.isNotEmpty()) {
        journalDao.getEntitiesForEntries(entryIds)
      } else emptyList()
      val entitiesByEntry = allEntities.groupBy { it.entryId }

      val withEntities = entries.map { entry ->
        EntryWithEntities(entry = entry, entities = entitiesByEntry[entry.id] ?: emptyList())
      }

      _uiState.update { it.copy(historyEntries = withEntities) }
    }
  }

  private fun loadFilterOptions() {
    viewModelScope.launch(Dispatchers.IO) {
      val people = journalDao.getDistinctValues("PERSON")
      val activities = journalDao.getDistinctValues("ACTIVITY")
      val moods = journalDao.getDistinctValues("MOOD")
      val locations = journalDao.getDistinctValues("LOCATION")
      _uiState.update {
        it.copy(
          availablePeople = people,
          availableActivities = activities,
          availableMoods = moods,
          availableLocations = locations,
        )
      }
    }
  }

  fun setFilter(
    person: String? = _uiState.value.filterPerson,
    activity: String? = _uiState.value.filterActivity,
    mood: String? = _uiState.value.filterMood,
    location: String? = _uiState.value.filterLocation,
    query: String = _uiState.value.filterQuery,
  ) {
    _uiState.update {
      it.copy(
        filterPerson = person,
        filterActivity = activity,
        filterMood = mood,
        filterLocation = location,
        filterQuery = query,
      )
    }
    loadHistory()
  }

  fun clearFilters() {
    _uiState.update {
      it.copy(
        filterPerson = null,
        filterActivity = null,
        filterMood = null,
        filterLocation = null,
        filterQuery = "",
      )
    }
    loadHistory()
  }

  fun resetConversation(model: Model) {
    _uiState.update { it.copy(messages = emptyList()) }
    viewModelScope.launch(Dispatchers.IO) { journalDao.clearChatMessages() }
    try {
      LlmChatModelHelper.resetConversation(
        model = model,
        systemInstruction = Contents.of(listOf(Content.Text(JOURNAL_SYSTEM_PROMPT))),
      )
    } catch (e: Exception) {
      Log.w(TAG, "Failed to reset conversation", e)
    }
  }
}

/** Convert raw PCM 16-bit mono audio to WAV format with a 44-byte header. */
private fun pcmToWav(pcmData: ByteArray, sampleRate: Int): ByteArray {
  val channels = 1
  val bitsPerSample = 16
  val byteRate = sampleRate * channels * bitsPerSample / 8
  val blockAlign = channels * bitsPerSample / 8
  val dataSize = pcmData.size
  val fileSize = dataSize + 44

  val header = ByteArray(44)
  // RIFF header
  header[0] = 'R'.code.toByte(); header[1] = 'I'.code.toByte()
  header[2] = 'F'.code.toByte(); header[3] = 'F'.code.toByte()
  header[4] = (fileSize and 0xff).toByte()
  header[5] = (fileSize shr 8 and 0xff).toByte()
  header[6] = (fileSize shr 16 and 0xff).toByte()
  header[7] = (fileSize shr 24 and 0xff).toByte()
  // WAVE
  header[8] = 'W'.code.toByte(); header[9] = 'A'.code.toByte()
  header[10] = 'V'.code.toByte(); header[11] = 'E'.code.toByte()
  // fmt chunk
  header[12] = 'f'.code.toByte(); header[13] = 'm'.code.toByte()
  header[14] = 't'.code.toByte(); header[15] = ' '.code.toByte()
  header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0 // chunk size
  header[20] = 1; header[21] = 0 // PCM format
  header[22] = channels.toByte(); header[23] = 0
  header[24] = (sampleRate and 0xff).toByte()
  header[25] = (sampleRate shr 8 and 0xff).toByte()
  header[26] = (sampleRate shr 16 and 0xff).toByte()
  header[27] = (sampleRate shr 24 and 0xff).toByte()
  header[28] = (byteRate and 0xff).toByte()
  header[29] = (byteRate shr 8 and 0xff).toByte()
  header[30] = (byteRate shr 16 and 0xff).toByte()
  header[31] = (byteRate shr 24 and 0xff).toByte()
  header[32] = blockAlign.toByte(); header[33] = 0
  header[34] = bitsPerSample.toByte(); header[35] = 0
  // data chunk
  header[36] = 'd'.code.toByte(); header[37] = 'a'.code.toByte()
  header[38] = 't'.code.toByte(); header[39] = 'a'.code.toByte()
  header[40] = (dataSize and 0xff).toByte()
  header[41] = (dataSize shr 8 and 0xff).toByte()
  header[42] = (dataSize shr 16 and 0xff).toByte()
  header[43] = (dataSize shr 24 and 0xff).toByte()

  return header + pcmData
}
