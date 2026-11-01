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
    fun 她改了我记的账_我的账户留着() {
        // 她把我记的 S$86 改成 S$90，她那边传上来的是「她记的、你付的」
        val herEdit = SyncedEntry.toMap(mine.copy(payer = Payer.HER, amountCents = 9000, ledgerCents = 9000), "her")
        val remote = SyncedEntry.fromMap("s1", herEdit, ledgerId = 2, myUid = "me")!!
        val merged = SyncedEntry.merge(mine, remote, Currency.SGD)
        assertEquals(7L, merged.id)
        assertEquals(Payer.ME, merged.payer)
        assertEquals(3L, merged.accountId)
        assertEquals(9000L, merged.accountCents)
        assertEquals(9000L, merged.ledgerCents)
        // 用人民币账户付的，按这一笔的汇率重算账户扣多少
        val cny = mine.copy(accountCents = 45580, rate = 5.30)
        assertEquals(47700L, SyncedEntry.merge(cny, remote.copy(rate = 5.30), Currency.CNY).accountCents)
        // 改成她付的：不动我的账户
        val herPaid = SyncedEntry.fromMap("s1", SyncedEntry.toMap(mine.copy(amountCents = 9000), "her"), 2, "me")!!
        assertNull(SyncedEntry.merge(mine, herPaid, Currency.SGD).accountId)
    }

    @Test
    fun 邀请码() {
        assertEquals("AB23CD", SyncCodes.normalize("ab2 3-cd"))
        assertNull(SyncCodes.normalize("AB0CD1"))
        assertEquals(6, SyncCodes.generate(java.security.SecureRandom()).length)
    }

    @Test
    fun 对方出固定金额同步过去换成她的角度() {
        // 我记的房租 3300，她出 1200
        val rent = mine.copy(amountCents = 330000, accountCents = 330000, ledgerCents = 330000, herCents = 120000)
        val map = SyncedEntry.toMap(rent, "me")
        // 我这边原样
        assertEquals(120000L, SyncedEntry.fromMap("s1", map, 2, "me")!!.herCents)
        // 她那边：她的「对方」是我，我出 2100
        val onHer = SyncedEntry.fromMap("s1", map, 9, "her")!!
        assertEquals(210000L, onHer.herCents)
        assertEquals(Payer.HER, onHer.payer)
        // 没设固定金额的还是 null
        assertNull(SyncedEntry.fromMap("s2", SyncedEntry.toMap(mine, "me"), 9, "her")!!.herCents)
    }
}
