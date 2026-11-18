package net.ziroo.reisms.cekirdek

import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Kayıt kimlikleri. Mesaj kimliği telefona özel anahtarla üretilir: sunucu gizli kaydın metnini
 * `id` üzerinden tahmin edip sınayamaz. Anahtar imza anahtarından ayrıdır ve telefonu terk etmez.
 */
object Kimlik {
    private const val ALGORITMA = "HmacSHA256"
    private const val KIMLIK_BAYT = 16

    // WhatsApp grup başlığına okunmamış sayısı ekleyebilir: "Grup (3 mesaj)". Sayı değişince `id` değişmesin.
    private val SAYI_EKI = Regex("""\s*[(\[]\s*\d+[^)\]]*[)\]]\s*$""")

    // Cihazda ölçüldü: WhatsApp sayılı başlığın başına U+200E koyar. Eklerden önce silinir, `id` yine değişmesin.
    private val GORUNMEZ = Regex("""\p{Cf}""")

    fun sohbetSade(sohbet: String?): String? =
        sohbet?.replace(GORUNMEZ, "")?.replace(SAYI_EKI, "")?.trim()?.takeIf { it.isNotEmpty() }

    /** Aynı mesaj kaç kez işlenirse işlensin aynı `id` çıkar. Uzunluk önekleri alan sınırlarını belirsiz bırakmaz. */
    fun mesajId(anahtar: ByteArray, paket: String, sohbet: String?, gonderen: String, zaman: Long, metin: String): String {
        val mac = Mac.getInstance(ALGORITMA)
        mac.init(SecretKeySpec(anahtar, ALGORITMA))
        for (alan in listOf(paket, sohbet.orEmpty(), gonderen, zaman.toString(), metin)) {
            val bayt = alan.toByteArray(Charsets.UTF_8)
            mac.update(bayt.size.toString().toByteArray(Charsets.US_ASCII))
            mac.update(':'.code.toByte())
            mac.update(bayt)
        }
        return Onaltilik.yaz(mac.doFinal().copyOf(KIMLIK_BAYT))
    }

    fun rastgele(): String = Onaltilik.yaz(rastgeleBayt(KIMLIK_BAYT))

    fun rastgeleBayt(adet: Int): ByteArray = ByteArray(adet).also { SecureRandom().nextBytes(it) }
}

internal object Onaltilik {
    private const val HANELER = "0123456789abcdef"

    fun yaz(b: ByteArray): String {
        val s = StringBuilder(b.size * 2)
        for (x in b) {
            val v = x.toInt() and 0xff
            s.append(HANELER[v ushr 4]).append(HANELER[v and 0x0f])
        }
        return s.toString()
    }
}
