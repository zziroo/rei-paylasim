package net.ziroo.reisms.cekirdek

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MesajIsleyiciTest {
    private val gun = 24L * 60 * 60 * 1000
    private var simdi = 200 * gun
    private val depo = BellekDepo()
    private val anahtar = ByteArray(32) { 7 }
    private val isleyici = MesajIsleyici(depo, Kuyruk(depo) { simdi }, anahtar) { simdi }

    private fun ham(
        metin: String?,
        gonderen: String? = "Anne",
        zaman: Long = simdi,
        sohbet: String? = null,
        kendi: Boolean = false,
    ) = HamMesaj("com.google.android.apps.messaging", "sms", sohbet, gonderen, kendi, zaman, metin)

    private fun yazilar() = depo.kuyruk.map { String(it.govde, Charsets.UTF_8) }

    @Test fun yeniMesajKuyrugaGirer() {
        assertEquals(1, isleyici.isle(listOf(ham("Eve gel"))))
        assertTrue(yazilar().single().contains(""""metin":"Eve gel""""))
    }

    @Test fun gecmisYalnizBirKezGider() {
        val a = ham("bir", zaman = simdi - 3000)
        val b = ham("iki", zaman = simdi - 2000)
        assertEquals(1, isleyici.isle(listOf(a)))
        // Sonraki bildirim geçmişi de taşır, yalnız yeni mesaj girer.
        assertEquals(1, isleyici.isle(listOf(a, b)))
        assertEquals(2, depo.kuyruk.size)
    }

    @Test fun yenidenYayinlananBildirimMukerrerGitmez() {
        val m = ham("bir")
        isleyici.isle(listOf(m))
        depo.kuyruk.clear()
        // Yeniden açılış: yeni nesne, aynı disk kaydı.
        val sonra = MesajIsleyici(depo, Kuyruk(depo) { simdi }, anahtar) { simdi }
        assertEquals(0, sonra.isle(listOf(m)))
        assertTrue(depo.kuyruk.isEmpty())
    }

    @Test fun kendiMesajlariAlinmaz() {
        assertEquals(0, isleyici.isle(listOf(ham("ben", kendi = true))))
        assertTrue(depo.gorulen.isEmpty())
    }

    @Test fun kodIcinHicKayitAcilmaz() {
        assertEquals(0, isleyici.isle(listOf(ham("Doğrulama kodunuz 482913", gonderen = "Garanti BBVA"))))
        assertTrue(depo.kuyruk.isEmpty())
        assertTrue(depo.gorulen.isEmpty())
        assertEquals(1L, depo.deger(Sayaclar.ELENEN))
    }

    @Test fun gecmistekiKodSayaciSisirmez() {
        val kod = ham("Doğrulama kodunuz 482913", zaman = simdi - 1000)
        isleyici.isle(listOf(kod))
        isleyici.isle(listOf(kod, ham("merhaba")))
        isleyici.isle(listOf(kod, ham("merhaba"), ham("nasılsın", zaman = simdi + 1)))
        assertEquals(1L, depo.deger(Sayaclar.ELENEN))
        assertFalse(yazilar().any { it.contains("482913") })
    }

    @Test fun bankaMesajiGizliGider() {
        assertEquals(1, isleyici.isle(listOf(ham("Kartınızdan 100 TL harcama", gonderen = "Akbank", sohbet = "Akbank"))))
        val y = yazilar().single()
        assertTrue(y.contains(""""metin":null"""))
        assertTrue(y.contains(""""gizli":true"""))
        assertFalse(y.contains("sohbet"))
        assertFalse(y.contains("harcama"))
        assertEquals(1L, depo.deger(Sayaclar.GIZLENEN))
    }

    @Test fun sayiEkindekiSinyalKaybolmaz() {
        assertEquals(0, isleyici.isle(listOf(ham("A7K2Q9", gonderen = "Ahmet", sohbet = "Bildirim (‎2 kod)"))))
        assertEquals(0, isleyici.isle(listOf(ham("A7K2Q9", gonderen = "Ahmet", sohbet = "Bildirim (2 kod)"))))
        assertEquals(1, isleyici.isle(listOf(ham("Merhaba", gonderen = "Ahmet", sohbet = "Param (2 mesaj)‏"))))
        assertTrue(yazilar().single().contains(""""gizli":true"""))
    }

    @Test fun sohbetAdiFiltredenGecer() {
        assertEquals(0, isleyici.isle(listOf(ham("Selam", gonderen = "Ahmet", sohbet = "Kodunuz 482913"))))
        assertEquals(1, isleyici.isle(listOf(ham("Selam", gonderen = "Ahmet", sohbet = "Akbank"))))
        val y = yazilar().single()
        assertTrue(y.contains(""""gizli":true"""))
        assertFalse(y.contains("Akbank"))
        assertFalse(y.contains("482913"))
    }

    @Test fun baslikKoduVeFinansiSunucuyaGitmez() {
        val ham = listOf(ham("Ayrıntı için dokunun", gonderen = "Hesabınızdan 5.000 TL çekildi"))
        assertEquals(0, isleyici.isle(ham))
        assertTrue(depo.kuyruk.isEmpty())
        assertTrue(depo.gorulen.isEmpty())
    }

    @Test fun gizliKayitIkiKezSayilmaz() {
        val m = ham("Merhaba", gonderen = "Akbank")
        isleyici.isle(listOf(m))
        isleyici.isle(listOf(m))
        assertEquals(1, depo.kuyruk.size)
        assertEquals(1L, depo.deger(Sayaclar.GIZLENEN))
    }

    @Test fun otuzGundenEskiMesajYokSayilir() {
        assertEquals(0, isleyici.isle(listOf(ham("eski", zaman = simdi - 31 * gun))))
        assertEquals(0, isleyici.isle(listOf(ham("sıfır", zaman = 0))))
        assertTrue(depo.gorulen.isEmpty())
    }

    @Test fun bosMetinAlinmaz() {
        assertEquals(0, isleyici.isle(listOf(ham(null), ham("   "))))
    }

    @Test fun gonderensizMesajBilinmiyorOlur() {
        isleyici.isle(listOf(ham("selam", gonderen = "")))
        assertTrue(yazilar().single().contains(""""gonderen":"Bilinmiyor""""))
    }

    @Test fun sohbetSayisiDegisinceMesajYinelenmez() {
        val z = simdi - 1000
        assertEquals(1, isleyici.isle(listOf(ham("selam", zaman = z, sohbet = "Grup (1 mesaj)"))))
        assertEquals(0, isleyici.isle(listOf(ham("selam", zaman = z, sohbet = "Grup (2 mesaj)"))))
        assertEquals(0, isleyici.isle(listOf(ham("selam", zaman = z, sohbet = "‎Grup (2 mesaj)"))))
        assertTrue(yazilar().single().contains(""""sohbet":"Grup""""))
    }
}

class BildirimKurallariTest {
    @Test fun yolSecimi() {
        assertEquals(Yol.YOK, Yollar.sec("sms", ozet = true, mesajVar = true, surekli = false))
        assertEquals(Yol.YOK, Yollar.sec("discord", ozet = true, mesajVar = false, surekli = false))
        assertEquals(Yol.ASIL, Yollar.sec("sms", ozet = false, mesajVar = true, surekli = false))
        assertEquals(Yol.ASIL, Yollar.sec("discord", ozet = false, mesajVar = true, surekli = false))
        assertEquals(Yol.YOK, Yollar.sec("sms", ozet = false, mesajVar = false, surekli = false))
        assertEquals(Yol.YOK, Yollar.sec("whatsapp", ozet = false, mesajVar = false, surekli = false))
        assertEquals(Yol.YEDEK, Yollar.sec("outlook", ozet = false, mesajVar = false, surekli = false))
        assertEquals(Yol.YOK, Yollar.sec("teams", ozet = false, mesajVar = false, surekli = true))
    }

    @Test fun beyazListe() {
        assertEquals("sms", Paketler.uygulama("com.google.android.apps.messaging"))
        assertEquals("whatsapp", Paketler.uygulama("com.whatsapp"))
        assertEquals("teams", Paketler.uygulama("com.microsoft.teams"))
        assertNull(Paketler.uygulama("org.telegram.messenger"))
        assertNull(Paketler.uygulama("com.google.android.apps.authenticator2"))
        assertNull(Paketler.uygulama("com.garanti.cepsubesi"))
    }

    @Test fun kendiMesajiAyrimi() {
        val sahip = Kisi("Yusuf", "ben")
        assertTrue(kendiMi(null, sahip, null))
        assertTrue(kendiMi(Kisi("Yusuf", "ben"), sahip, null))
        assertFalse(kendiMi(Kisi("Yusuf", "baska"), sahip, null))
        assertTrue(kendiMi(Kisi("Sen", null), Kisi("Sen", null), null))
        assertTrue(kendiMi(Kisi("Sen", null), null, "Sen"))
        assertFalse(kendiMi(Kisi("Anne", null), sahip, "Sen"))
        assertFalse(kendiMi(Kisi(null, null), sahip, null))
    }
}
