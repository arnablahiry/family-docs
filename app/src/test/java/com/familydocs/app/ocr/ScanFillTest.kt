package com.familydocs.app.ocr

import com.familydocs.app.domain.DocField
import com.familydocs.app.domain.DocTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the phone's text reader really returns: split lines, '<' read as K, digits read as letters, no colons. */
class ScanFillTest {
    private fun value(fields: List<DocField>, label: String) = fields.firstOrNull { it.label == label }?.value

    private fun td3(): List<String> {
        val c = DocumentParser::mrzCheck
        val l1 = "P<INDLAHIRY<<ARNAB".padEnd(44, '<')
        val no9 = "Z9918237<"
        val opt = "".padEnd(14, '<')
        var l2 = no9 + c(no9) + "IND" + "940314" + c("940314") + "M" + "340815" + c("340815") + opt + c(opt)
        l2 += c(l2.substring(0, 10) + l2.substring(13, 20) + l2.substring(21, 43))
        return listOf(l1, l2)
    }

    @Test fun passport_mrzSplitAcrossLinesWithKsAndLetterDigits() {
        val (l1, l2) = td3()
        // filler read as K, line 1 split in two, a 0 in the birth date read as O, spaces inside line 2
        val noisyL1 = l1.replace("<<<<<<<<", "KKKK<<KK")
        val noisyL2 = l2.substring(0, 13) + l2.substring(13, 19).replace('0', 'O') + l2.substring(19)
        val text = "REPUBLIC OF INDIA\nPASSPORT\n${noisyL1.take(20)}\n${noisyL1.drop(20)}\n${noisyL2.chunked(9).joinToString(" ")}"
        val r = DocumentParser.parse(text, "Passport")
        assertEquals("passport", r.type)
        assertEquals("Z9918237", value(r.fields, "Passport No."))
        assertEquals("LAHIRY", value(r.fields, "Surname"))
        assertEquals("ARNAB", value(r.fields, "Given Names"))
        assertEquals("14/03/1994", value(r.fields, "Date of Birth"))
        assertEquals("15/08/2034", value(r.fields, "Date of Expiry"))
    }

    @Test fun passport_fillsPresetFieldsInsteadOfReplacingThem() {
        val template = DocTypes.template("Passport")
        val blank = template.map { DocField(it, "") }
        val (l1, l2) = td3()
        val scanned = DocumentParser.parse("$l1\n$l2", "Passport").fields
        val out = FieldMerge.merge(blank, template, scanned, ensureTemplate = true)
        // every preset field is still there, in order, now filled
        assertEquals(template, out.take(template.size).map { it.label })
        assertEquals("Z9918237", value(out, "Passport No."))
        assertEquals("Indian (IND)", value(out, "Nationality"))
        assertEquals("15/08/2034", value(out, "Date of Expiry"))
        // extra values the preset doesn't have are added after it
        assertEquals("Male", value(out, "Sex"))
        assertTrue(out.size > template.size)
    }

    @Test fun merge_keepsTypedValuesAndPresetLabels() {
        val current = listOf(DocField("Passport No.", "TYPED123"), DocField("Valid Until", ""), DocField("Notes", "mine"))
        val scanned = listOf(DocField("Passport No.", "Z9918237", auto = true), DocField("Date of Expiry", "15/08/2034", auto = true))
        val out = FieldMerge.merge(current, listOf("Passport No.", "Valid Until"), scanned, ensureTemplate = false)
        assertEquals("TYPED123", value(out, "Passport No."))      // typed by hand: kept
        assertEquals("15/08/2034", value(out, "Valid Until"))       // same meaning, preset label kept
        assertNull(value(out, "Date of Expiry"))
        assertEquals("mine", value(out, "Notes"))
    }

    @Test fun merge_rescanReplacesOnlyEarlierScanValues() {
        val first = listOf(DocField("Policy No.", "OLD-1", auto = true), DocField("Insurer", "Typed Co"), DocField("Member", "x", auto = true))
        val out = FieldMerge.merge(first, DocTypes.template("Health insurance"), listOf(DocField("Policy No.", "NEW-2", auto = true)), ensureTemplate = false)
        assertEquals("NEW-2", value(out, "Policy No."))
        assertEquals("Typed Co", value(out, "Insurer"))
        assertNull(value(out, "Member"))
    }

    @Test fun insurance_unlabelledCardFindsPresetFields() {
        val text = "FAMILY HEALTH INSURANCE\nInsured Member\nTEESTA LAHIRY\nPolicy Number\n4521-889-002\nInsurer\nAegean Health S.A.\nValid till 31.03.2027"
        val r = DocumentParser.parse(text, "Health Insurance")
        assertEquals("4521-889-002", value(r.fields, "Policy No."))
        assertEquals("Aegean Health S.A.", value(r.fields, "Insurer"))
        assertEquals("31/03/2027", value(r.fields, "Valid Until"))
    }

    @Test fun residencePermit_noMrzStillGetsNumberAndDates() {
        val text = "HELLENIC REPUBLIC\nRESIDENCE PERMIT\nPermit No GR7550557\nType of Permit\nStudies\nValid until 30 JUN 2029\nNationality INDIAN"
        val r = DocumentParser.parse(text, "Residence Permit")
        assertEquals("GR7550557", value(r.fields, "Permit No."))
        assertEquals("Studies", value(r.fields, "Type of Permit"))
        assertEquals("30/06/2029", value(r.fields, "Valid Until"))
        assertEquals("INDIAN", value(r.fields, "Nationality"))
    }

    @Test fun degree_subjectAndSpecialisation() {
        val text = "UNIVERSITY OF CRETE\nThis is to certify that\nARNAB LAHIRY\nhas been awarded the degree of\nMaster of Science in Physics (Astrophysics)\nGrade: Excellent\nHeraklion, 12/07/2021"
        val r = DocumentParser.parse(text, "MSc degree")
        assertEquals("Master of Science", value(r.fields, "Degree"))
        assertEquals("Physics", value(r.fields, "Subject / Field"))
        assertEquals("Astrophysics", value(r.fields, "Specialisation"))
        assertEquals("UNIVERSITY OF CRETE", value(r.fields, "University"))
        assertEquals("2021", value(r.fields, "Year of Passing"))
        assertEquals("Excellent", value(r.fields, "Grade"))
    }

    @Test fun degree_templateHasSubjectAndSpecialisation() {
        val t = DocTypes.template("Bachelor degree")
        assertTrue("Subject / Field" in t && "Specialisation" in t)
    }

    @Test fun passport_issueDateFromPrintedPart() {
        val (l1, l2) = td3()
        val r = DocumentParser.parse("REPUBLIC OF INDIA\nPASSPORT\nDate of Issue\n16/08/2024\n$l1\n$l2", "Passport")
        assertEquals("16/08/2024", value(r.fields, "Date of Issue"))
        assertEquals("15/08/2034", value(r.fields, "Date of Expiry"))
        assertTrue("Date of Issue" in DocTypes.template("Passport"))
    }
}
