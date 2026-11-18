package net.ziroo.reisms.cekirdek

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KuyrukTest {
    private val gun = 24L * 60 * 60 * 1000
    private var simdi = 100 * gun
    private val depo = BellekDepo()
    private val kuyruk = Kuyruk(depo) { simdi }

    private fun kayit(id: String) = KuyrukKaydi(id, Kuyruk.TUR_MESAJ, byteArrayOf(1), simdi)

    @Test fun ayniKimlikIkiKezGirmez() {
        assertTrue(kuyruk.ekleYeniyse(kayit("a")))
        assertFalse(kuyruk.ekleYeniyse(kayit("a")))
        assertEquals(1, depo.kuyruk.size)
    }

    @Test fun gonderilenMesajYineGirmez() {
        kuyruk.ekleYeniyse(kayit("a"))
        depo.kuyruktanSil("a")
        assertFalse(kuyruk.ekleYeniyse(kayit("a")))
    }

    @Test fun ikiBinKayitTavani() {
        repeat(2005) { kuyruk.ekleYeniyse(kayit("k$it")) }
        assertEquals(Kuyruk.EN_COK_KAYIT, depo.kuyruk.size)
        assertEquals("k5", depo.kuyruk.first().id)
        assertEquals("k2004", depo.kuyruk.last().id)
        assertEquals(5L, depo.deger(Sayaclar.DUSEN_TAVAN))
    }

    @Test fun yediGunBekleyenMesajDusmez() {
        kuyruk.ekleYeniyse(kayit("a"))
        simdi += 8 * gun
        kuyruk.bakim()
        assertEquals(listOf("a"), depo.kuyruk.map { it.id })
    }

    @Test fun otuzGundenEskiDuser() {
        kuyruk.ekleYeniyse(kayit("eski"))
        simdi += 30 * gun + 1
        kuyruk.ekleYeniyse(kayit("yeni"))
        kuyruk.bakim()
        assertEquals(listOf("yeni"), depo.kuyruk.map { it.id })
        assertEquals(1L, depo.deger(Sayaclar.DUSEN_TAVAN))
    }

    @Test fun otuzGunlukGorulduKaydiSilinir() {
        kuyruk.ekleYeniyse(kayit("a"))
        simdi += 29 * gun
        kuyruk.bakim()
        assertTrue(depo.gorulduMu("a"))
        simdi += 2 * gun
        kuyruk.bakim()
        assertFalse(depo.gorulduMu("a"))
    }

    @Test fun enCokBirYoklamaBekler() {
        kuyruk.yoklamaEkle()
        kuyruk.yoklamaEkle()
        assertEquals(1, depo.kuyruk.size)
        depo.kuyruk.clear()
        kuyruk.yoklamaEkle()
        assertEquals(1, depo.kuyruk.size)
    }

    @Test fun bagHemenVarsaBeklenmez() {
        var uyku = 0L
        assertTrue(bagBekle({ true }, { false }) { uyku += it })
        assertEquals(0L, uyku)
    }

    @Test fun bagSonradanGelirseBulunur() {
        var uyku = 0L
        assertTrue(bagBekle({ uyku >= 3000 }, { false }) { uyku += it })
        assertEquals(3000L, uyku)
    }

    @Test fun bagGelmezseOnSaniyedeVazgecilir() {
        var uyku = 0L
        var adet = 0
        assertFalse(
            bagBekle({ false }, { false }) {
                uyku += it
                adet++
            },
        )
        assertEquals(10_000L, uyku)
        assertEquals(20, adet)
    }

    @Test fun durdurulanIsBeklemez() {
        var uyku = 0L
        assertFalse(bagBekle({ false }, { true }) { uyku += it })
        assertEquals(0L, uyku)
    }
}
