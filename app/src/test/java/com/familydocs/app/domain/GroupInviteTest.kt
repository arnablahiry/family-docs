package com.familydocs.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupInviteTest {
    @Test fun shortInviteRoundTrips() {
        val inv = GroupInvite("3f2b8c1e-5a7d-4e2f-9b1c-0d6e8a4f2c71", "K7#-Q2@-XM4", "1AbCdEfGhIjKlMnOpQrStUvWxYz012345", "Doe family", "k", "John")
        val token = inv.encode()
        assertTrue(token.length <= 72)
        val back = GroupInvite.find("John invited you…\n\n$token")!!
        assertEquals(inv.groupId, back.groupId); assertEquals(inv.code, back.code); assertEquals(inv.index, back.index)
    }

    @Test fun unusualValuesStillRoundTrip() {
        val back = GroupInvite.find(GroupInvite("group-1", "abc", "id with spaces", "n", "k", "m").encode())!!
        assertEquals("group-1", back.groupId); assertEquals("abc", back.code); assertEquals("id with spaces", back.index)
    }

    @Test fun garbageIsNotAnInvite() { assertNull(GroupInvite.find("LR.not-really-an-invite-at-all")) }
}
