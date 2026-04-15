package com.appswithlove.ai.customtasks.aijournal

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.appswithlove.ai.customtasks.aijournal.data.ChatMessageEntity
import com.appswithlove.ai.customtasks.aijournal.data.EntryWithEntities
import com.appswithlove.ai.customtasks.aijournal.data.JournalDao
import com.appswithlove.ai.customtasks.common.CustomTask
import com.appswithlove.ai.data.Model
import com.appswithlove.ai.data.SAMPLE_RATE
import com.appswithlove.ai.data.Task
import com.appswithlove.ai.ui.llmchat.LlmChatModelHelper
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
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
  val audioDurationSec: Float? = null,
  val transcript: String? = null,
  val processLog: String? = null, // tool calls + thinking steps during inference
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
  private val journalTools: AiJournalTools,
  customTasks: Set<@JvmSuppressWildcards CustomTask>,
) : ViewModel() {

  private val _uiState = MutableStateFlow(AiJournalUiState())
  val uiState = _uiState.asStateFlow()
  private val gson = Gson()
  private val journalTask = customTasks.filterIsInstance<AiJournalTask>().first()

  val task: Task get() = journalTask.task

  init {
    loadHistory()
    loadFilterOptions()
    loadChatHistory()
    journalTools.onEntrySaved = {
      loadHistory()
      loadFilterOptions()
    }
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
            processLog = entity.processLog,
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
          processLog = message.processLog,
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
        val accumulated = StringBuilder()
        val thinkingAccum = StringBuilder()
        val toolCalls = java.util.concurrent.CopyOnWriteArrayList<Pair<String, String>>()

        journalTools.onToolCalled = { call, result ->
          toolCalls.add(call to result)
        }

        LlmChatModelHelper.runInference(
          model = model,
          input = text,
          resultListener = { partialResult, done, partialThinking ->
            accumulated.append(partialResult)
            if (partialThinking != null) thinkingAccum.append(partialThinking)
            if (done) {
              val response = accumulated.toString()
              val log = buildProcessLog(
                userInput = text,
                thinking = thinkingAccum.toString(),
                toolCalls = toolCalls,
                response = response,
              )
              journalTools.onToolCalled = { _, _ -> }
              processResponse(response, text, inputType, log)
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

    // Pass 1: reset without tools for audio transcription, then route transcript through sendMessage
    viewModelScope.launch(Dispatchers.Default) {
      try {
        // Temporarily switch to audio-only mode (tools break audio processing)
        LlmChatModelHelper.resetConversation(
          model = model,
          supportAudio = true,
        )

        val wavBytes = pcmToWav(pcmBytes, SAMPLE_RATE)
        val accumulated = StringBuilder()
        LlmChatModelHelper.runInference(
          model = model,
          input = "Transcribe this voice message exactly. Output only the spoken words.",
          audioClips = listOf(wavBytes),
          resultListener = { partialResult, done, _ ->
            accumulated.append(partialResult)
            if (done) {
              val transcript = accumulated.toString().trim()
              // Update voice bubble with transcript
              _uiState.update { state ->
                val msgs = state.messages.toMutableList()
                val userIdx = msgs.indexOfFirst { it.id == userMsgId }
                if (userIdx >= 0) {
                  msgs[userIdx] = msgs[userIdx].copy(transcript = transcript)
                }
                state.copy(messages = msgs)
              }
              viewModelScope.launch(Dispatchers.IO) {
                journalDao.updateTranscript(userMsgId, transcript)
              }
              // Restore tools + send on a new coroutine to avoid deadlocking the engine
              viewModelScope.launch(Dispatchers.Default) {
                LlmChatModelHelper.resetConversation(
                  model = model,
                  supportAudio = true,
                  systemInstruction = Contents.of(listOf(Content.Text(JOURNAL_SYSTEM_PROMPT))),
                  tools = journalTask.tools,
                )
                _uiState.update { state ->
                  val msgs = state.messages.toMutableList()
                  if (msgs.isNotEmpty() && !msgs.last().isUser) {
                    msgs.removeAt(msgs.lastIndex)
                  }
                  state.copy(messages = msgs, isProcessing = false)
                }
                sendMessage(transcript, model, "voice_gemma")
              }
            } else {
              _uiState.update { state ->
                val msgs = state.messages.toMutableList()
                if (msgs.isNotEmpty() && !msgs.last().isUser) {
                  msgs[msgs.lastIndex] = ChatMessage(
                    text = "Transcribing...",
                    isUser = false,
                    isLoading = true,
                  )
                }
                state.copy(messages = msgs)
              }
            }
          },
          cleanUpListener = {},
          onError = { error ->
            Log.e(TAG, "Audio transcription error: $error")
            // Restore tools even on error
            LlmChatModelHelper.resetConversation(
              model = model,
              supportAudio = true,
              systemInstruction = Contents.of(listOf(Content.Text(JOURNAL_SYSTEM_PROMPT))),
              tools = journalTask.tools,
            )
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

  private fun processResponse(
    response: String,
    userText: String,
    inputType: String,
    processLog: String? = null,
  ) {
    val aiMessage = ChatMessage(
      text = response,
      isUser = false,
      isLoading = false,
      processLog = processLog,
    )

    _uiState.update { state ->
      val msgs = state.messages.toMutableList()
      if (msgs.isNotEmpty() && !msgs.last().isUser) {
        msgs[msgs.lastIndex] = aiMessage
      }
      state.copy(messages = msgs, isProcessing = false)
    }

    persistChatMessage(aiMessage)
  }

  private fun buildProcessLog(
    userInput: String,
    thinking: String,
    toolCalls: List<Pair<String, String>>,
    response: String,
  ): String {
    val sections = mutableListOf<Map<String, String>>()
    sections.add(mapOf("type" to "system", "content" to JOURNAL_SYSTEM_PROMPT.trim()))
    sections.add(mapOf("type" to "input", "content" to userInput))
    for ((call, result) in toolCalls) {
      sections.add(mapOf("type" to "tool_call", "content" to call))
      sections.add(mapOf("type" to "tool_result", "content" to result))
    }
    if (thinking.isNotBlank()) {
      sections.add(mapOf("type" to "thinking", "content" to thinking))
    }
    sections.add(mapOf("type" to "response", "content" to response))
    return gson.toJson(sections)
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
        tools = journalTask.tools,
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
