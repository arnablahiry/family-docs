package com.familydocs.app.ocr

import com.familydocs.app.domain.Countries
import com.familydocs.app.domain.DocField
import java.time.Year

/**
 * Turns raw OCR text (from ML Kit) into labelled, editable fields.
 *
 * Supported:
 *  - Machine-readable zones: passports (TD3), visas (MRV-A), ID cards / residence permits (TD1),
 *    with ICAO 9303 check digits.
 *  - Aadhaar (12 digits + Verhoeff checksum), PAN, Indian driving licence, Voter ID (EPIC).
 *  - Anything else: "Label: value" lines, IBAN, first date.
 *
 * Pure Kotlin (no Android imports) so it runs in plain JVM unit tests.
 */
object DocumentParser {

    data class Result(val type: String, val suggestedName: String?, val fields: List<DocField>)

    private val DATE = Regex("\\b(\\d{2})[/\\-.](\\d{2})[/\\-.](\\d{4})\\b")

    fun parse(raw: String): Result {
        val text = raw.replace("\r", "")
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val up = text.uppercase()

        findMrz(lines)?.let { mrz -> return parseMrz(mrz, up).let { r -> r.copy(fields = r.fields.filter { it.value.isNotBlank() }) } }
        parseAadhaar(text, lines, up)?.let { return it }
        parsePan(lines, up)?.let { return it }
        parseDrivingLicence(lines, up)?.let { return it }
        parseVoter(lines, up)?.let { return it }
        return parseGeneric(text, lines, up)
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

    private fun splitName(s: String): Pair<String, String> {
        val parts = s.split("<<")
        val surname = parts.first().replace('<', ' ').trim()
        val given = parts.drop(1).joinToString(" ").replace('<', ' ').replace(Regex("\\s+"), " ").trim()
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
    private fun parseGeneric(text: String, lines: List<String>, up: String): Result {
        val fields = mutableListOf<DocField>()
        val pair = Regex("^([A-Za-z][A-Za-z .'/()]{2,28})\\s*[:：]\\s*(.{2,})$")
        lines.forEach { l -> pair.find(l)?.let { fields += f(it.groupValues[1].trim(), it.groupValues[2].trim()) } }
        Regex("\\b([A-Z]{2}\\d{2}(?: ?[A-Z0-9]){11,30})\\b").find(up)?.let { m ->
            if (fields.none { it.label.contains("iban", true) }) fields += f("IBAN", m.groupValues[1].replace(" ", "").chunked(4).joinToString(" "))
        }
        if (fields.isEmpty()) DATE.find(text)?.let { fields += f("Date", it.value) }
        return Result("other", null, fields)
    }
}
