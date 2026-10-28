package com.familydocs.app.ocr

import com.familydocs.app.domain.DocField

/**
 * Field labels that mean the same thing ("Date of Expiry" / "Valid Until", "Sex" / "Gender", "Passport No." / "Document No.")
 * share one key, so a scan fills the preset field instead of adding a second one next to it.
 * Pure Kotlin so it runs in JVM unit tests.
 */
object FieldKeys {
    private fun norm(label: String) = label.lowercase()
        .replace(Regex("[^\\p{L}\\p{N} ]"), " ").replace(Regex("\\s+"), " ").trim()

    fun canonical(label: String): String {
        val l = norm(label)
        return when {
            Regex("expir|valid (until|till|thru|to|upto)|^valid$|validity|λήξ|ισχ").containsMatchIn(l) -> "expiry"
            Regex("birth|^dob$|γέννησ").containsMatchIn(l) -> "dob"
            Regex("date of issue|issue date|issued on|valid from|^issued$|έκδοσ").containsMatchIn(l) -> "issued"
            Regex("^(sex|gender|φύλο)$").containsMatchIn(l) -> "sex"
            Regex("issuing (country|state)|issued by|^issuer$").containsMatchIn(l) -> "issuer"
            Regex("^(surname|last name|family name|επώνυμο)$").containsMatchIn(l) -> "surname"
            Regex("^(given names?|first names?|forenames?)$").containsMatchIn(l) -> "given"
            Regex("^(name|full name|holder|holder name|όνομα|ονοματεπώνυμο)$").containsMatchIn(l) -> "name"
            Regex("^(nationality|ιθαγένεια)$").containsMatchIn(l) -> "nationality"
            Regex("^(subject|field|subject field|field of study|discipline|major)$").containsMatchIn(l) -> "subject"
            Regex("^(specialisation|specialization|specialty|speciality)$").containsMatchIn(l) -> "specialisation"
            // "Passport No.", "Permit No.", "Document No.", "Visa Number", "Licence Nr" → the document's number
            Regex("(^| )(no|number|nr|n°|#)$").containsMatchIn(l) && !Regex("phone|mobile|account|policy|contract").containsMatchIn(l) -> "number"
            else -> l
        }
    }

    fun same(a: String, b: String) = canonical(a) == canonical(b)
}

object FieldMerge {
    /**
     * Puts scanned values into the fields already on screen:
     *  - values filled by an earlier scan are cleared first (preset fields stay, as blanks; other old scan fields go);
     *  - when the user hasn't touched the fields yet, any preset field missing from the list is added (in preset order);
     *  - each scanned value fills the matching field if it is empty, or else is added as a new field.
     * Values typed by hand are never replaced.
     * [keepEarlierScan]: adding another page of the same document (the back of a card): what the first page filled stays.
     */
    fun merge(current: List<DocField>, template: List<String>, scanned: List<DocField>, ensureTemplate: Boolean, keepEarlierScan: Boolean = false): List<DocField> {
        val tKeys = template.map(FieldKeys::canonical).toSet()
        val out = current.mapNotNull { f ->
            when {
                !f.auto || keepEarlierScan -> f
                FieldKeys.canonical(f.label) in tKeys -> f.copy(value = "", auto = false, verified = null)
                else -> null
            }
        }.toMutableList()
        if (ensureTemplate) {
            // Preset fields come first, in preset order; anything else the user added stays after them.
            val ordered = template.map { t -> out.firstOrNull { FieldKeys.same(it.label, t) } ?: DocField(t, "") }
            out.removeAll { o -> ordered.any { it === o } }
            out.addAll(0, ordered)
        }
        for (s in scanned) {
            if (s.value.isBlank()) continue
            val key = FieldKeys.canonical(s.label)
            val slot = out.indexOfFirst { FieldKeys.canonical(it.label) == key && it.value.isBlank() }
            when {
                slot >= 0 -> out[slot] = out[slot].copy(value = s.value, auto = true, verified = s.verified)
                out.any { FieldKeys.canonical(it.label) == key } -> Unit   // already has a value (typed or scanned): keep it
                else -> out += s.copy(auto = true)
            }
        }
        return out
    }
}
