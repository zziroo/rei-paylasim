package net.ziroo.reisms.cekirdek

/**
 * Sunucunun `kayit_gecerli` kuralına birebir uyan JSON gövdesi. Fazla alan 400 alır, bu yüzden elle yazılır.
 * Sunucu uzunluğu kod noktasıyla sayar, Kotlin `length` UTF-16 birimi sayar: tavanlar kod noktasıyla uygulanır.
 */
object Govde {
    const val BAYT_TAVANI = 8192
    const val METIN_TAVANI = 4000
    const val AD_TAVANI = 120
    const val BILINMEYEN = "Bilinmiyor"

    fun yoklama(id: String, zaman: Long): ByteArray =
        """{"id":"$id","tur":"yoklama","zaman":$zaman}""".toByteArray(Charsets.UTF_8)

    /** Gizli kayıtta metin `null` olur, sohbet yazılmaz. */
    fun mesaj(id: String, zaman: Long, uyg: String, gonderen: String, metin: String?, gizli: Boolean, sohbet: String?): ByteArray {
        val ad = kirp(temizle(gonderen), AD_TAVANI).ifBlank { BILINMEYEN }
        val baslik = if (gizli) null else sohbet?.let { kirp(temizle(it), AD_TAVANI) }?.ifBlank { null }
        val yaz = { m: String? -> json(id, zaman, uyg, ad, m, gizli, baslik) }
        if (gizli || metin == null) return yaz(null)

        val tam = kirp(temizle(metin), METIN_TAVANI)
        yaz(tam).let { if (it.size <= BAYT_TAVANI) return it }

        // Kırpma baytla değil kod noktasıyla ilerler, çok baytlı karakter ortadan bölünmez.
        var alt = 0
        var ust = tam.codePointCount(0, tam.length)
        while (alt < ust) {
            val orta = (alt + ust + 1) / 2
            if (yaz(kirp(tam, orta)).size <= BAYT_TAVANI) alt = orta else ust = orta - 1
        }
        return yaz(kirp(tam, alt))
    }

    /** En çok `tavan` kod noktası. */
    fun kirp(s: String, tavan: Int): String {
        if (s.codePointCount(0, s.length) <= tavan) return s
        return s.substring(0, s.offsetByCodePoints(0, tavan))
    }

    /** Eşi olmayan vekil karakter UTF-8'e yazılamaz, U+FFFD olur. */
    private fun temizle(s: String): String {
        val b = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val k = s.codePointAt(i)
            i += Character.charCount(k)
            if (k in 0xD800..0xDFFF) b.append('�') else b.appendCodePoint(k)
        }
        return b.toString()
    }

    private fun json(id: String, zaman: Long, uyg: String, gonderen: String, metin: String?, gizli: Boolean, sohbet: String?): ByteArray {
        val b = StringBuilder()
        b.append("""{"id":"""").append(id).append("""","tur":"mesaj","zaman":""").append(zaman)
        b.append(""","uyg":"""")
        kacir(b, uyg)
        b.append("""","gonderen":"""")
        kacir(b, gonderen)
        b.append("""","metin":""")
        if (metin == null) {
            b.append("null")
        } else {
            b.append('"')
            kacir(b, metin)
            b.append('"')
        }
        b.append(""","gizli":""").append(gizli)
        if (sohbet != null) {
            b.append(""","sohbet":"""")
            kacir(b, sohbet)
            b.append('"')
        }
        b.append('}')
        return b.toString().toByteArray(Charsets.UTF_8)
    }

    private fun kacir(b: StringBuilder, s: String) {
        for (c in s) {
            when {
                c == '"' -> b.append("\\\"")
                c == '\\' -> b.append("\\\\")
                c == '\n' -> b.append("\\n")
                c == '\r' -> b.append("\\r")
                c == '\t' -> b.append("\\t")
                c < ' ' -> b.append("\\u").append(c.code.toString(16).padStart(4, '0'))
                else -> b.append(c)
            }
        }
    }
}
