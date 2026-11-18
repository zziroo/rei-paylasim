package net.ziroo.reisms.cekirdek

import java.io.IOException

fun interface Tasiyici {
    /** HTTP yanıt kodunu döner. Yanıt alınamazsa `IOException`. */
    @Throws(IOException::class)
    fun gonder(govde: ByteArray, zaman: String, imza: String): Int
}

enum class Yanit { YAZILDI, KALICI_RET, GECICI }

fun siniflandir(kod: Int): Yanit = when (kod) {
    200 -> Yanit.YAZILDI
    400 -> Yanit.KALICI_RET
    else -> Yanit.GECICI
}

enum class DonguSonucu { BITTI, DEVAM, TEKRAR }

/**
 * Kuyruğu sırayla boşaltır. Mesaj ancak 200 alınca silinir, 400 kalıcıdır ve kuyruğu tıkamaz.
 * Geçici hatada durur: 404 genelde saat ya da imza sorunudur, kayıt değişmeden geçmez, kalanları denemek boşunadır.
 * İmza her denemede o anki zamanla üretilir, `id` kuyrukta sabit kalır. Sunucu tekrarı `id` ile eler.
 */
class GonderimDongusu(
    private val depo: KuyrukDeposu,
    private val kuyruk: Kuyruk,
    private val tasiyici: Tasiyici,
    private val anahtar: ByteArray,
    private val saat: () -> Long,
    private val bekle: (Long) -> Unit,
) {
    fun calistir(duruyor: () -> Boolean): DonguSonucu {
        kuyruk.bakim()
        val bitis = saat() + SURE_TAVANI
        while (true) {
            val parti = depo.enEskiler(PARTI)
            if (parti.isEmpty()) return DonguSonucu.BITTI
            for (kayit in parti) {
                if (duruyor() || saat() >= bitis) return DonguSonucu.DEVAM
                val kod = dene(kayit)
                depo.degerYaz(Sayaclar.SON_YANIT, kod?.toLong() ?: Sayaclar.AG_HATASI)
                when (kod?.let(::siniflandir) ?: Yanit.GECICI) {
                    Yanit.YAZILDI -> {
                        depo.kuyruktanSil(kayit.id)
                        depo.degerYaz(Sayaclar.SON_BASARI, saat())
                    }
                    Yanit.KALICI_RET -> depo.islem {
                        depo.kuyruktanSil(kayit.id)
                        depo.sayacArtir(Sayaclar.DUSEN_RET)
                    }
                    Yanit.GECICI -> return DonguSonucu.TEKRAR
                }
                bekle(ARALIK)
            }
        }
    }

    /** Ağ hatasında aynı çalışmada birkaç kez dener: Funnel denemesinde ilk istek zaman aşımına düşmüştü. */
    private fun dene(kayit: KuyrukKaydi): Int? {
        for (n in 1..AG_DENEME) {
            val zaman = (saat() / 1000).toString()
            try {
                return tasiyici.gonder(kayit.govde, zaman, Imza.hesapla(anahtar, zaman, kayit.govde))
            } catch (_: IOException) {
                if (n < AG_DENEME) bekle(AG_BEKLEME)
            }
        }
        return null
    }

    companion object {
        /** Sunucu dakikada 60 yazma kabul eder. */
        const val ARALIK = 1100L
        const val AG_DENEME = 3
        const val AG_BEKLEME = 2000L
        const val PARTI = 50

        /** WorkManager işi 10 dakikada keser, kesilmeden önce kendi bırakır. */
        const val SURE_TAVANI = 8L * 60 * 1000
    }
}
