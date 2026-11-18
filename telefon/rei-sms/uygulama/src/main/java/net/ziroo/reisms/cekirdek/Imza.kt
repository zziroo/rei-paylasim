package net.ziroo.reisms.cekirdek

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Sunucudaki `imza_dogru`nun karşılığı: hmac_sha256(anahtar, zaman + "\n" + ham_govde), küçük harf hex. */
object Imza {
    private const val ALGORITMA = "HmacSHA256"

    fun hesapla(anahtar: ByteArray, zaman: String, govde: ByteArray): String {
        val mac = Mac.getInstance(ALGORITMA)
        mac.init(SecretKeySpec(anahtar, ALGORITMA))
        mac.update(zaman.toByteArray(Charsets.US_ASCII))
        mac.update('\n'.code.toByte())
        return Onaltilik.yaz(mac.doFinal(govde))
    }
}
