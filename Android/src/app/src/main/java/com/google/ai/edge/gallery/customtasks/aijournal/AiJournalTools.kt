package com.google.ai.edge.gallery.customtasks.aijournal

import android.util.Log
import com.google.ai.edge.gallery.customtasks.aijournal.data.JournalDao
import com.google.ai.edge.gallery.customtasks.aijournal.data.JournalEntry
import com.google.ai.edge.gallery.customtasks.aijournal.data.JournalEntity
import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam
import com.google.ai.edge.litertlm.ToolSet
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

private const val TAG = "AiJournalTools"

class AiJournalTools(
  private val journalDao: JournalDao,
) : ToolSet {

  @Volatile var onToolCalled: (call: String, result: String) -> Unit = { _, _ -> }

  /** Called after a journal entry is saved so ViewModel can refresh history. */
  @Volatile var onEntrySaved: () -> Unit = {}

  @Tool(description = "Search the user's journal for past entries by keyword. Use when the user asks about past events, people, places, activities, or moods.")
  fun searchJournal(
    @ToolParam(description = "A single keyword to search for, e.g. a person name, place, activity, or topic") keyword: String,
  ): Map<String, Any> {
    return runBlocking(Dispatchers.IO) {
      val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
      val ids = mutableSetOf<Long>()
      val results = mutableListOf<Map<String, String>>()

      for (entry in journalDao.searchEntriesByKeyword(keyword, limit = 5)) {
        if (ids.add(entry.id)) {
          val entities = journalDao.getEntitiesForEntry(entry.id)
          results.add(mapOf(
            "date" to dateFormat.format(Date(entry.timestamp)),
            "text" to entry.rawText.take(120),
            "tags" to entities.joinToString(", ") { it.entityValue },
          ))
        }
      }
      for (entry in journalDao.searchEntitiesByKeyword(keyword, limit = 5)) {
        if (ids.add(entry.id)) {
          val entities = journalDao.getEntitiesForEntry(entry.id)
          results.add(mapOf(
            "date" to dateFormat.format(Date(entry.timestamp)),
            "text" to entry.rawText.take(120),
            "tags" to entities.joinToString(", ") { it.entityValue },
          ))
        }
      }

      val callDesc = "searchJournal(\"$keyword\")"
      val resultDesc = if (results.isEmpty()) "No entries found"
        else results.take(5).joinToString("\n") { "${it["date"]} — ${it["tags"]}" }
      Log.d(TAG, "searchJournal: keyword=$keyword, results=${results.size}")
      onToolCalled(callDesc, resultDesc)

      if (results.isEmpty()) mapOf("results" to "No entries found for '$keyword'")
      else mapOf("results" to results.take(5))
    }
  }

  @Tool(description = "Save a journal entry when the user shares experiences, stories, reflections, or events from their life. Extract structured data from what they said. ALL parameters are required — pass empty string when not mentioned by user.")
  fun saveJournalEntry(
    @ToolParam(description = "Brief one-line summary of the entry") summary: String,
    @ToolParam(description = "Comma-separated people names, or empty string if none mentioned") people: String,
    @ToolParam(description = "Comma-separated activities, or empty string if none mentioned") activities: String,
    @ToolParam(description = "User mood in one word, or empty string if unclear") mood: String,
    @ToolParam(description = "Comma-separated locations, or empty string if none mentioned") locations: String,
    @ToolParam(description = "Comma-separated notable events, or empty string if none") events: String,
  ): Map<String, String> {
    return runBlocking(Dispatchers.IO) {
      val entryId = journalDao.insertEntry(
        JournalEntry(
          timestamp = System.currentTimeMillis(),
          rawText = summary,
          inputType = "text",
        )
      )

      val entities = mutableListOf<JournalEntity>()
      fun addEntities(type: String, csv: String) {
        for (v in csv.split(",").map { it.trim() }.filter { it.isNotBlank() }) {
          entities.add(JournalEntity(entryId = entryId, entityType = type, entityValue = v))
        }
      }
      addEntities("PERSON", people)
      addEntities("ACTIVITY", activities)
      if (mood.isNotBlank()) entities.add(JournalEntity(entryId = entryId, entityType = "MOOD", entityValue = mood.trim()))
      addEntities("LOCATION", locations)
      addEntities("EVENT", events)
      if (entities.isNotEmpty()) journalDao.insertEntities(entities)

      val tagSummary = entities.joinToString(", ") { "${it.entityType}: ${it.entityValue}" }
      val callDesc = "saveJournalEntry(\"${summary.take(60)}\")"
      val resultDesc = if (entities.isEmpty()) "Saved (no tags)" else "Saved — $tagSummary"
      Log.d(TAG, "saveJournalEntry: summary=$summary, entities=${entities.size}")
      onToolCalled(callDesc, resultDesc)
      onEntrySaved()

      mapOf("result" to "success", "summary" to summary, "entities" to "${entities.size}")
    }
  }
}
