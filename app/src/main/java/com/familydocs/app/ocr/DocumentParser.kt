package com.familydocs.app.ocr

import com.familydocs.app.domain.Countries
import com.familydocs.app.domain.DocField
import com.familydocs.app.domain.DocTypes
import java.time.Year

/**
 * Turns raw OCR text (from ML Kit) into labelled, editable fields.
 *
 * Supported:
 *  - Machine-readable zones: passports (TD3), visas (MRV-A), ID cards / residence permits (TD1),
 *    with ICAO 9303 check digits.
 *  - Aadhaar (12 digits + Verhoeff checksum), PAN, Indian driving licence, Voter ID (EPIC).
 *  - Anything else: the preset fields for the document's type (from the name the user typed) looked up by their
 *    label and its usual wordings ("Valid until", "Expiry", "Λήξη"…), then "Label: value" lines, IBAN and dates.
 *
 * Pure Kotlin (no Android imports) so it runs in plain JVM unit tests.
 */
object DocumentParser {

    data class Result(val type: String, val suggestedName: String?, val fields: List<DocField>)

    private val DATE = Regex("\\b(\\d{2})[/\\-.](\\d{2})[/\\-.](\\d{4})\\b")

    /** [hint]: the document name the user typed, if any ("Passport", "Health insurance"…): picks the fields to look for. */
    fun parse(raw: String, hint: String? = null): Result {
        val text = raw.replace("\r", "")
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val up = text.uppercase()

        (findMrz(lines) ?: findMrzLoose(text))?.let { mrz -> return parseMrz(mrz, up).let { r -> r.copy(fields = r.fields.filter { it.value.isNotBlank() }) } }
        parseAadhaar(text, lines, up)?.let { return it }
        parsePan(lines, up)?.let { return it }
        parseDrivingLicence(lines, up)?.let { return it }
        parseVoter(lines, up)?.let { return it }
        return parseGeneric(text, lines, up, hint)
    }

    // ---------------- checksums ----------------
    private val VD = arrayOf(
        intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9), intArrayOf(1, 2, 3, 4, 0, 6, 7, 8, 9, 5), intArrayOf(2, 3, 4, 0, 1, 7, 8, 9, 5, 6),
        intArrayOf(3, 4, 0, 1, 2, 8, 9, 5, 6, 7), intArrayOf(4, 0, 1, 2, 3, 9, 5, 6, 7, 8), intArrayOf(5, 9, 8, 7, 6, 0, 4, 3, 2, 1),
        intArrayOf(6, 5, 9, 8, 7, 1, 0, 4, 3, 2), intArrayOf(7, 6, 5, 9, 8, 2, 1, 0, 4, 3), intArrayOf(8, 7, 6, 5, 9, 3, 2, 1, 0, 4),
        intArrayOf(9, 8, 7, 6, 5, 4, 3, 2, 1, 0),
    )
    private val VP = arrayOf(
        intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9), intArrayOf(1, 5, 7, 6, 2, 8, 3, 0, 9, 4), intArrayOf(5, 8, 0, 3, 7, 9, 6, 1, 4, 2),
        intArrayOf(8, 9, 1, 6, 0, 4, 3, 5, 2, 7), intArrayOf(9, 4, 5, 3, 1, 2, 6, 8, 7, 0), intArrayOf(4, 2, 8, 6, 5, 7, 3, 9, 0, 1),
        intArrayOf(2, 7, 9, 3, 8, 0, 6, 4, 1, 5), intArrayOf(7, 0, 4, 6, 9, 1, 3, 2, 5, 8),
    )
    private val VINV = intArrayOf(0, 4, 3, 2, 1, 5, 6, 7, 8, 9)

    fun verhoeffValid(num: String): Boolean {
        var c = 0
        num.reversed().forEachIndexed { i, ch -> c = VD[c][VP[i % 8][ch - '0']] }
        return c == 0
    }

    fun verhoeffDigit(num: String): Int {
        var c = 0
        num.reversed().forEachIndexed { i, ch -> c = VD[c][VP[(i + 1) % 8][ch - '0']] }
        return VINV[c]
    }

    fun mrzCheck(s: String): Char {
        val w = intArrayOf(7, 3, 1)
        var sum = 0
        s.forEachIndexed { i, ch ->
            val v = when (ch) { '<' -> 0; in '0'..'9' -> ch - '0'; in 'A'..'Z' -> ch - 'A' + 10; else -> 0 }
            sum += v * w[i % 3]
        }
        return '0' + sum % 10
    }

    // ---------------- MRZ ----------------
    data class Mrz(val format: String, val lines: List<String>)

    fun findMrz(lines: List<String>): Mrz? {
        val cand = lines.map { it.uppercase().replace("«", "<<").replace(Regex("\\s+"), "") }
            .filter { it.matches(Regex("[A-Z0-9<]{28,46}")) && it.contains('<') }
        if (cand.size >= 3 && cand.takeLast(3).all { kotlin.math.abs(it.length - 30) <= 2 }) {
            return Mrz("TD1", cand.takeLast(3).map { it.padEnd(30, '<').take(30) })
        }
        if (cand.size >= 2) {
            val l = cand.takeLast(2).map { it.padEnd(44, '<').take(44) }
            return when (l[0][0]) {
                'P' -> Mrz("TD3", l)
                'V' -> Mrz("MRVA", l)
                'I', 'A', 'C' -> Mrz("TD3", l)
                else -> null
            }
        }
        return null
    }

    // Phone OCR of the machine-readable zone is messy: lines come split or merged, '<' reads as 'K', '«' or '‹',
    // and digits as letters (0/O, 1/I, 5/S, 8/B). So search the text with all spacing removed for the fixed
    // layout of the second line and repair digit positions; the check digits then say which values to trust.
    private const val D = "[0-9OQDIULZSBG]"
    private val TD3_L2 = Regex("[A-Z0-9<]{9}[0-9OQDIULZSBG<][A-Z<]{3}$D{6}$D[MFX<]$D{6}$D")
    private val TD1_L2 = Regex("$D{6}$D[MFX<]$D{6}$D[A-Z<]{3}")

    private fun digits(s: String) = s.map { when (it) { 'O', 'Q', 'D', 'U' -> '0'; 'I', 'L' -> '1'; 'Z' -> '2'; 'S' -> '5'; 'B' -> '8'; 'G' -> '6'; else -> it } }.joinToString("")
    private fun letters(s: String) = s.map { when (it) { '0' -> 'O'; '1' -> 'I'; '5' -> 'S'; '8' -> 'B'; '2' -> 'Z'; '6' -> 'G'; else -> it } }.joinToString("")
    private fun fix(s: String, numeric: List<IntRange>, alpha: List<IntRange>): String {
        val b = StringBuilder(s)
        numeric.forEach { r -> r.forEach { i -> if (i < b.length && b[i] != '<') b.setCharAt(i, digits(b[i].toString())[0]) } }
        alpha.forEach { r -> r.forEach { i -> if (i < b.length) b.setCharAt(i, letters(b[i].toString())[0]) } }
        return b.toString()
    }

    fun findMrzLoose(text: String): Mrz? {
        val flat = text.uppercase().replace("«", "<<").replace(Regex("[‹〈＜]"), "<").replace(Regex("\\s+"), "")
        // Passport / visa: line 1 starts with P or V and has the name with "<<"; line 2 has the fixed numeric layout.
        for (m in TD3_L2.findAll(flat)) {
            val l2 = fix(flat.substring(m.range.first).padEnd(44, '<').take(44), listOf(9..9, 13..19, 21..27), listOf(10..12))
            val before = flat.substring(0, m.range.first)
            // Line 1 = "P<IND" + surname + "<<": take the last such start before line 2 (or the first after it).
            val starts = Regex("(?=[PV][A-Z<][A-Z]{3}[A-Z]{1,39}<<)").findAll(flat).map { it.range.first }.toList()
            val l1Start = starts.lastOrNull { it < m.range.first } ?: starts.firstOrNull { it >= m.range.first + 44 } ?: continue
            val l1 = fix(flat.substring(l1Start).take(if (l1Start < m.range.first) minOf(44, m.range.first - l1Start) else 44).padEnd(44, '<'), emptyList(), listOf(2..4))
            return Mrz(if (l1[0] == 'V') "MRVA" else "TD3", listOf(l1, l2))
        }
        // ID card / residence permit (3 × 30): line 2 starts with birth date, sex, expiry, nationality.
        for (m in TD1_L2.findAll(flat)) {
            val s2 = m.range.first
            if (s2 < 30) continue
            val l1 = flat.substring(s2 - 30, s2)
            if (l1[0] !in "ACI") continue
            val l2 = fix(flat.substring(s2).padEnd(30, '<').take(30), listOf(0..6, 8..14), listOf(15..17))
            val l3 = flat.substring(minOf(flat.length, s2 + 30)).padEnd(30, '<').take(30)
            return Mrz("TD1", listOf(fix(l1, listOf(14..14), listOf(2..4)), l2, l3))
        }
        return null
    }

    private fun splitName(s: String): Pair<String, String> {
        // Filler '<' that the OCR read as K, C, L or E shows up as tokens made only of those letters: drop them.
        // (also glued to the end of a name: "ARNABKKKK"), so drop those runs.
        fun clean(t: String) = t.split('<').map { it.replace(Regex("[KCLE]{3,}$"), "") }
            .filter { it.isNotEmpty() && !(it.length >= 2 && it.all { c -> c in "KCLE" }) }.joinToString(" ")
        val parts = s.split("<<")
        val surname = clean(parts.first())
        val given = clean(parts.drop(1).joinToString("<")).replace(Regex("\\s+"), " ").trim()
        return surname to given
    }

    private fun mrzDate(yymmdd: String, birth: Boolean): String {
        if (!yymmdd.matches(Regex("\\d{6}"))) return yymmdd.replace("<", "")
        val yy = yymmdd.take(2).toInt()
        val cur = Year.now().value % 100
        val year = if (birth && yy > cur) 1900 + yy else 2000 + yy
        return "${yymmdd.substring(4, 6)}/${yymmdd.substring(2, 4)}/$year"
    }

    private val NATIONALITY = mapOf(
        "IND" to "Indian", "GRC" to "Greek", "D" to "German", "DEU" to "German", "GBR" to "British", "USA" to "American",
        "FRA" to "French", "ITA" to "Italian", "ESP" to "Spanish", "NLD" to "Dutch", "CAN" to "Canadian", "AUS" to "Australian",
        "ARE" to "Emirati", "CHE" to "Swiss",
    )
    private val COUNTRY = mapOf(
        "IND" to "India", "GRC" to "Greece", "D" to "Germany", "DEU" to "Germany", "GBR" to "United Kingdom", "USA" to "United States",
        "FRA" to "France", "ITA" to "Italy", "ESP" to "Spain", "NLD" to "Netherlands", "CAN" to "Canada", "AUS" to "Australia",
        "ARE" to "United Arab Emirates", "CHE" to "Switzerland",
    )
    private fun nat(c: String) = c.replace("<", "").let { k -> (NATIONALITY[k] ?: Countries.fromAlpha3(k)?.let(Countries::name))?.let { "$it ($k)" } ?: k }
    private fun ctry(c: String) = c.replace("<", "").let { k -> (COUNTRY[k] ?: Countries.fromAlpha3(k)?.let(Countries::name))?.let { "$it ($k)" } ?: k }
    private fun sex(c: Char) = when (c) { 'M' -> "Male"; 'F' -> "Female"; else -> "Unspecified" }
    private fun f(label: String, value: String, verified: Boolean? = null) = DocField(label, value.trim(), auto = true, verified = verified)

    private fun parseMrz(m: Mrz, upText: String): Result {
        if (m.format == "TD1") {
            val (a, b, c) = m.lines
            val code = a.take(2).replace("<", "")
            val no = a.substring(5, 14)
            val (sur, given) = splitName(c)
            val isPermit = Regex("^(IR|AR|RP|CR|R)").containsMatchIn(code) ||
                Regex("RESIDENCE|PERMIT|ΑΔΕΙΑ ΔΙΑΜΟΝΗΣ|AUFENTHALT|SOGGIORNO").containsMatchIn(upText)
            return Result(
                if (isPermit) "residence" else "id",
                if (isPermit) "Residence Permit" else "National ID",
                listOf(
                    f(if (isPermit) "Permit No." else "Document No.", no.replace("<", ""), mrzCheck(no) == a[14]),
                    f("Surname", sur), f("Given Names", given), f("Nationality", nat(b.substring(15, 18))),
                    f("Date of Birth", mrzDate(b.take(6), true), mrzCheck(b.take(6)) == b[6]),
                    f("Sex", sex(b[7])),
                    f("Valid Until", mrzDate(b.substring(8, 14), false), mrzCheck(b.substring(8, 14)) == b[14]),
                    f("Issued By", ctry(a.substring(2, 5))),
                ),
            )
        }
        val (a, b) = m.lines
        val visa = m.format == "MRVA"
        val (sur, given) = splitName(a.substring(5))
        val no = b.take(9)
        return Result(
            if (visa) "visa" else "passport",
            if (visa) "Visa" else "Passport",
            listOf(
                f(if (visa) "Visa No." else "Passport No.", no.replace("<", ""), mrzCheck(no) == b[9]),
                f("Surname", sur), f("Given Names", given), f("Nationality", nat(b.substring(10, 13))),
                f("Date of Birth", mrzDate(b.substring(13, 19), true), mrzCheck(b.substring(13, 19)) == b[19]),
                f("Sex", sex(b[20])),
                f(if (visa) "Valid Until" else "Date of Expiry", mrzDate(b.substring(21, 27), false), mrzCheck(b.substring(21, 27)) == b[27]),
                f(if (visa) "Issued By" else "Issuing Country", ctry(a.substring(2, 5))),
            ),
        )
    }

    // ---------------- Indian documents ----------------
    private fun labelled(lines: List<String>, re: Regex): String {
        for ((i, l) in lines.withIndex()) {
            val m = re.find(l) ?: continue
            val after = l.substring(m.range.last + 1).trimStart(' ', ':', '.', '-', '/').trim()
            if (after.isNotEmpty() && after.any { it.isLetterOrDigit() }) return after
            if (i + 1 < lines.size) return lines[i + 1].trim()
        }
        return ""
    }

    private fun parseAadhaar(text: String, lines: List<String>, up: String): Result? {
        val aad = Regex("\\b([2-9]\\d{3})\\s?(\\d{4})\\s?(\\d{4})\\b").findAll(text)
            .map { it.groupValues[1] + it.groupValues[2] + it.groupValues[3] }
            .firstOrNull { verhoeffValid(it) } ?: return null
        val keyword = Regex("AADHAAR|AADHAR|GOVERNMENT OF INDIA|UNIQUE IDENTIFICATION|भारत सरकार|आधार").containsMatchIn(up)
        if (!keyword && !Regex("\\b\\d{4} \\d{4} \\d{4}\\b").containsMatchIn(text)) return null

        val dobIdx = lines.indexOfFirst { Regex("DOB|D\\.O\\.B|Date of Birth|जन्म|Year of Birth", RegexOption.IGNORE_CASE).containsMatchIn(it) }
        val dobLine = lines.getOrNull(dobIdx).orEmpty()
        val dob = DATE.find(dobLine)?.value ?: Regex("\\b(19|20)\\d{2}\\b").find(dobLine)?.value.orEmpty()
        var name = ""
        for (i in (dobIdx - 1) downTo 0) {
            val l = lines[i].replace(Regex("[^A-Za-z .]"), "").trim()
            if (l.length >= 3 && !Regex("GOVERNMENT|INDIA|AADHAAR|UNIQUE|AUTHORITY", RegexOption.IGNORE_CASE).containsMatchIn(l)) { name = l; break }
        }
        val g = Regex("\\b(FEMALE|MALE|TRANSGENDER)\\b").find(up)?.groupValues?.get(1)
            ?: when { text.contains("महिला") -> "FEMALE"; text.contains("पुरुष") -> "MALE"; else -> "" }
        val vid = Regex("\\bVID\\s*:?\\s*(\\d{4}\\s?\\d{4}\\s?\\d{4}\\s?\\d{4})\\b", RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)
        val addr = labelled(lines, Regex("\\bAddress\\b|पता", RegexOption.IGNORE_CASE))
        val fields = mutableListOf(
            f("Aadhaar Number", aad.chunked(4).joinToString(" "), true), f("Name", name), f("Date of Birth", dob),
            f("Gender", g.lowercase().replaceFirstChar { it.uppercase() }),
        )
        if (vid != null) fields += f("VID", vid)
        if (addr.isNotEmpty()) fields += f("Address", addr)
        return Result("aadhaar", "Aadhaar", fields.filter { it.value.isNotBlank() })
    }

    private fun parsePan(lines: List<String>, up: String): Result? {
        val pan = Regex("\\b([A-Z]{3}[ABCFGHLJPT][A-Z]\\d{4}[A-Z])\\b").find(up)?.groupValues?.get(1) ?: return null
        var name = labelled(lines, Regex("^(Name|नाम)\\b(?!.*father)", RegexOption.IGNORE_CASE))
        var father = labelled(lines, Regex("Father'?s Name|पिता", RegexOption.IGNORE_CASE))
        val dob = DATE.find(labelled(lines, Regex("Date of Birth|जन्म", RegexOption.IGNORE_CASE)))?.value
            ?: DATE.find(lines.joinToString("\n"))?.value.orEmpty()
        if (name.isEmpty()) {
            val caps = lines.filter {
                it.matches(Regex("[A-Z][A-Z .]{2,}")) && it != pan &&
                    !Regex("INCOME|TAX|DEPARTMENT|GOVT|INDIA|PERMANENT|ACCOUNT|NUMBER|CARD").containsMatchIn(it)
            }
            name = caps.getOrElse(0) { "" }
            if (father.isEmpty()) father = caps.getOrElse(1) { "" }
        }
        return Result("pan", "PAN Card", listOf(f("PAN", pan), f("Name", name), f("Father's Name", father), f("Date of Birth", dob)).filter { it.value.isNotBlank() })
    }

    private fun parseDrivingLicence(lines: List<String>, up: String): Result? {
        val m = Regex("\\b([A-Z]{2})[- ]?(\\d{2})[- ]?((?:19|20)\\d{2})[- ]?(\\d{7})\\b").find(up) ?: return null
        if (!Regex("DRIV|LICEN").containsMatchIn(up)) return null
        val (st, rto, yr, num) = m.destructured
        val valid = DATE.find(labelled(lines, Regex("Valid (Till|Upto|Until)|Validity", RegexOption.IGNORE_CASE)))?.value.orEmpty()
        return Result("driving", "Driving Licence", listOf(
            f("Licence No.", "$st$rto $yr$num"), f("Name", labelled(lines, Regex("^Name\\b", RegexOption.IGNORE_CASE))),
            f("Valid Until", valid), f("Vehicle Classes", labelled(lines, Regex("COV|Class of Vehicle", RegexOption.IGNORE_CASE))),
        ).filter { it.value.isNotBlank() })
    }

    private fun parseVoter(lines: List<String>, up: String): Result? {
        val epic = Regex("\\b([A-Z]{3}\\d{7})\\b").find(up)?.groupValues?.get(1) ?: return null
        if (!Regex("ELECTION|ELECTOR|VOTER|EPIC").containsMatchIn(up)) return null
        return Result("voter", "Voter ID", listOf(
            f("EPIC No.", epic),
            f("Name", labelled(lines, Regex("Elector'?s Name|^Name\\b", RegexOption.IGNORE_CASE))),
            f("Relative's Name", labelled(lines, Regex("Father'?s Name|Husband'?s Name|Relative", RegexOption.IGNORE_CASE))),
        ).filter { it.value.isNotBlank() })
    }

    // ---------------- generic ----------------
    private enum class Kind { DATE, YEAR, NUMBER, TEXT }
    private val DATE_ANY = Regex("\\b(\\d{1,2})[/\\-.](\\d{1,2})[/\\-.]((?:19|20)\\d{2})\\b|\\b(\\d{1,2}) ?(JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC)[A-Z]* ?((?:19|20)\\d{2})\\b", RegexOption.IGNORE_CASE)
    private val MONTHS = listOf("JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC")

    private fun normDate(m: MatchResult): String {
        val g = m.groupValues
        return if (g[1].isNotEmpty()) "${g[1].padStart(2, '0')}/${g[2].padStart(2, '0')}/${g[3]}"
        else "${g[4].padStart(2, '0')}/${(MONTHS.indexOf(g[5].take(3).uppercase()) + 1).toString().padStart(2, '0')}/${g[6]}"
    }
    private fun dateKey(d: String) = d.split('/').let { (dd, mm, yy) -> yy + mm + dd }

    /** How each preset field is worded on real documents, and what its value looks like. */
    private fun lookup(label: String): Pair<Regex, Kind> {
        val i = RegexOption.IGNORE_CASE
        return when (FieldKeys.canonical(label)) {
            "expiry" -> Regex("valid(?:ity)?\\s*(?:until|till|thru|to|upto|up to)|expir\\w*|date of expiry|λήξη|ισχύ\\w*\\s*έως", i) to Kind.DATE
            "dob" -> Regex("date of birth|birth\\s*date|\\bd\\.?o\\.?b\\b|γέννησ", i) to Kind.DATE
            "issued" -> Regex("date of issue|issue\\s*date|issued on|valid from|\\bissued\\b|έκδοσ", i) to Kind.DATE
            "surname" -> Regex("surname|last name|family name|επώνυμο", i) to Kind.TEXT
            "given" -> Regex("given names?|first names?|forenames?", i) to Kind.TEXT
            "name" -> Regex("(?<!sur|last |family |first |given )\\bname\\b|holder|όνομα", i) to Kind.TEXT
            "nationality" -> Regex("nationality|ιθαγένεια", i) to Kind.TEXT
            "sex" -> Regex("\\bsex\\b|gender|φύλο", i) to Kind.TEXT
            "subject" -> Regex("subject|field of study|discipline|\\bmajor\\b", i) to Kind.TEXT
            "specialisation" -> Regex("speciali[sz]ation|specialty|speciality|with specialization in", i) to Kind.TEXT
            else -> {
                val words = label.lowercase().replace(Regex("\\b(no|number|nr)\\b|[^\\p{L}\\p{N} ]"), " ").trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
                val numberish = Regex("(^| )(no|number|nr|#)\\.?$", i).containsMatchIn(label.trim())
                val head = words.joinToString("\\s*") { Regex.escape(it) }
                when {
                    numberish && head.isNotEmpty() -> Regex("$head\\s*(?:no|number|nr|n°|#)?\\.?", i) to Kind.NUMBER
                    numberish -> Regex("\\b(?:no|number|nr|n°)\\b\\.?", i) to Kind.NUMBER
                    head.isEmpty() -> Regex("(?!)") to Kind.TEXT
                    Regex("year", i).containsMatchIn(label) -> Regex(head, i) to Kind.YEAR
                    else -> Regex(head, i) to (if (Regex("date|until|from", i).containsMatchIn(label)) Kind.DATE else Kind.TEXT)
                }
            }
        }
    }

    private fun take(candidate: String, kind: Kind): String? {
        val c = candidate.trim().trimStart(':', '.', '-', '/', '|', ' ').trim()
        if (c.isEmpty()) return null
        return when (kind) {
            Kind.DATE -> DATE_ANY.find(c)?.let(::normDate)
            Kind.YEAR -> Regex("\\b(?:19|20)\\d{2}\\b").find(c)?.value
            Kind.NUMBER -> Regex("[A-Z0-9][A-Z0-9/\\-]{3,}", RegexOption.IGNORE_CASE).findAll(c).map { it.value }.firstOrNull { t -> t.count(Char::isDigit) >= 3 }
            Kind.TEXT -> c.takeIf { it.length in 2..60 && it.count(Char::isLetter) >= 2 && !it.endsWith(":") && !it.lowercase().matches(Regex("(of|in|the|and|to|for|by|on|at|is|no)\\.?")) }
        }
    }

    /** Value printed after [re] on the same line, or on the line below. */
    private fun find(lines: List<String>, re: Regex, kind: Kind): String? {
        for ((i, l) in lines.withIndex()) {
            val m = re.find(l) ?: continue
            take(l.substring(m.range.last + 1), kind)?.let { return it }
            lines.getOrNull(i + 1)?.let { next -> if (re.find(next) == null) take(next, kind)?.let { return it } }
        }
        return null
    }

    /** Degree certificates rarely label anything: read "Master of Science in Physics (Astrophysics)" and the university line. */
    private fun education(lines: List<String>, text: String): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        val i = RegexOption.IGNORE_CASE
        val degreeLine = lines.firstOrNull { Regex("\\b(bachelor|master|doctor of|diploma|ph\\.? ?d\\b|b\\.? ?sc\\b|m\\.? ?sc\\b|b\\.? ?tech\\b|m\\.? ?tech\\b|mba\\b|bba\\b|b\\.a\\.|m\\.a\\.|b\\.e\\.|m\\.e\\.|πτυχίο|μεταπτυχιακ)", i).containsMatchIn(it) }
        if (degreeLine != null) {
            val m = Regex("^(.*?)\\s+in\\s+(.+)$", i).find(degreeLine)
            val degree = (m?.groupValues?.get(1) ?: degreeLine).trim().trimEnd(',', '.')
            out += "Degree" to degree
            m?.groupValues?.get(2)?.let { rest ->
                val spec = Regex("\\((.+?)\\)|(?:with )?speciali[sz]ation(?: in)?\\s+(.+)|,\\s*(.+)$", i).find(rest)
                val subject = rest.substring(0, spec?.range?.first ?: rest.length).trim().trimEnd(',', '.', '-')
                if (subject.isNotEmpty()) out += "Subject / Field" to subject
                spec?.groupValues?.drop(1)?.firstOrNull { it.isNotBlank() }?.let { out += "Specialisation" to it.trim().trimEnd('.', ')') }
            }
        }
        lines.firstOrNull { Regex("universit|institute|college|school of|πανεπιστήμ|πολυτεχνε", i).containsMatchIn(it) }?.let { out += "University" to it.trim() }
        Regex("\\b(?:grade|cgpa|gpa|class|division|βαθμ)\\b\\W*([^\\n]{1,30})", i).find(text)?.let { out += "Grade" to it.groupValues[1].trim() }
        Regex("\\b(?:19|20)\\d{2}\\b").findAll(text).map { it.value }.maxOrNull()?.let { out += "Year of Passing" to it }
        return out
    }

    private fun parseGeneric(text: String, lines: List<String>, up: String, hint: String?): Result {
        val fields = mutableListOf<DocField>()
        fun has(label: String) = fields.any { FieldKeys.same(it.label, label) }
        // 1. The preset fields for this kind of document, found by their wording on the card.
        val template = if (hint.isNullOrBlank()) DocTypes.template("") else DocTypes.template(hint)
        if (!hint.isNullOrBlank() && DocTypes.forName(hint).key == "education") education(lines, text).forEach { (l, v) -> if (!has(l) && v.isNotBlank()) fields += f(l, v) }
        for (label in template) {
            if (has(label)) continue
            val (re, kind) = lookup(label)
            find(lines, re, kind)?.let { v ->
                val value = if (FieldKeys.canonical(label) == "sex") when (v.trim().uppercase().firstOrNull()) { 'M' -> "Male"; 'F' -> "Female"; else -> v } else v
                fields += f(label, value)
            }
        }
        // 2. Any other "Label: value" lines.
        val pair = Regex("^([A-Za-z][A-Za-z .'/()]{2,28})\\s*[:：]\\s*(.{2,})$")
        lines.forEach { l -> pair.find(l)?.let { if (!has(it.groupValues[1].trim())) fields += f(it.groupValues[1].trim(), it.groupValues[2].trim()) } }
        Regex("\\b([A-Z]{2}\\d{2}(?: ?[A-Z0-9]){11,30})\\b").find(up)?.let { m ->
            if (fields.none { it.label.contains("iban", true) }) fields += f("IBAN", m.groupValues[1].replace(" ", "").chunked(4).joinToString(" "))
        }
        // 3. For a known kind of document, unlabelled dates: the latest is the expiry, the earliest a birth date.
        val dates = DATE_ANY.findAll(text).map(::normDate).distinct().sortedBy(::dateKey).toList()
        if (!hint.isNullOrBlank() && dates.isNotEmpty()) {
            val now = Year.now().value
            template.firstOrNull { FieldKeys.canonical(it) == "expiry" && !has(it) }?.let { l -> dates.last().takeIf { it.takeLast(4).toInt() >= now - 1 }?.let { fields += f(l, it) } }
            template.firstOrNull { FieldKeys.canonical(it) == "dob" && !has(it) }?.let { l -> dates.first().takeIf { it.takeLast(4).toInt() <= now - 5 }?.let { fields += f(l, it) } }
        }
        if (fields.isEmpty()) dates.firstOrNull()?.let { fields += f("Date", it) }
        return Result("other", null, fields)
    }
}
