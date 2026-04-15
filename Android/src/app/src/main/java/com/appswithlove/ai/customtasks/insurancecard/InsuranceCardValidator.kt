package com.appswithlove.ai.customtasks.insurancecard

import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class FieldValidation(
  val isValid: Boolean,
  val message: String = "",
)

data class CardValidationResult(
  val name: FieldValidation,
  val vorname: FieldValidation,
  val geburtsdatum: FieldValidation,
  val versichertennummer: FieldValidation,
  val ahvNummer: FieldValidation,
  val versicherer: FieldValidation,
  val kartenNummer: FieldValidation,
) {
  val allValid: Boolean
    get() = listOf(name, vorname, geburtsdatum, ahvNummer, versicherer)
      .all { it.isValid }
}

object InsuranceCardValidator {

  fun validate(result: InsuranceCardResult): CardValidationResult {
    return CardValidationResult(
      name = validateName(result.name),
      vorname = validateName(result.vorname),
      geburtsdatum = validateDate(result.geburtsdatum),
      versichertennummer = validateInsuranceNumber(result.versichertennummer),
      ahvNummer = validateAhvNumber(result.ahvNummer),
      versicherer = validateVersicherer(result.versicherer),
      kartenNummer = validateKartenNummer(result.kartenNummer),
    )
  }

  private fun validateName(name: String): FieldValidation {
    if (name.isBlank()) return FieldValidation(false, "Fehlt")
    if (name.length < 2) return FieldValidation(false, "Zu kurz")
    if (name.any { it.isDigit() }) return FieldValidation(false, "Enthält Zahlen")
    return FieldValidation(true)
  }

  private fun validateDate(date: String): FieldValidation {
    if (date.isBlank()) return FieldValidation(false, "Fehlt")

    val formats = listOf(
      "dd.MM.yyyy", "dd/MM/yyyy", "yyyy-MM-dd",
      "dd.MM.yy", "dd/MM/yy", "d.M.yyyy", "d.M.yy",
    )
    for (fmt in formats) {
      try {
        val parsed = LocalDate.parse(date, DateTimeFormatter.ofPattern(fmt))
        val now = LocalDate.now()
        if (parsed.isAfter(now)) return FieldValidation(false, "Datum in der Zukunft")
        if (parsed.isBefore(now.minusYears(150))) return FieldValidation(false, "Unrealistisches Datum")
        return FieldValidation(true)
      } catch (_: Exception) { }
    }
    return FieldValidation(false, "Ungültiges Format")
  }

  /**
   * Validates Swiss AHV/OASI number (new format: 756.XXXX.XXXX.XX).
   * 13-digit EAN-13 with country prefix 756. Last digit is check digit.
   */
  private fun validateAhvNumber(ahv: String): FieldValidation {
    if (ahv.isBlank()) return FieldValidation(false, "Fehlt")

    val digits = ahv.replace(Regex("[^0-9]"), "")
    if (digits.length != 13) return FieldValidation(false, "Muss 13 Ziffern haben")
    if (!digits.startsWith("756")) return FieldValidation(false, "Muss mit 756 beginnen")

    // EAN-13 check digit verification
    val checkDigit = calculateEan13CheckDigit(digits.substring(0, 12))
    if (checkDigit != digits[12].digitToInt()) {
      return FieldValidation(false, "Ungültige Prüfziffer")
    }

    return FieldValidation(true)
  }

  /**
   * EAN-13 check digit: sum digits alternating weight 1,3. Check = (10 - sum%10) % 10
   */
  private fun calculateEan13CheckDigit(first12: String): Int {
    var sum = 0
    for (i in first12.indices) {
      val digit = first12[i].digitToInt()
      sum += if (i % 2 == 0) digit else digit * 3
    }
    return (10 - (sum % 10)) % 10
  }

  private fun validateInsuranceNumber(number: String): FieldValidation {
    if (number.isBlank()) return FieldValidation(true) // optional on some cards
    val digits = number.replace(Regex("[^0-9]"), "")
    if (digits.length < 5) return FieldValidation(false, "Zu kurz")
    return FieldValidation(true)
  }

  private val KNOWN_INSURERS = listOf(
    "Groupe Mutuel", "Mutuel Assurance", "Luzerner Hinterland", "Vita Surselva", "Easy Sana",
    "Agrisano", "Aquilana", "Assura", "Atupri", "Avenir", "Birchmeier", "Compact",
    "Concordia", "CSS", "Curaulta", "EGK", "Einsiedler", "Galenos", "Glarner",
    "Helsana", "Hotela", "Innova", "Kolping", "KPT", "Metallvita",
    "Mutuelle Neuchâteloise", "ÖKK", "Philos", "Rhenusana", "Sana24", "Sanagate",
    "Sanitas", "SLKK", "Sodalis", "Steffisburg", "Sumiswalder", "Supra", "Swica",
    "Sympany", "Visana", "Visperterminen", "Vivao", "Wädenswil", "AMB",
  )

  private fun validateVersicherer(name: String): FieldValidation {
    if (name.isBlank()) return FieldValidation(false, "Fehlt")
    val match = KNOWN_INSURERS.any { name.contains(it, ignoreCase = true) }
    if (!match) return FieldValidation(true, "Unbekannter Versicherer")
    return FieldValidation(true)
  }

  private fun validateKartenNummer(number: String): FieldValidation {
    if (number.isBlank()) return FieldValidation(true) // optional
    return FieldValidation(true)
  }
}
