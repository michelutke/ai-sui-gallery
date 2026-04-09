package com.google.ai.edge.gallery.customtasks.aijournal.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.ai.edge.gallery.customtasks.aijournal.data.JournalDatabase
import com.google.ai.edge.gallery.customtasks.aijournal.data.JournalSummary
import java.util.Calendar
import java.util.concurrent.TimeUnit

private const val TAG = "JournalSumWorker"

class JournalSummarizationWorker(
  context: Context,
  params: WorkerParameters,
) : CoroutineWorker(context, params) {

  private val journalDao = JournalDatabase.getInstance(context).journalDao()

  override suspend fun doWork(): Result {
    return try {
      generateDailySummary()
      generateWeeklySummaryIfSunday()
      generateMonthlySummaryIfFirstOfMonth()
      Result.success()
    } catch (e: Exception) {
      Log.e(TAG, "Summarization failed", e)
      Result.retry()
    }
  }

  private suspend fun generateDailySummary() {
    val cal = Calendar.getInstance().apply {
      set(Calendar.HOUR_OF_DAY, 0)
      set(Calendar.MINUTE, 0)
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
    }
    val dayStart = cal.timeInMillis
    val dayEnd = dayStart + 86_400_000L

    val existing = journalDao.getSummariesBetween("DAILY", dayStart, dayEnd)
    if (existing.isNotEmpty()) return

    val entries = journalDao.getEntriesBetween(dayStart, dayEnd)
    if (entries.isEmpty()) return

    val entryIds = entries.map { it.id }
    val entities = journalDao.getEntitiesForEntries(entryIds)
    val entityByEntry = entities.groupBy { it.entryId }

    val people = mutableSetOf<String>()
    val activities = mutableSetOf<String>()
    val moods = mutableSetOf<String>()
    val locations = mutableSetOf<String>()

    entries.forEach { entry ->
      val entryEntities = entityByEntry[entry.id] ?: emptyList()
      entryEntities.forEach { entity ->
        when (entity.entityType) {
          "PERSON" -> people.add(entity.entityValue)
          "ACTIVITY" -> activities.add(entity.entityValue)
          "MOOD" -> moods.add(entity.entityValue)
          "LOCATION" -> locations.add(entity.entityValue)
        }
      }
    }

    val sb = StringBuilder("${entries.size} entries.")
    if (people.isNotEmpty()) sb.append(" People: ${people.joinToString()}.")
    if (activities.isNotEmpty()) sb.append(" Activities: ${activities.joinToString()}.")
    if (moods.isNotEmpty()) sb.append(" Mood: ${moods.joinToString()}.")
    if (locations.isNotEmpty()) sb.append(" Locations: ${locations.joinToString()}.")

    journalDao.insertSummary(
      JournalSummary(
        periodType = "DAILY",
        periodStart = dayStart,
        periodEnd = dayEnd,
        summaryText = sb.toString(),
        tokenCount = sb.length / 4,
      )
    )
  }

  private suspend fun generateWeeklySummaryIfSunday() {
    val cal = Calendar.getInstance()
    if (cal.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY) return

    cal.apply {
      set(Calendar.HOUR_OF_DAY, 0)
      set(Calendar.MINUTE, 0)
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
    }
    val weekEnd = cal.timeInMillis + 86_400_000L
    val weekStart = weekEnd - 7 * 86_400_000L

    val existing = journalDao.getSummariesBetween("WEEKLY", weekStart, weekEnd)
    if (existing.isNotEmpty()) return

    val dailySummaries = journalDao.getSummariesBetween("DAILY", weekStart, weekEnd)
    if (dailySummaries.isEmpty()) return

    val text = dailySummaries.joinToString(" | ") { it.summaryText }

    journalDao.insertSummary(
      JournalSummary(
        periodType = "WEEKLY",
        periodStart = weekStart,
        periodEnd = weekEnd,
        summaryText = text,
        tokenCount = text.length / 4,
      )
    )
  }

  private suspend fun generateMonthlySummaryIfFirstOfMonth() {
    val cal = Calendar.getInstance()
    if (cal.get(Calendar.DAY_OF_MONTH) != 1) return

    cal.apply {
      set(Calendar.DAY_OF_MONTH, 1)
      set(Calendar.HOUR_OF_DAY, 0)
      set(Calendar.MINUTE, 0)
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
    }
    val monthStart = cal.timeInMillis
    cal.add(Calendar.MONTH, -1)
    val prevMonthStart = cal.timeInMillis

    val existing = journalDao.getSummariesBetween("MONTHLY", prevMonthStart, monthStart)
    if (existing.isNotEmpty()) return

    val weeklySummaries = journalDao.getWeeklySummariesBetween(prevMonthStart, monthStart)
    if (weeklySummaries.isEmpty()) return

    val text = weeklySummaries.joinToString(" | ") { it.summaryText }

    journalDao.insertSummary(
      JournalSummary(
        periodType = "MONTHLY",
        periodStart = prevMonthStart,
        periodEnd = monthStart,
        summaryText = text,
        tokenCount = text.length / 4,
      )
    )
  }

  companion object {
    private const val WORK_NAME = "journal_summarization"

    fun schedule(context: Context) {
      val request = PeriodicWorkRequestBuilder<JournalSummarizationWorker>(
        repeatInterval = 12,
        repeatIntervalTimeUnit = TimeUnit.HOURS,
      ).build()

      WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        WORK_NAME,
        ExistingPeriodicWorkPolicy.KEEP,
        request,
      )
    }
  }
}
