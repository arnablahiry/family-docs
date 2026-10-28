package com.familydocs.app.domain

import java.util.Locale

/** Issuing countries (ISO 3166 alpha-2) for card flags, filter pills and the "Issued by" picker. Covers every country Java knows. */
object Countries {
    /** Default selection at sign-up. */
    val DEFAULT = listOf("IN", "GR")
    /** Shown first in the picker before searching. */
    val POPULAR = listOf("IN", "GR", "DE", "FR", "IT", "NL", "ES", "GB", "US", "CA", "AU", "AE", "CH", "SG", "JP")

    private val OVERRIDES = mapOf("AE" to "UAE", "GB" to "United Kingdom", "US" to "United States")

    /** Every ISO country, sorted by English name. */
    val ALL: List<String> by lazy { Locale.getISOCountries().filter { it != "AQ" }.sortedBy { name(it) } }

    private val ALPHA3: Map<String, String> by lazy {
        Locale.getISOCountries().associateBy { runCatching { region(it).isO3Country }.getOrDefault("") } + ("D" to "DE")
    }

    /** Short label for filter pills: the ISO code, with the familiar USA / UK. */
    fun short(code: String): String = when (code) { "US" -> "USA"; "GB" -> "UK"; else -> code }

    fun name(code: String): String = OVERRIDES[code] ?: runCatching { region(code).getDisplayCountry(Locale.ENGLISH) }.getOrDefault("").ifBlank { code }

    private fun region(code: String): Locale = Locale.Builder().setRegion(code).build()

    /** "IND" → "IN"; null when unknown. */
    fun fromAlpha3(a3: String): String? = ALPHA3[a3.replace("<", "")]

    fun search(query: String, exclude: Collection<String> = emptyList(), limit: Int = 24): List<String> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return POPULAR.filter { it !in exclude }
        return ALL.filter { it !in exclude && (name(it).lowercase().contains(q) || it.lowercase() == q) }.take(limit)
    }

    /** Emoji flag from regional-indicator letters; Android draws these for every country. */
    fun flagEmoji(code: String): String =
        if (code.length == 2 && code.all { it in 'A'..'Z' }) code.map { String(Character.toChars(0x1F1E6 + (it - 'A'))) }.joinToString("") else ""

    fun encode(list: List<String>) = list.joinToString(",")
    fun decode(s: String?) = s?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty().ifEmpty { DEFAULT }

    /** "" when unknown. Order: MRZ issuing state → document type → wording in the scanned text → the document's name. */
    fun infer(docName: String, fields: List<DocField>, text: String): String {
        fields.firstOrNull { Regex("issuing country|issued by", RegexOption.IGNORE_CASE).containsMatchIn(it.label) && it.value.isNotBlank() }
            ?.let { f -> Regex("\\(([A-Z]{1,3})\\)").find(f.value)?.groupValues?.get(1)?.let { return fromAlpha3(it) ?: it } }
        if (DocTypes.forName(docName).key in setOf("aadhaar", "pan", "voter", "driving")) return "IN"
        if (Regex("₹|GOVT\\. OF INDIA|GOVERNMENT OF INDIA|REPUBLIC OF INDIA", RegexOption.IGNORE_CASE).containsMatchIn(text)) return "IN"
        if (Regex("HELLENIC|ΕΛΛΗΝΙΚΗ|GREECE|\\bAFM\\b|ΑΦΜ|AMKA|ΑΜΚΑ", RegexOption.IGNORE_CASE).containsMatchIn(text)) return "GR"
        // numbers that belong to one country, named as such ("AFM", "AMKA", "SSN", "National Insurance")
        if (Regex("\\bAFM\\b|ΑΦΜ|\\bAMKA\\b|ΑΜΚΑ|\\bE?FKA\\b|Ε?ΦΚΑ|TAXIS", RegexOption.IGNORE_CASE).containsMatchIn(docName)) return "GR"
        if (Regex("\\bSSN\\b", RegexOption.IGNORE_CASE).containsMatchIn(docName)) return "US"
        if (Regex("national insurance|\\bNINO?\\b", RegexOption.IGNORE_CASE).containsMatchIn(docName)) return "GB"
        return ""
    }
}
