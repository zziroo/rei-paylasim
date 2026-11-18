package net.ziroo.reisms.cekirdek

class KuyrukKaydi(val id: String, val tur: String, val govde: ByteArray, val eklenme: Long)

object Sayaclar {
    const val ELENEN = "elenen"
    const val GIZLENEN = "gizlenen"
    const val DUSEN_RET = "dusen_ret"
    const val DUSEN_TAVAN = "dusen_tavan"
    const val SON_BASARI = "son_basari"
    const val SON_YANIT = "son_yanit"
    const val ATLANAN_YOKLAMA = "atlanan_yoklama"

    /** Yanıt alınamadı. */
    const val AG_HATASI = -1L
}

/** Kalıcı depo. Android'de SQLite, testte bellek. Metin yalnız `kuyruk` kaydının gövdesinde durur, `gorulen` kaydında metin yoktur. */
interface KuyrukDeposu {
    fun <T> islem(blok: () -> T): T
    fun gorulduMu(id: String): Boolean
    fun gorulduYaz(id: String, zaman: Long)
    fun eskiGorulenleriSil(esik: Long)
    fun kuyrugaYaz(kayit: KuyrukKaydi)
    fun kuyrukAdedi(): Int
    fun yoklamaVarMi(): Boolean
    fun enEskiler(adet: Int): List<KuyrukKaydi>
    fun kuyruktanSil(id: String)
    fun enEskiyiSil(adet: Int)
    fun eskiKuyruguSil(esik: Long): Int
    fun sayacArtir(ad: String, miktar: Long = 1)
    fun degerYaz(ad: String, deger: Long)
    fun deger(ad: String): Long
}

/** Tavanlar ve tekrar eleme. Kayıt ve görüldü işareti tek işlemde yazılır: süreç arada ölse de mesaj kaybolmaz, çift gitmez. */
class Kuyruk(private val depo: KuyrukDeposu, private val saat: () -> Long) {
    /** Yeni mesajsa kuyruğa girer ve `true` döner. */
    fun ekleYeniyse(kayit: KuyrukKaydi): Boolean = depo.islem {
        if (depo.gorulduMu(kayit.id)) {
            false
        } else {
            depo.gorulduYaz(kayit.id, saat())
            depo.kuyrugaYaz(kayit)
            tasaniDusur()
            true
        }
    }

    /** Aynı anda en çok bir yoklama bekler: telefon günlerce çevrimdışı kalırsa birikmesin. */
    fun yoklamaEkle() {
        depo.islem {
            if (!depo.yoklamaVarMi()) {
                val zaman = saat()
                val id = Kimlik.rastgele()
                depo.kuyrugaYaz(KuyrukKaydi(id, TUR_YOKLAMA, Govde.yoklama(id, zaman), zaman))
                tasaniDusur()
            }
        }
    }

    fun bakim() {
        val simdi = saat()
        depo.islem {
            val dusen = depo.eskiKuyruguSil(simdi - KUYRUK_OMRU)
            if (dusen > 0) depo.sayacArtir(Sayaclar.DUSEN_TAVAN, dusen.toLong())
            depo.eskiGorulenleriSil(simdi - TEKRAR_SURESI)
        }
    }

    private fun tasaniDusur() {
        val fazla = depo.kuyrukAdedi() - EN_COK_KAYIT
        if (fazla > 0) {
            depo.enEskiyiSil(fazla)
            depo.sayacArtir(Sayaclar.DUSEN_TAVAN, fazla.toLong())
        }
    }

    companion object {
        const val TUR_MESAJ = "mesaj"
        const val TUR_YOKLAMA = "yoklama"
        const val EN_COK_KAYIT = 2000
        const val TEKRAR_SURESI = 30L * 24 * 60 * 60 * 1000
        const val KUYRUK_OMRU = TEKRAR_SURESI
    }
}

private const val BAG_BEKLEME_MS = 10_000L
private const val BAG_ARALIK_MS = 500L

/** Dinleyicinin bağlanmasını en çok 10 sn bekler. `bagli` süreç içi değerdir, süreç yeniden doğunca bağ gelene kadar `false` kalır. */
fun bagBekle(bagli: () -> Boolean, durdu: () -> Boolean, uyu: (Long) -> Unit): Boolean {
    var gecen = 0L
    while (!bagli() && gecen < BAG_BEKLEME_MS && !durdu()) {
        uyu(BAG_ARALIK_MS)
        gecen += BAG_ARALIK_MS
    }
    return bagli()
}
