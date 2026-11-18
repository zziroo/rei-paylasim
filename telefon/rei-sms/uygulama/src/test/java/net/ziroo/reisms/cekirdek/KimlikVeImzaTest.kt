package net.ziroo.reisms.cekirdek

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KimlikVeImzaTest {
    private val anahtar = ByteArray(32) { it.toByte() }
    private fun id(paket: String = "p", sohbet: String? = "s", gonderen: String = "g", zaman: Long = 1, metin: String = "m") =
        Kimlik.mesajId(anahtar, paket, sohbet, gonderen, zaman, metin)

    @Test fun ayniMesajAyniKimlik() {
        assertEquals(id(), id())
        assertTrue(Regex("[0-9a-f]{32}").matches(id()))
    }

    @Test fun herAlanKimligiDegistirir() {
        for (farkli in listOf(id(paket = "x"), id(sohbet = "x"), id(sohbet = null), id(gonderen = "x"), id(zaman = 2), id(metin = "x"))) {
            assertNotEquals(id(), farkli)
        }
    }

    @Test fun alanSinirlariKaymaz() {
        assertNotEquals(id(gonderen = "ab", metin = "c"), id(gonderen = "a", metin = "bc"))
    }

    @Test fun anahtarDegisinceKimlikDegisir() {
        assertNotEquals(id(), Kimlik.mesajId(ByteArray(32), "p", "s", "g", 1, "m"))
    }

    @Test fun sohbetSayisiKimligeGirmez() {
        assertEquals("Grup", Kimlik.sohbetSade("Grup (3 mesaj)"))
        assertEquals("Grup", Kimlik.sohbetSade("Grup (12 messages)"))
        assertEquals("Grup", Kimlik.sohbetSade("‎Grup (2 mesaj)"))
        assertEquals("Grup", Kimlik.sohbetSade("Grup (2 mesaj)‏"))
        assertEquals("Grup (Aile)", Kimlik.sohbetSade("Grup (Aile)"))
        assertEquals(null, Kimlik.sohbetSade("   "))
    }

    @Test fun rastgeleKimlikBicimi() {
        assertTrue(Regex("[0-9a-f]{32}").matches(Kimlik.rastgele()))
        assertNotEquals(Kimlik.rastgele(), Kimlik.rastgele())
    }

    // Vektörler Python'daki hmac.new(k, z.encode() + b"\n" + g, hashlib.sha256).hexdigest() ile hesaplandı, anahtar sahte.
    private val sahte = "sahte-anahtar-0123456789abcdef0123456789abcdef".toByteArray()

    @Test fun imzaOrnekVektoru() {
        val g = """{"id":"00112233445566778899aabbccddeeff","tur":"yoklama","zaman":1762070400000}""".toByteArray()
        assertEquals("cac1cdd571001b12b04cd88e9af8ad45de78288a2f9a214878146a69720c60e0", Imza.hesapla(sahte, "1762070400", g))
    }

    @Test fun imzaTurkceGovdeVektoru() {
        val g = """{"id":"ffeeddccbbaa99887766554433221100","tur":"mesaj","zaman":1762070400000,"uyg":"sms","gonderen":"Şükrü","metin":"çay içelim mi?","gizli":false}"""
            .toByteArray(Charsets.UTF_8)
        assertEquals("2433f068803486bf5c01394021035cc6a765e910efe19efc10466eb118ff7e92", Imza.hesapla(sahte, "1762070401", g))
    }
}
