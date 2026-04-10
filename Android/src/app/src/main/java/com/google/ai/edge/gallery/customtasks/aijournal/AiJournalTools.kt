package com.google.ai.edge.gallery.customtasks.aijournal

import android.util.Log
import com.google.ai.edge.gallery.customtasks.aijournal.data.JournalDao
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

  /** Set by ViewModel before inference to capture tool calls. */
  @Volatile var onToolCalled: (call: String, result: String) -> Unit = { _, _ -> }

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
      val resultDesc = if (results.isEmpty()) {
        "No entries found"
      } else {
        results.take(5).joinToString("\n") { "${it["date"]} — ${it["tags"]}" }
      }
      Log.d(TAG, "searchJournal called: keyword=$keyword, results=${results.size}")
      onToolCalled(callDesc, resultDesc)

      if (results.isEmpty()) {
        mapOf("results" to "No entries found for '$keyword'")
      } else {
        mapOf("results" to results.take(5))
      }
    }
  }
}
