package com.familydocs.app.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneCryptoTest {
    @Test fun sealedToAPhoneOpensOnlyThere() {
        val me = PhoneCrypto.newKeys()
        val (epk, box) = PhoneCrypto.sealTo("secret", PhoneCrypto.publicOf(PhoneCrypto.b64(me.public.encoded)))
        assertEquals("secret", PhoneCrypto.openFrom(epk, box, PhoneCrypto.privateOf(PhoneCrypto.b64(me.private.encoded))))
        assertTrue(runCatching { PhoneCrypto.openFrom(epk, box, PhoneCrypto.newKeys().private) }.isFailure)
    }
}
