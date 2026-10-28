package com.familydocs.app.domain

/**
 * Picks a placeholder icon + colour for a document from the name the user typed.
 * Order matters: the first matching rule wins ("Vaccination certificate" → vaccine, not certificate).
 * Kept free of Android imports so it can be unit-tested on the JVM.
 */
enum class DocIcon { HOUSE, BOLT, FINGERPRINT, CARD, PASSPORT, PLANE, RESIDENCE, CAR, BALLOT, SHIELD, SYRINGE, BANK, CAP, CERTIFICATE, MEDICAL, RECEIPT, ID, FILE, KEY, PEOPLE }

data class DocType(
    val key: String,
    val label: String,
    val icon: DocIcon,
    /** ARGB colour, e.g. 0xFFD9822B */
    val hue: Long,
    val matched: Boolean = true,
    val initials: String = "",
)

object DocTypes {
    private val I = RegexOption.IGNORE_CASE

    private val RULES: List<Pair<Regex, DocType>> = listOf(
        Regex("aadh?aa?r|uidai|आधार", I) to DocType("aadhaar", "Aadhaar", DocIcon.FINGERPRINT, 0xFFD9822B),
        Regex("\\bpan\\b|permanent account", I) to DocType("pan", "PAN Card", DocIcon.CARD, 0xFF2F6FB0),
        Regex("passport|διαβατ|pasaporte|reisepass", I) to DocType("passport", "Passport", DocIcon.PASSPORT, 0xFF284A80),
        Regex("visa|schengen|βίζα|θεώρηση", I) to DocType("visa", "Visa", DocIcon.PLANE, 0xFF7A4FB5),
        Regex("\\brent|lease|tenan|\\bdeed\\b|property|ενοίκ|μίσθωσ", I) to DocType("property", "Rental Agreement", DocIcon.HOUSE, 0xFF6B5B95),
        Regex("\\bbill\\b|electric|utility|wi-?fi|broadband|internet|\\bgas\\b|water|ΔΕΗ", I) to DocType("bill", "Electricity Bill", DocIcon.BOLT, 0xFFC98A12),
        Regex("residen|permit|άδεια|aufenthalt|permesso|\\btrc\\b|\\bbrp\\b|green card|\\boci\\b", I) to DocType("residence", "Residence Permit", DocIcon.RESIDENCE, 0xFF2F7F7A),
        Regex("driv|licen[cs]e|\\bdl\\b", I) to DocType("driving", "Driving Licence", DocIcon.CAR, 0xFFB5523A),
        Regex("voter|\\bepic\\b|election", I) to DocType("voter", "Voter ID", DocIcon.BALLOT, 0xFF5E7029),
        Regex("insur|mediclaim|ayushman|policy|\\behic\\b|health card", I) to DocType("insurance", "Health Insurance", DocIcon.SHIELD, 0xFF3E8E5E),
        Regex("vaccin|covid|immuni", I) to DocType("vaccine", "Vaccination Record", DocIcon.SYRINGE, 0xFFC0467A),
        Regex("bank|passbook|cheque|debit|credit|iban|\\bupi\\b", I) to DocType("bank", "Bank Passbook", DocIcon.BANK, 0xFF5B56C9),
        Regex("degree|diploma|marksheet|transcript|school|universit|\\bph\\.?d|\\bm\\.?sc|\\bb\\.?sc|\\bb\\.?tech", I) to DocType("education", "Degree Certificate", DocIcon.CAP, 0xFF8A6D1F),
        Regex("birth|marriage|death|certificate|domicile|caste|income", I) to DocType("certificate", "Birth Certificate", DocIcon.CERTIFICATE, 0xFF9A5B2E),
        Regex("medical|prescription|blood|report|\\blab\\b", I) to DocType("medical", "Medical Report", DocIcon.MEDICAL, 0xFFC2453D),
        Regex("social security|\\bssn\\b|\\bamka\\b|ΑΜΚΑ|national insurance|\\bnino?\\b|sozialversicherung|seguridad social|s[ée]curit[ée] sociale|\\besic\\b|\\bepfo?\\b|\\buan\\b", I) to DocType("social", "Social Security", DocIcon.PEOPLE, 0xFF4F7A96),
        Regex("\\btax\\b|\\bitr\\b|form ?16|\\bgst\\b|\\bafm\\b|ΑΦΜ", I) to DocType("tax", "Tax Document", DocIcon.RECEIPT, 0xFF47708A),
        Regex("\\bid\\b|identity|ταυτότητα|national", I) to DocType("id", "National ID", DocIcon.ID, 0xFF4B5D7A),
    )

    private val FALLBACK = longArrayOf(0xFF4B6BAF, 0xFFA0573C, 0xFF2F7F7A, 0xFF7A4FB5, 0xFF8A6D1F, 0xFFB04A6E, 0xFF3F7C4B, 0xFF5868A8)

    val SUGGESTIONS = listOf(
        "Aadhaar", "PAN Card", "Passport", "Visa", "Residence Permit", "Driving Licence", "Voter ID",
        "Social Security", "Health Insurance", "Birth Certificate", "Bank Passbook", "Vaccination Record", "Degree Certificate",
    )

    /** Empty field templates used when OCR finds nothing (or the user skips the file). */
    val TEMPLATES: Map<String, List<String>> = mapOf(
        "aadhaar" to listOf("Aadhaar Number", "Name", "Date of Birth", "Gender", "Address"),
        "pan" to listOf("PAN", "Name", "Father's Name", "Date of Birth"),
        "passport" to listOf("Passport No.", "Surname", "Given Names", "Nationality", "Date of Birth", "Date of Issue", "Date of Expiry"),
        "visa" to listOf("Visa No.", "Type", "Valid From", "Valid Until", "Entries"),
        "residence" to listOf("Permit No.", "Type of Permit", "Valid Until", "Nationality"),
        "driving" to listOf("Licence No.", "Valid Until", "Vehicle Classes"),
        "voter" to listOf("EPIC No.", "Name"),
        "insurance" to listOf("Policy No.", "Insurer", "Valid Until"),
        "bank" to listOf("Account No.", "IFSC / IBAN", "Branch"),
        "social" to listOf("Social Security No.", "Name", "Issued On"),
        "tax" to listOf("Tax No.", "Name"),
        "id" to listOf("ID No.", "Surname", "Given Names", "Date of Birth", "Valid Until"),
        "education" to listOf("Degree", "Subject / Field", "Specialisation", "University", "Year of Passing", "Grade"),
        "property" to listOf("Contract No.", "Landlord", "Address", "Monthly Rent", "Valid Until"),
        "bill" to listOf("Account No.", "Provider", "Amount", "Due Date"),
        "other" to listOf("Document No.", "Name", "Issued On", "Valid Until"),
    )

    /** Labels whose values are masked until the user taps the eye icon. */
    val SENSITIVE = Regex("social security no|\\bssn\\b|\\bamka\\b|aadhaar number|\\bvid\\b|passport no|permit no|document no|visa no|licence no|account no|iban|policy no|card no|\\bpan\\b|epic", I)

    fun isSensitive(label: String) = SENSITIVE.containsMatchIn(label)

    /** National ID cards show the issuing country's flag on their card unless turned off; other documents only when turned on. */
    fun flagByDefault(name: String) = forName(name).key in FLAG_TYPES

    /** National ID cards: these show the issuing country's flag faintly behind the card art unless turned off. */
    val FLAG_TYPES = setOf("aadhaar", "visa", "residence", "id", "pan", "voter", "social", "tax")

    fun forName(name: String): DocType {
        val n = name.trim()
        RULES.firstOrNull { it.first.containsMatchIn(n) }?.let { return it.second }
        val initials = n.split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2).joinToString("") { it.first().uppercase() }
        return DocType("other", n, DocIcon.FILE, FALLBACK[Math.floorMod(fnv(n), FALLBACK.size)], matched = false, initials = initials)
    }

    fun template(name: String): List<String> = TEMPLATES[forName(name).key] ?: TEMPLATES.getValue("other")

    private fun fnv(s: String): Int {
        var h = -0x7ee3623b // 2166136261
        for (c in s) { h = h xor c.code; h *= 16777619 }
        return h
    }
}
