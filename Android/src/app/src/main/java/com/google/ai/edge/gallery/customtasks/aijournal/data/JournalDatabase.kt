package com.google.ai.edge.gallery.customtasks.aijournal.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
  entities = [JournalEntry::class, JournalEntity::class, JournalSummary::class, JournalEntryFts::class, ChatMessageEntity::class],
  version = 2,
  exportSchema = false,
)
abstract class JournalDatabase : RoomDatabase() {
  abstract fun journalDao(): JournalDao

  companion object {
    @Volatile
    private var INSTANCE: JournalDatabase? = null

    fun getInstance(context: Context): JournalDatabase {
      return INSTANCE ?: synchronized(this) {
        INSTANCE ?: Room.databaseBuilder(
          context.applicationContext,
          JournalDatabase::class.java,
          "ai_journal.db",
        ).addCallback(object : Callback() {
          override fun onOpen(db: SupportSQLiteDatabase) {
            super.onOpen(db)
            CoroutineScope(Dispatchers.IO).launch {
              INSTANCE?.let { JournalSeeder.seedIfEmpty(it.journalDao()) }
            }
          }
        }).fallbackToDestructiveMigration()
          .build().also { INSTANCE = it }
      }
    }
  }
}
