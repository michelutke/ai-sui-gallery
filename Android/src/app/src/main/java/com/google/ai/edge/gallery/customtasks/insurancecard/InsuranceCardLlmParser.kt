package com.google.ai.edge.gallery.customtasks.insurancecard

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException

object InsuranceCardLlmParser {
  private val gson = Gson()
  private val codeFenceRegex = Regex("```(?:json)?\\s*([\\s\\S]*?)```")

  fun parse(response: String): InsuranceCardResult? {
    // Try to extract JSON from markdown code fences first, then raw response
    val jsonText = codeFenceRegex.find(response)?.groupValues?.get(1)?.trim()
      ?: response.trim()

    return try {
      gson.fromJson(jsonText, InsuranceCardResult::class.java)
    } catch (_: JsonSyntaxException) {
      null
    }
  }
}
