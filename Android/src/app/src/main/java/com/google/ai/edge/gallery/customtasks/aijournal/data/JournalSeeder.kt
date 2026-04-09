package com.google.ai.edge.gallery.customtasks.aijournal.data

import java.util.Calendar

data class SeedEntry(
  val daysAgo: Int,
  val hour: Int,
  val minute: Int,
  val rawText: String,
  val inputType: String = "text",
  val entities: List<Pair<String, String>>, // type to value
)

object JournalSeeder {

  suspend fun seedIfEmpty(dao: JournalDao) {
    val existing = dao.getRecentEntries(1)
    if (existing.isNotEmpty()) return

    for (seed in SEED_DATA) {
      val ts = daysAgoToTimestamp(seed.daysAgo, seed.hour, seed.minute)
      val entryId = dao.insertEntry(
        JournalEntry(
          timestamp = ts,
          rawText = seed.rawText,
          inputType = seed.inputType,
        )
      )
      val entities = seed.entities.map { (type, value) ->
        JournalEntity(entryId = entryId, entityType = type, entityValue = value)
      }
      if (entities.isNotEmpty()) dao.insertEntities(entities)
    }

    // Add a weekly summary for week 2
    val week2Start = daysAgoToTimestamp(14, 0, 0)
    val week2End = daysAgoToTimestamp(7, 0, 0)
    dao.insertSummary(
      JournalSummary(
        periodType = "WEEKLY",
        periodStart = week2Start,
        periodEnd = week2End,
        summaryText = "Active week. Hiked with Anna in Zürich on Saturday. Coffee with Hans on Thursday to discuss startup ideas. Worked on the mobile app daily, feeling productive. Yoga sessions Monday and Wednesday. Mood mostly upbeat, some stress mid-week from a deadline.",
        tokenCount = 55,
      )
    )
  }

  private fun daysAgoToTimestamp(daysAgo: Int, hour: Int, minute: Int): Long {
    return Calendar.getInstance().apply {
      add(Calendar.DAY_OF_YEAR, -daysAgo)
      set(Calendar.HOUR_OF_DAY, hour)
      set(Calendar.MINUTE, minute)
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
    }.timeInMillis
  }

  private val SEED_DATA = listOf(
    // --- 21 days ago (3 weeks) ---
    SeedEntry(
      daysAgo = 21, hour = 9, minute = 15,
      rawText = "Morning run along the lake with Sarah. Beautiful sunrise, we did about 5k. She mentioned she's training for the Zürich marathon in October.",
      entities = listOf("PERSON" to "Sarah", "ACTIVITY" to "running", "MOOD" to "energized", "LOCATION" to "lake"),
    ),
    SeedEntry(
      daysAgo = 21, hour = 20, minute = 30,
      rawText = "Cooked Thai curry for dinner and watched a documentary about deep sea creatures. Very relaxing evening.",
      entities = listOf("ACTIVITY" to "cooking", "ACTIVITY" to "watching documentary", "MOOD" to "relaxed"),
    ),

    // --- 18 days ago ---
    SeedEntry(
      daysAgo = 18, hour = 12, minute = 0,
      rawText = "Lunch with Hans at the Italian place near Paradeplatz. He's excited about his new startup idea — something with AI and sustainability. We brainstormed for two hours.",
      entities = listOf("PERSON" to "Hans", "ACTIVITY" to "lunch", "ACTIVITY" to "brainstorming", "LOCATION" to "Paradeplatz", "MOOD" to "inspired"),
    ),

    // --- 15 days ago ---
    SeedEntry(
      daysAgo = 15, hour = 18, minute = 45,
      rawText = "Yoga class was amazing today. The instructor introduced a new breathing technique that really helped with my stress. Need to remember: box breathing, 4-4-4-4.",
      entities = listOf("ACTIVITY" to "yoga", "MOOD" to "calm", "EVENT" to "learned box breathing"),
    ),

    // --- 14 days ago ---
    SeedEntry(
      daysAgo = 14, hour = 10, minute = 0,
      rawText = "Hiked up Uetliberg with Anna. Perfect weather, clear view of the Alps. We stopped at the restaurant on top for Rösti. She told me about her new job at Google starting next month.",
      entities = listOf("PERSON" to "Anna", "ACTIVITY" to "hiking", "LOCATION" to "Uetliberg", "LOCATION" to "Zürich", "MOOD" to "happy", "EVENT" to "Anna starting at Google"),
    ),

    // --- 11 days ago ---
    SeedEntry(
      daysAgo = 11, hour = 8, minute = 30,
      rawText = "Feeling overwhelmed today. Big deadline at work for the mobile app release. Had three meetings back to back. Didn't even have time for lunch.",
      entities = listOf("ACTIVITY" to "work", "MOOD" to "stressed", "EVENT" to "mobile app deadline"),
    ),
    SeedEntry(
      daysAgo = 11, hour = 22, minute = 0,
      rawText = "Called Mom to catch up. She's planning to visit Zürich in May. We talked about restaurants to try. Feeling better after hearing her voice.",
      entities = listOf("PERSON" to "Mom", "ACTIVITY" to "phone call", "LOCATION" to "Zürich", "MOOD" to "comforted", "EVENT" to "Mom visiting in May"),
    ),

    // --- 8 days ago ---
    SeedEntry(
      daysAgo = 8, hour = 14, minute = 30,
      rawText = "Coffee with Marco at Café Sprüngli. He just got back from Japan and showed me incredible photos from Kyoto. We talked about potentially doing a trip together to Portugal this summer.",
      entities = listOf("PERSON" to "Marco", "ACTIVITY" to "coffee", "LOCATION" to "Café Sprüngli", "MOOD" to "excited", "EVENT" to "planning Portugal trip"),
    ),

    // --- 6 days ago ---
    SeedEntry(
      daysAgo = 6, hour = 7, minute = 45,
      rawText = "Early morning swim at the Hallenbad. Did 40 laps, personal best! Feeling really strong lately. The consistent training is paying off.",
      entities = listOf("ACTIVITY" to "swimming", "LOCATION" to "Hallenbad", "MOOD" to "proud"),
    ),
    SeedEntry(
      daysAgo = 6, hour = 19, minute = 0,
      rawText = "Dinner party at Lisa and Tom's place. They made homemade pasta. Met their friend Julia who works in architecture — fascinating conversation about sustainable building design.",
      entities = listOf("PERSON" to "Lisa", "PERSON" to "Tom", "PERSON" to "Julia", "ACTIVITY" to "dinner party", "MOOD" to "social", "EVENT" to "met Julia the architect"),
    ),

    // --- 4 days ago ---
    SeedEntry(
      daysAgo = 4, hour = 11, minute = 0,
      rawText = "Worked from the library today for a change. So much more productive without the office noise. Finished the API integration I've been struggling with all week.",
      entities = listOf("ACTIVITY" to "work", "LOCATION" to "library", "MOOD" to "productive", "EVENT" to "finished API integration"),
    ),

    // --- 3 days ago ---
    SeedEntry(
      daysAgo = 3, hour = 16, minute = 30,
      rawText = "Ran into Hans on Bahnhofstrasse. Quick catch-up — his startup got accepted into the accelerator program! Really happy for him. We agreed to celebrate with dinner next week.",
      entities = listOf("PERSON" to "Hans", "LOCATION" to "Bahnhofstrasse", "MOOD" to "happy", "EVENT" to "Hans accepted into accelerator"),
    ),

    // --- 2 days ago ---
    SeedEntry(
      daysAgo = 2, hour = 8, minute = 0,
      rawText = "Yoga and meditation morning. 30 minutes of each. Set an intention for the week: focus on being present and not rushing through things.",
      entities = listOf("ACTIVITY" to "yoga", "ACTIVITY" to "meditation", "MOOD" to "mindful"),
    ),
    SeedEntry(
      daysAgo = 2, hour = 20, minute = 15,
      rawText = "Movie night with Sarah and Marco. Watched Dune Part Two. Incredible cinematography. Heated debate about the ending afterwards over wine.",
      inputType = "voice_gemma",
      entities = listOf("PERSON" to "Sarah", "PERSON" to "Marco", "ACTIVITY" to "movie night", "MOOD" to "entertained"),
    ),

    // --- 1 day ago (yesterday) ---
    SeedEntry(
      daysAgo = 1, hour = 12, minute = 30,
      rawText = "Tried the new ramen place on Langstrasse. The tonkotsu was incredible — easily the best ramen in Zürich. Need to bring Anna here.",
      entities = listOf("ACTIVITY" to "lunch", "LOCATION" to "Langstrasse", "MOOD" to "satisfied", "EVENT" to "discovered best ramen in Zürich"),
    ),
    SeedEntry(
      daysAgo = 1, hour = 17, minute = 0,
      rawText = "Read 50 pages of 'The Pragmatic Programmer'. Great chapter on design by contract. Making notes to apply it to the codebase at work.",
      entities = listOf("ACTIVITY" to "reading", "MOOD" to "focused", "EVENT" to "reading Pragmatic Programmer"),
    ),
  )
}
