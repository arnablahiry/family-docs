package com.familydocs.app.domain

/**
 * One digitised value on a document, e.g. "Passport No." → "Z1234567".
 * [auto] = filled by OCR, [verified] = passed a check digit (Verhoeff / MRZ), null when no check exists.
 */
data class DocField(
    val label: String,
    val value: String,
    val sensitive: Boolean = DocTypes.isSensitive(label),
    val auto: Boolean = false,
    val verified: Boolean? = null,
)

fun maskValue(v: String): String {
    val clean = v.replace(Regex("\\s"), "")
    return if (clean.length <= 4) v else "•••• " + clean.takeLast(4)
}
