package com.google.ai.edge.gallery.customtasks.aijournal.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class DayCard(
  val dayTimestamp: Long,
  val entryCount: Int,
)

data class EntryWithEntities(
  val entry: JournalEntry,
  val entities: List<JournalEntity>,
)

@Dao
interface JournalDao {

  // -- Entries --

  @Insert
  suspend fun insertEntry(entry: JournalEntry): Long

  @Query("SELECT * FROM journal_entries WHERE id = :id")
  suspend fun getEntry(id: Long): JournalEntry?

  @Query("SELECT * FROM journal_entries WHERE timestamp >= :from AND timestamp < :to ORDER BY timestamp DESC")
  suspend fun getEntriesBetween(from: Long, to: Long): List<JournalEntry>

  @Query("SELECT * FROM journal_entries ORDER BY timestamp DESC")
  fun getAllEntriesFlow(): Flow<List<JournalEntry>>

  @Query("SELECT * FROM journal_entries ORDER BY timestamp DESC LIMIT :limit")
  suspend fun getRecentEntries(limit: Int): List<JournalEntry>

  // -- Entities --

  @Insert
  suspend fun insertEntities(entities: List<JournalEntity>)

  @Query("SELECT * FROM journal_entities WHERE entryId = :entryId")
  suspend fun getEntitiesForEntry(entryId: Long): List<JournalEntity>

  @Query("SELECT * FROM journal_entities WHERE entryId IN (:entryIds)")
  suspend fun getEntitiesForEntries(entryIds: List<Long>): List<JournalEntity>

  @Query("SELECT DISTINCT entityValue FROM journal_entities WHERE entityType = :type ORDER BY entityValue")
  suspend fun getDistinctValues(type: String): List<String>

  @Query(
    """
    SELECT DISTINCT je.* FROM journal_entries je
    INNER JOIN journal_entities ent ON ent.entryId = je.id
    WHERE ent.entityType = :type AND ent.entityValue LIKE '%' || :value || '%'
    ORDER BY je.timestamp DESC
    """
  )
  suspend fun getEntriesByEntity(type: String, value: String): List<JournalEntry>

  // -- Summaries --

  @Insert
  suspend fun insertSummary(summary: JournalSummary): Long

  @Query("SELECT * FROM journal_summaries WHERE periodType = :type ORDER BY periodStart DESC")
  suspend fun getSummariesByType(type: String): List<JournalSummary>

  @Query("SELECT * FROM journal_summaries WHERE periodType = :type AND periodStart >= :from AND periodStart < :to")
  suspend fun getSummariesBetween(type: String, from: Long, to: Long): List<JournalSummary>

  @Query("SELECT * FROM journal_summaries WHERE periodType = 'WEEKLY' AND periodStart >= :from AND periodEnd <= :to ORDER BY periodStart DESC")
  suspend fun getWeeklySummariesBetween(from: Long, to: Long): List<JournalSummary>

  @Query("SELECT * FROM journal_summaries WHERE periodType = 'MONTHLY' AND periodStart >= :from ORDER BY periodStart DESC")
  suspend fun getMonthlySummariesFrom(from: Long): List<JournalSummary>

  // -- FTS --

  @Query("SELECT je.* FROM journal_entries je JOIN journal_entries_fts fts ON je.rowid = fts.rowid WHERE journal_entries_fts MATCH :query ORDER BY je.timestamp DESC LIMIT :limit")
  suspend fun searchEntries(query: String, limit: Int = 20): List<JournalEntry>

  // -- Entity keyword search (across all entity types) --

  @Query(
    """
    SELECT DISTINCT je.* FROM journal_entries je
    INNER JOIN journal_entities ent ON ent.entryId = je.id
    WHERE ent.entityValue LIKE '%' || :keyword || '%'
    ORDER BY je.timestamp DESC
    LIMIT :limit
    """
  )
  suspend fun searchEntitiesByKeyword(keyword: String, limit: Int = 5): List<JournalEntry>

  // -- Direct LIKE search fallback (more reliable than FTS) --

  @Query("SELECT * FROM journal_entries WHERE rawText LIKE '%' || :keyword || '%' ORDER BY timestamp DESC LIMIT :limit")
  suspend fun searchEntriesByKeyword(keyword: String, limit: Int = 5): List<JournalEntry>

  // -- Aggregations for History tab --

  @Query(
    """
    SELECT (timestamp / 86400000) * 86400000 as dayTimestamp, COUNT(*) as entryCount
    FROM journal_entries
    GROUP BY dayTimestamp
    ORDER BY dayTimestamp DESC
    """
  )
  fun getDayCardsFlow(): Flow<List<DayCard>>

  // -- Entity-based filtering for History --

  @Query(
    """
    SELECT DISTINCT je.* FROM journal_entries je
    INNER JOIN journal_entities ent ON ent.entryId = je.id
    WHERE (:person IS NULL OR (ent.entityType = 'PERSON' AND ent.entityValue LIKE '%' || :person || '%'))
    AND (:activity IS NULL OR (ent.entityType = 'ACTIVITY' AND ent.entityValue LIKE '%' || :activity || '%'))
    AND (:mood IS NULL OR (ent.entityType = 'MOOD' AND ent.entityValue LIKE '%' || :mood || '%'))
    AND (:location IS NULL OR (ent.entityType = 'LOCATION' AND ent.entityValue LIKE '%' || :location || '%'))
    AND (:from IS NULL OR je.timestamp >= :from)
    AND (:to IS NULL OR je.timestamp < :to)
    ORDER BY je.timestamp DESC
    """
  )
  suspend fun filterEntries(
    person: String? = null,
    activity: String? = null,
    mood: String? = null,
    location: String? = null,
    from: Long? = null,
    to: Long? = null,
  ): List<JournalEntry>

  // -- Chat messages persistence --

  @Insert
  suspend fun insertChatMessage(message: ChatMessageEntity)

  @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
  suspend fun getAllChatMessages(): List<ChatMessageEntity>

  @Query("DELETE FROM chat_messages")
  suspend fun clearChatMessages()

  @Query("UPDATE chat_messages SET transcript = :transcript WHERE id = :id")
  suspend fun updateTranscript(id: Long, transcript: String)
}
