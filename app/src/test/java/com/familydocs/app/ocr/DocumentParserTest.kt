package com.familydocs.app.ocr

import com.familydocs.app.domain.Countries
import com.familydocs.app.domain.DocTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentParserTest {

    private fun value(r: DocumentParser.Result, label: String) = r.fields.firstOrNull { it.label == label }?.value

    // Builds a valid TD3 (passport) MRZ so the test doesn't depend on real documents.
    private fun td3(no: String, dob: String, sex: Char, exp: String): List<String> {
        val c = DocumentParser::mrzCheck
        val l1 = "P<INDSAMPLE<<ARNAB".padEnd(44, '<')
        val no9 = no.padEnd(9, '<')
        val opt = "".padEnd(14, '<')
        var l2 = no9 + c(no9) + "IND" + dob + c(dob) + sex + exp + c(exp) + opt + c(opt)
        l2 += c(l2.substring(0, 10) + l2.substring(13, 20) + l2.substring(21, 43))
        return listOf(l1, l2)
    }

    @Test fun verhoeff_roundTrip() {
        val base = "23456789012"
        val full = base + DocumentParser.verhoeffDigit(base)
        assertTrue(DocumentParser.verhoeffValid(full))
        val broken = full.dropLast(1) + ((full.last() - '0' + 1) % 10)
        assertFalse(DocumentParser.verhoeffValid(broken))
    }

    @Test fun mrzCheck_icaoExample() {
        // ICAO 9303 specimen: document number L898902C3 → check digit 6
        assertEquals('6', DocumentParser.mrzCheck("L898902C3"))
        assertEquals('2', DocumentParser.mrzCheck("740812"))
        assertEquals('9', DocumentParser.mrzCheck("120415"))
    }

    @Test fun aadhaar() {
        val base = "39643578082"
        val num = (base + DocumentParser.verhoeffDigit(base)).chunked(4).joinToString(" ")
        val r = DocumentParser.parse("भारत सरकार\nGovernment of India\nArnab Sample\nजन्म तिथि / DOB: 14/03/1994\nपुरुष / MALE\n$num\nमेरा आधार, मेरी पहचान")
        assertEquals("aadhaar", r.type)
        assertEquals(num, value(r, "Aadhaar Number"))
        assertEquals("Arnab Sample", value(r, "Name"))
        assertEquals("14/03/1994", value(r, "Date of Birth"))
        assertEquals("Male", value(r, "Gender"))
        assertEquals(true, r.fields.first().verified)
    }

    @Test fun aadhaar_badChecksumIsIgnored() {
        val r = DocumentParser.parse("Government of India\nDOB: 01/01/1990\n2345 6789 0120")
        // 234567890120 fails Verhoeff unless it happens to be valid
        if (!DocumentParser.verhoeffValid("234567890120")) assertEquals("other", r.type)
    }

    @Test fun pan() {
        val r = DocumentParser.parse("INCOME TAX DEPARTMENT\nGOVT. OF INDIA\nPermanent Account Number Card\nABCPS1234K\nName\nARNAB SAMPLE\nFather's Name\nFATHER SAMPLE\nDate of Birth\n14/03/1994")
        assertEquals("pan", r.type)
        assertEquals("ABCPS1234K", value(r, "PAN"))
        assertEquals("ARNAB SAMPLE", value(r, "Name"))
        assertEquals("FATHER SAMPLE", value(r, "Father's Name"))
        assertEquals("14/03/1994", value(r, "Date of Birth"))
    }

    @Test fun pan_oldLayoutWithoutLabels() {
        val r = DocumentParser.parse("INCOME TAX DEPARTMENT GOVT. OF INDIA\nMOHUA SAMPLE\nFATHER SAMPLE\n09/08/1995\nPermanent Account Number\nABCPS9375D")
        assertEquals("MOHUA SAMPLE", value(r, "Name"))
        assertEquals("FATHER SAMPLE", value(r, "Father's Name"))
    }

    @Test fun passport_td3() {
        val mrz = td3("Z9918237", "940314", 'M', "340815")
        val r = DocumentParser.parse("REPUBLIC OF INDIA\nPASSPORT\n${mrz[0]}\n${mrz[1]}")
        assertEquals("passport", r.type)
        assertEquals("Z9918237", value(r, "Passport No."))
        assertEquals("SAMPLE", value(r, "Surname"))
        assertEquals("ARNAB", value(r, "Given Names"))
        assertEquals("14/03/1994", value(r, "Date of Birth"))
        assertEquals("15/08/2034", value(r, "Date of Expiry"))
        assertEquals("India (IND)", value(r, "Issuing Country"))
        assertTrue(r.fields.filter { it.verified != null }.all { it.verified == true })
    }

    @Test fun passport_ocrNoiseGuillemetsAndSpaces() {
        val mrz = td3("Z9918237", "940314", 'M', "340815")
        val noisy = mrz[0].replace("<<", "«").chunked(11).joinToString(" ")
        val r = DocumentParser.parse("$noisy\n${mrz[1]}")
        assertEquals("passport", r.type)
        assertEquals("ARNAB", value(r, "Given Names"))
    }

    @Test fun residencePermit_td1() {
        val c = DocumentParser::mrzCheck
        val no = "GR7550557"
        val l1 = ("IRGRC" + no + c(no)).padEnd(30, '<')
        var l2 = "940314" + c("940314") + "M" + "290630" + c("290630") + "IND" + "".padEnd(11, '<')
        l2 += c(l1.substring(5, 30) + l2.substring(0, 7) + l2.substring(8, 15) + l2.substring(18, 29))
        val l3 = "SAMPLE<<ARNAB".padEnd(30, '<')
        val r = DocumentParser.parse("HELLENIC REPUBLIC\nRESIDENCE PERMIT\n$l1\n$l2\n$l3")
        assertEquals("residence", r.type)
        assertEquals(no, value(r, "Permit No."))
        assertEquals("30/06/2029", value(r, "Valid Until"))
        assertEquals("Greece (GRC)", value(r, "Issued By"))
        assertEquals(true, r.fields.first().verified)
    }

    @Test fun voterId() {
        val r = DocumentParser.parse("ELECTION COMMISSION OF INDIA\nXKS4785723\nElector's Name : MOHUA SAMPLE\nHusband's Name : ARNAB SAMPLE")
        assertEquals("voter", r.type)
        assertEquals("XKS4785723", value(r, "EPIC No."))
        assertEquals("MOHUA SAMPLE", value(r, "Name"))
    }

    @Test fun drivingLicence() {
        val r = DocumentParser.parse("INDIAN UNION DRIVING LICENCE\nDL No: MH12 20110012345\nName: TEST PERSON\nValid Till: 12/05/2031\nCOV: LMV, MCWG")
        assertEquals("driving", r.type)
        assertEquals("MH12 20110012345", value(r, "Licence No."))
        assertEquals("12/05/2031", value(r, "Valid Until"))
    }

    @Test fun generic_labelValueAndIban() {
        val r = DocumentParser.parse("FAMILY HEALTH INSURANCE\nPolicy No: FH/2026/0048213\nValid Until: 31/03/2027\nIBAN GR16 0110 1250 0000 0001 2300 695")
        assertEquals("other", r.type)
        assertNull(r.suggestedName)
        assertEquals("FH/2026/0048213", value(r, "Policy No"))
        assertEquals("GR16 0110 1250 0000 0001 2300 695", value(r, "IBAN"))
        assertTrue(r.fields.first { it.label == "Policy No" }.sensitive)
    }

    @Test fun docTypes_iconFromName() {
        assertEquals("aadhaar", DocTypes.forName("Aadhar card").key)
        assertEquals("passport", DocTypes.forName("Teesta passport").key)
        assertEquals("residence", DocTypes.forName("OCI card").key)
        assertEquals("vaccine", DocTypes.forName("Vaccination certificate").key)
        assertEquals("certificate", DocTypes.forName("Birth certificate").key)
        val other = DocTypes.forName("Gym membership")
        assertFalse(other.matched)
        assertEquals("GM", other.initials)
    }

    @Test fun countries_inferredForFlagsAndPills() {
        val mrz = td3("Z9918237", "940314", 'M', "340815")
        val passport = DocumentParser.parse("${mrz[0]}\n${mrz[1]}")
        assertEquals("IN", Countries.infer("Passport", passport.fields, ""))
        assertEquals("IN", Countries.infer("Aadhaar", emptyList(), ""))
        assertEquals("GR", Countries.infer("Residence Permit", emptyList(), "HELLENIC REPUBLIC\nRESIDENCE PERMIT"))
        assertEquals("IN", Countries.infer("Health Insurance", emptyList(), "Sum Insured: ₹ 5,00,000"))
        assertEquals("", Countries.infer("Gym membership", emptyList(), ""))
    }

    @Test fun countries_allIsoCodes() {
        assertTrue(Countries.ALL.size > 240)
        assertEquals("JP", Countries.fromAlpha3("JPN"))
        assertEquals("Japan", Countries.name("JP"))
        assertEquals("\uD83C\uDDEC\uD83C\uDDF7", Countries.flagEmoji("GR"))
        assertEquals("JP", Countries.search("jap").first())
        // A Japanese passport MRZ resolves to Japan for the flag
        assertEquals("JP", Countries.infer("Passport", listOf(com.familydocs.app.domain.DocField("Issuing Country", "Japan (JPN)")), ""))
    }
}
