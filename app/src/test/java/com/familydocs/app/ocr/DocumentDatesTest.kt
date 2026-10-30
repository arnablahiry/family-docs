package com.familydocs.app.ocr

import org.junit.Assert.assertEquals
import org.junit.Test

class DocumentDatesTest {
    private fun value(text: String, hint: String, key: String) =
        DocumentParser.parse(text.trimIndent(), hint).fields.firstOrNull { FieldKeys.canonical(it.label) == key }?.value

    @Test fun labelsSideBySideWithDatesBelow() {
        val t = """
            Date of Issue      Date of Expiry
            12/03/2021         11/03/2026
        """
        assertEquals("12/03/2021", value(t, "Residence permit", "issued"))
        assertEquals("11/03/2026", value(t, "Residence permit", "expiry"))
    }

    @Test fun greekCapitals() {
        val t = """
            ΗΜΕΡΟΜΗΝΙΑ ΕΚΔΟΣΗΣ: 12.03.2021
            ΛΗΞΗ: 11.03.2026
        """
        assertEquals("12/03/2021", value(t, "Residence permit", "issued"))
        assertEquals("11/03/2026", value(t, "Residence permit", "expiry"))
    }

    @Test fun bothOnOneLine() {
        val t = "Issue Date 12MAR2019 Expiration Date 11MAR2029"
        assertEquals("12/03/2019", value(t, "Visa", "issued"))
        assertEquals("11/03/2029", value(t, "Visa", "expiry"))
    }

    @Test fun validFromTo() {
        val t = "Valid from 05/06/2018 to 04/06/2033"
        assertEquals("05/06/2018", value(t, "Driving licence", "issued"))
        assertEquals("04/06/2033", value(t, "Driving licence", "expiry"))
    }
}
