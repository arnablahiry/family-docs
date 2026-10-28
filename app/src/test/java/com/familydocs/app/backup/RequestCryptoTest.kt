package com.familydocs.app.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RequestCryptoTest {
    @Test fun lookupIsStableAndHidesTheCode() {
        val a = RequestCrypto.lookup("K7#Q2@XM4")
        assertEquals(a, RequestCrypto.lookup("K7#Q2@XM4"))
        assertTrue(a != RequestCrypto.lookup("K7#Q2@XM5"))
        assertTrue(!a.contains("K7"))
        assertEquals(40, a.length)
    }

    @Test fun codeKeySealsTheGroupName() {
        val k = RequestCrypto.codeKey("K7#Q2@XM4")
        val sealed = RequestCrypto.sealText("Lahiry family", k)
        assertEquals("Lahiry family", RequestCrypto.openText(sealed, RequestCrypto.codeKey("K7#Q2@XM4")))
        assertTrue(runCatching { RequestCrypto.openText(sealed, RequestCrypto.codeKey("K7#Q2@XM5")) }.isFailure)
    }

    @Test fun approvalOpensOnlyWithTheRequestersKey() {
        val me = RequestCrypto.newKeys()
        val pub = RequestCrypto.b64(me.public.encoded)
        val (epk, box) = RequestCrypto.sealTo("FD1.invite", RequestCrypto.publicOf(pub))
        val priv = RequestCrypto.privateOf(RequestCrypto.b64(me.private.encoded))
        assertEquals("FD1.invite", RequestCrypto.openFrom(epk, box, priv))
        val other = RequestCrypto.newKeys()
        assertTrue(runCatching { RequestCrypto.openFrom(epk, box, other.private) }.isFailure)
    }
}
