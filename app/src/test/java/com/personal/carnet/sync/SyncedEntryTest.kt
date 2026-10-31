package com.personal.carnet.sync

import com.personal.carnet.data.Entry
import com.personal.carnet.data.EntryKind
import com.personal.carnet.data.Payer
import com.personal.carnet.domain.Currency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SyncedEntryTest {

    private val mine = Entry(
        id = 7, kind = EntryKind.EXPENSE, timeMillis = 100, ledgerId = 2, category = "food", note = "火锅",
        payer = Payer.ME, amountCents = 8600, currency = Currency.SGD, accountId = 3, accountCents = 8600, ledgerCents = 8600,
    )

    @Test
    fun 账户不上传() {
        val map = SyncedEntry.toMap(mine, "me")
        assertEquals(false, map.containsKey("accountId"))
        assertEquals(false, map.containsKey("accountCents"))
    }

    @Test
    fun 我付的到她那边是她付的() {
        val map = SyncedEntry.toMap(mine, "me")
        val onHerPhone = SyncedEntry.fromMap("s1", map, ledgerId = 9, myUid = "her")!!
        assertEquals(Payer.HER, onHerPhone.payer)
        assertNull(onHerPhone.accountId)
        assertEquals(0L, onHerPhone.accountCents)
        assertEquals(9L, onHerPhone.ledgerId)
        assertEquals("s1", onHerPhone.syncId)
        // 她记的「她付」到我这边也是她付
        val herOwn = SyncedEntry.toMap(mine.copy(payer = Payer.ME), "her")
        assertEquals(Payer.HER, SyncedEntry.fromMap("s2", herOwn, 2, "me")!!.payer)
        // 她记的「你付的」到我这边是我付
        val sheSaysIPaid = SyncedEntry.toMap(mine.copy(payer = Payer.HER), "her")
        assertEquals(Payer.ME, SyncedEntry.fromMap("s3", sheSaysIPaid, 2, "me")!!.payer)
    }

    @Test
    fun 邀请码() {
        assertEquals("AB23CD", SyncCodes.normalize("ab2 3-cd"))
        assertNull(SyncCodes.normalize("AB0CD1"))
        assertEquals(6, SyncCodes.generate(java.security.SecureRandom()).length)
    }
}
