package net.ziroo.reisms.cekirdek

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GonderimDongusuTest {
    private val anahtar = "sahte-anahtar-0123456789abcdef0123456789abcdef".toByteArray()
    private var simdi = 1_762_070_400_000L
    private val depo = BellekDepo()
    private val kuyruk = Kuyruk(depo) { simdi }
    private val beklemeler = ArrayList<Long>()

    private class Istek(val govde: ByteArray, val zaman: String, val imza: String)

    private val istekler = ArrayList<Istek>()
    private var yanitlar: (Int) -> Int = { 200 }

    private fun dongu() = GonderimDongusu(
        depo, kuyruk,
        Tasiyici { g, z, i ->
            istekler.add(Istek(g, z, i))
            yanitlar(istekler.size)
        },
        anahtar, { simdi },
        {
            beklemeler.add(it)
            simdi += it
        },
    )

    private fun ekle(vararg idler: String) = idler.forEach {
        kuyruk.ekleYeniyse(KuyrukKaydi(it, Kuyruk.TUR_MESAJ, it.toByteArray(), simdi))
    }

    @Test fun iki_yuz_alinca_silinir() {
        ekle("a", "b")
        assertEquals(DonguSonucu.BITTI, dongu().calistir { false })
        assertTrue(depo.kuyruk.isEmpty())
        assertEquals(listOf("a", "b"), istekler.map { String(it.govde) })
        assertTrue(depo.deger(Sayaclar.SON_BASARI) > 0)
        assertEquals(200L, depo.deger(Sayaclar.SON_YANIT))
    }

    @Test fun dort_yuz_kalici_kuyrugu_tikamaz() {
        ekle("a", "b")
        yanitlar = { n -> if (n == 1) 400 else 200 }
        assertEquals(DonguSonucu.BITTI, dongu().calistir { false })
        assertTrue(depo.kuyruk.isEmpty())
        assertEquals(1L, depo.deger(Sayaclar.DUSEN_RET))
        assertEquals(2, istekler.size)
    }

    @Test fun gecici_hatalar_kaydi_tutar() {
        for (kod in listOf(404, 429, 503, 500, 301)) {
            depo.kuyruk.clear()
            depo.gorulen.clear()
            istekler.clear()
            ekle("a", "b")
            yanitlar = { kod }
            assertEquals(DonguSonucu.TEKRAR, dongu().calistir { false })
            assertEquals(2, depo.kuyruk.size)
            assertEquals(1, istekler.size)
            assertEquals(kod.toLong(), depo.deger(Sayaclar.SON_YANIT))
        }
    }

    @Test fun ag_hatasi_kaydi_tutar_ve_yeniden_dener() {
        ekle("a")
        val d = GonderimDongusu(depo, kuyruk, { _, _, _ -> throw IOException("kapalı") }, anahtar, { simdi }, { simdi += it })
        assertEquals(DonguSonucu.TEKRAR, d.calistir { false })
        assertEquals(1, depo.kuyruk.size)
        assertEquals(Sayaclar.AG_HATASI, depo.deger(Sayaclar.SON_YANIT))
    }

    @Test fun ilk_istek_zaman_asimina_dusse_de_gider() {
        ekle("a")
        var n = 0
        val d = GonderimDongusu(
            depo, kuyruk,
            { _, _, _ -> if (++n == 1) throw IOException("zaman aşımı") else 200 },
            anahtar, { simdi }, { simdi += it },
        )
        assertEquals(DonguSonucu.BITTI, d.calistir { false })
        assertTrue(depo.kuyruk.isEmpty())
        assertEquals(2, n)
    }

    @Test fun imza_her_denemede_yenilenir_kimlik_sabit_kalir() {
        ekle("a")
        yanitlar = { 503 }
        dongu().calistir { false }
        simdi += 60_000
        yanitlar = { 200 }
        dongu().calistir { false }
        assertEquals(2, istekler.size)
        assertEquals(String(istekler[0].govde), String(istekler[1].govde))
        assertNotEquals(istekler[0].zaman, istekler[1].zaman)
        assertNotEquals(istekler[0].imza, istekler[1].imza)
        for (i in istekler) {
            assertEquals(10, i.zaman.length)
            assertEquals(Imza.hesapla(anahtar, i.zaman, i.govde), i.imza)
        }
    }

    @Test fun gonderim_dakikada_altmisi_gecmez() {
        val idler = (0 until 200).map { "k$it" }.toTypedArray()
        ekle(*idler)
        val saatler = ArrayList<Long>()
        yanitlar = {
            saatler.add(simdi)
            200
        }
        dongu().calistir { false }
        assertEquals(200, saatler.size)
        for (i in 60 until saatler.size) assertTrue(saatler[i] - saatler[i - 60] >= 60_000)
    }

    @Test fun durdurulunca_birakir_kuyruk_kalir() {
        ekle("a", "b")
        assertEquals(DonguSonucu.DEVAM, dongu().calistir { true })
        assertEquals(0, istekler.size)
        assertEquals(2, depo.kuyruk.size)
    }

    @Test fun sure_tavaninda_birakir() {
        val idler = (0 until 2000).map { "k$it" }.toTypedArray()
        ekle(*idler)
        assertEquals(DonguSonucu.DEVAM, dongu().calistir { false })
        assertTrue(istekler.size in 400..440)
        assertEquals(2000 - istekler.size, depo.kuyruk.size)
    }

    @Test fun eski_kayitlar_baslangicta_duser() {
        ekle("eski")
        simdi += Kuyruk.KUYRUK_OMRU + 1
        ekle("yeni")
        dongu().calistir { false }
        assertEquals(listOf("yeni"), istekler.map { String(it.govde) })
    }
}
