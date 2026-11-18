package net.ziroo.reisms.cekirdek

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GovdeTest {
    private val id = "00112233445566778899aabbccddeeff"

    private fun yazi(b: ByteArray) = String(b, Charsets.UTF_8)
    private fun metinKod(b: ByteArray): Int {
        val s = yazi(b)
        val m = s.substringAfter(""""metin":"""").substringBeforeLast("""","gizli"""")
        return m.codePointCount(0, m.length)
    }

    @Test fun acikMesajAlanlari() {
        assertEquals(
            """{"id":"$id","tur":"mesaj","zaman":1762070400000,"uyg":"sms","gonderen":"Anne","metin":"Eve gel","gizli":false}""",
            yazi(Govde.mesaj(id, 1762070400000, "sms", "Anne", "Eve gel", false, null)),
        )
    }

    @Test fun sohbetIsteyeBagli() {
        assertEquals(
            """{"id":"$id","tur":"mesaj","zaman":1,"uyg":"whatsapp","gonderen":"Ali","metin":"ok","gizli":false,"sohbet":"Aile"}""",
            yazi(Govde.mesaj(id, 1, "whatsapp", "Ali", "ok", false, "Aile")),
        )
    }

    @Test fun gizliKayittaMetinVeSohbetYok() {
        assertEquals(
            """{"id":"$id","tur":"mesaj","zaman":1,"uyg":"sms","gonderen":"Akbank","metin":null,"gizli":true}""",
            yazi(Govde.mesaj(id, 1, "sms", "Akbank", "kartınızdan harcama", true, "Akbank Sohbet")),
        )
    }

    @Test fun yoklamaAlanlari() {
        assertEquals("""{"id":"$id","tur":"yoklama","zaman":5}""", yazi(Govde.yoklama(id, 5)))
    }

    @Test fun kacisKarakterleri() {
        assertEquals(
            """{"id":"$id","tur":"mesaj","zaman":1,"uyg":"sms","gonderen":"a\"b\\c","metin":"s\n\t\r\u0001","gizli":false}""",
            yazi(Govde.mesaj(id, 1, "sms", "a\"b\\c", "s\n\t\r\u0001", false, null)),
        )
    }

    @Test fun bosGonderenBilinmiyorOlur() {
        assertTrue(yazi(Govde.mesaj(id, 1, "sms", "  ", "m", false, null)).contains(""""gonderen":"Bilinmiyor""""))
    }

    @Test fun metinTavani() {
        val b = Govde.mesaj(id, 1, "sms", "a", "x".repeat(5000), false, null)
        assertEquals(Govde.METIN_TAVANI, metinKod(b))
    }

    @Test fun adTavani() {
        val s = yazi(Govde.mesaj(id, 1, "sms", "g".repeat(200), "m", false, "s".repeat(200)))
        assertTrue(s.contains("\"gonderen\":\"" + "g".repeat(120) + "\""))
        assertTrue(s.contains("\"sohbet\":\"" + "s".repeat(120) + "\""))
    }

    @Test fun turkceMetinBaytTavaninaKirpilir() {
        assertEquals(4000, metinKod(Govde.mesaj(id, 1, "sms", "a", "ş".repeat(5000), false, null)))
        val b = Govde.mesaj(id, 1, "sms", "ş".repeat(120), "ş".repeat(5000), false, "ç".repeat(120))
        assertTrue(b.size <= Govde.BAYT_TAVANI)
        assertTrue(Govde.BAYT_TAVANI - b.size < 2)
        assertTrue(metinKod(b) in 3700 until 4000)
    }

    @Test fun emojiOrtadanBolunmez() {
        val b = Govde.mesaj(id, 1, "sms", "a", "😀".repeat(4000), false, null)
        assertTrue(b.size <= Govde.BAYT_TAVANI)
        val s = yazi(b)
        assertTrue(!s.contains('�'))
        val m = s.substringAfter(""""metin":"""").substringBeforeLast("""","gizli"""")
        assertEquals(m.length / 2, m.codePointCount(0, m.length))
        // Tavana bir karakterden fazla yaklaşmayı bırakmaz.
        assertTrue(Govde.BAYT_TAVANI - b.size < 4)
    }

    @Test fun kacisliMetinBaytTavanindaKalir() {
        val b = Govde.mesaj(id, 1, "sms", "\"".repeat(120), "\u0001".repeat(4000), false, "\\".repeat(120))
        assertTrue(b.size <= Govde.BAYT_TAVANI)
    }

    @Test fun esiOlmayanVekilDegistirilir() {
        val s = yazi(Govde.mesaj(id, 1, "sms", "a", "x\uD83Dy", false, null))
        assertTrue(s.contains("x�y"))
    }

    /** Sunucu kuralıyla karşılaştırmak için `build/` altına sahte örnekler yazar. */
    @Test fun sozlesmeOrnekleriniYaz() {
        val k = "sahte-anahtar-0123456789abcdef0123456789abcdef".toByteArray()
        val ornekler = listOf(
            Govde.yoklama(id, 1762070400000),
            Govde.mesaj(id, 1762070400000, "sms", "Anne", "Eve gel", false, null),
            Govde.mesaj(id, 1762070400000, "whatsapp", "Şükrü", "çay? \"evet\"\n\u0001", false, "Aile"),
            Govde.mesaj(id, 1762070400000, "sms", "Akbank", "gizli", true, "x"),
            Govde.mesaj(id, 1762070400000, "discord", "g".repeat(200), "ş".repeat(5000), false, "s".repeat(200)),
            Govde.mesaj(id, 1762070400000, "teams", "😀".repeat(200), "😀".repeat(5000), false, "😀".repeat(200)),
            Govde.mesaj(id, 1762070400000, "linkedin", "\"".repeat(120), "\u0001".repeat(5000), false, "\\".repeat(120)),
        )
        val dosya = File("build/sozlesme-ornekleri.txt")
        dosya.parentFile?.mkdirs()
        dosya.writeText(
            ornekler.joinToString("\n") { g ->
                val z = "1762070400"
                val b64 = java.util.Base64.getEncoder().encodeToString(g)
                "$z $b64 ${Imza.hesapla(k, z, g)}"
            } + "\n",
        )
    }
}
